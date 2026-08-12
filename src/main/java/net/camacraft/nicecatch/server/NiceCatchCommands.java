package net.camacraft.nicecatch.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The /nicecatch command suite (permission level 2):
 *
 *   spawn <type> [count] [weightLbs] [traits]  — spawn fish; weight/traits find a matching
 *       individual by searching the UUID lottery, so the spawned fish is a real, consistent
 *       individual (it keeps its size and traits through saves and catch-and-release).
 *       weight 0 = any; traits: comma-separated ids, or "none" for a trait-free fish.
 *   give <type> [weightLbs] [traits]           — the caught ITEM, stamped exactly as asked.
 *   inspect                                    — full report on the fish you're looking at.
 *   traits                                     — list every registered trait.
 *   population [radius]                        — per-species fish census around you.
 *   calm [radius]                              — clear fear, panic and bite cooldowns.
 *   scatter [radius]                           — spook everything.
 *   chum [radius]                              — crank nearby fishes' bobber interest up.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public final class NiceCatchCommands
{
    /** UUID-lottery search budget; specific trait pairs are rare tickets. */
    private static final int MAX_SEARCH_TRIES = 400_000;
    /** Relative tolerance when matching a requested weight. */
    private static final double WEIGHT_TOLERANCE = 0.04D;

    /**
     * Fish-kind verdict per entity type. Fish-ness needs an instance (AbstractFish check),
     * so each type is probe-created once, tested, discarded, and remembered.
     */
    private static final Map<EntityType<?>, Boolean> FISH_TYPE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Whitelist config changes what counts as a fish; forget the verdicts. */
    public static void clearCache()
    {
        FISH_TYPE_CACHE.clear();
    }

    private static boolean isFishType(EntityType<?> type, ServerLevel level)
    {
        return FISH_TYPE_CACHE.computeIfAbsent(type, t -> {
            try {
                Entity probe = t.create(level);
                boolean fish = probe instanceof PathfinderMob mob && FishBehavior.isFishKind(mob);
                if (probe != null) probe.discard();
                return fish;
            } catch (Exception e) {
                return false; // a probe that can't even construct is certainly not our fish
            }
        });
    }

    /** Only the fish Nice Catch manages — no creepers and arrows in a fishing command. */
    private static final SuggestionProvider<CommandSourceStack> FISH_TYPES =
            (ctx, builder) -> {
                ServerLevel level = ctx.getSource().getLevel();
                return SharedSuggestionProvider.suggestResource(
                        ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                                .filter(id -> isFishType(ForgeRegistries.ENTITY_TYPES.getValue(id), level))
                                .toList(), builder);
            };

    private static final SuggestionProvider<CommandSourceStack> TRAIT_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Stream.concat(Stream.of("random", "none"),
                            FishTraits.all().stream().map(t -> t.id)), builder);

    /** Sentinel for a traits argument that failed to parse (error already sent). */
    private static final Set<String> INVALID_TRAITS = Set.of("__invalid__");

    private NiceCatchCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("nicecatch")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("type", ResourceLocationArgument.id()).suggests(FISH_TYPES)
                                .executes(ctx -> summon(ctx, 1, 0.0F, null))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 24))
                                        .executes(ctx -> summon(ctx, IntegerArgumentType.getInteger(ctx, "count"), 0.0F, null))
                                        .then(Commands.argument("weightLbs", FloatArgumentType.floatArg(0.0F, 100000.0F))
                                                .executes(ctx -> summon(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                                        FloatArgumentType.getFloat(ctx, "weightLbs"), null))
                                                .then(Commands.argument("traits", StringArgumentType.word()).suggests(TRAIT_IDS)
                                                        .executes(ctx -> summon(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                                                FloatArgumentType.getFloat(ctx, "weightLbs"),
                                                                StringArgumentType.getString(ctx, "traits"))))))))
                .then(Commands.literal("give")
                        .then(Commands.argument("type", ResourceLocationArgument.id()).suggests(FISH_TYPES)
                                .executes(ctx -> give(ctx, 0.0F, null))
                                .then(Commands.argument("weightLbs", FloatArgumentType.floatArg(0.01F, 100000.0F))
                                        .executes(ctx -> give(ctx, FloatArgumentType.getFloat(ctx, "weightLbs"), null))
                                        .then(Commands.argument("traits", StringArgumentType.word()).suggests(TRAIT_IDS)
                                                .executes(ctx -> give(ctx, FloatArgumentType.getFloat(ctx, "weightLbs"),
                                                        StringArgumentType.getString(ctx, "traits")))))))
                .then(Commands.literal("inspect").executes(NiceCatchCommands::inspect))
                .then(Commands.literal("traits").executes(NiceCatchCommands::listTraits))
                .then(radiusCommand("population", 48, NiceCatchCommands::population))
                .then(radiusCommand("calm", 16, NiceCatchCommands::calm))
                .then(radiusCommand("scatter", 16, NiceCatchCommands::scatter))
                .then(radiusCommand("chum", 16, NiceCatchCommands::chum)));
    }

    private interface RadiusCommand
    {
        int run(CommandContext<CommandSourceStack> ctx, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> radiusCommand(
            String name, int defaultRadius, RadiusCommand command)
    {
        return Commands.literal(name)
                .executes(ctx -> command.run(ctx, defaultRadius))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> command.run(ctx, IntegerArgumentType.getInteger(ctx, "radius"))));
    }

    // ---- summon ----

    private static int summon(CommandContext<CommandSourceStack> ctx, int count, float weightLbs,
                              @Nullable String traitsArg) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        EntityType<?> type = resolveFishType(source, ResourceLocationArgument.getId(ctx, "type"));
        if (type == null) return 0;
        Set<String> wantTraits = parseTraits(source, traitsArg);
        if (wantTraits == INVALID_TRAITS) return 0;

        Vec3 pos = spawnPos(source);
        RandomSource random = level.random;
        int spawned = 0;
        String lastSummary = "";
        for (int i = 0; i < count; i++) {
            UUID individual = findIndividual(type, weightLbs, wantTraits, random);
            if (individual == null) {
                source.sendFailure(Component.literal(
                        "No such individual exists: " + unreachableHint(type, weightLbs)));
                break;
            }
            Entity entity = type.create(level);
            if (!(entity instanceof PathfinderMob fish) || !FishBehavior.isFishKind(fish)) {
                if (entity != null) entity.discard();
                source.sendFailure(Component.literal(idOf(type) + " is not a fish"));
                return spawned;
            }
            fish.setUUID(individual);
            fish.moveTo(pos.x + (random.nextDouble() - 0.5D) * Math.min(3, count - 1),
                    pos.y, pos.z + (random.nextDouble() - 0.5D) * Math.min(3, count - 1),
                    random.nextFloat() * 360.0F, 0.0F);
            fish.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)),
                    MobSpawnType.COMMAND, null, null);
            fish.setPersistenceRequired();
            level.addFreshEntity(fish);
            spawned++;
            lastSummary = describe(fish);
        }
        if (spawned > 0) {
            int total = spawned;
            String summary = lastSummary;
            source.sendSuccess(() -> Component.literal(
                    "Spawned " + total + "x " + idOf(type) + " (" + summary + ")"), true);
        }
        return spawned;
    }

    /**
     * Sizes and traits are a pure function of the UUID (that's what keeps them sync-free and
     * persistent), so a fish matching the request is FOUND, not fabricated: roll the lottery
     * until an individual with the right weight and traits comes up, then spawn exactly it.
     */
    @Nullable
    private static UUID findIndividual(EntityType<?> type, float weightLbs,
                                       @Nullable Set<String> wantTraits, RandomSource random)
    {
        if (weightLbs <= 0.0F && wantTraits == null) {
            return Mth.createInsecureUUID(random);
        }
        for (int tries = 0; tries < MAX_SEARCH_TRIES; tries++) {
            UUID candidate = Mth.createInsecureUUID(random);
            FishTraits.Modifiers mods = FishTraits.computeFor(candidate);
            if (wantTraits != null) {
                Set<String> has = new HashSet<>();
                for (FishTraits.FishTrait trait : mods.traits()) has.add(trait.id);
                if (!has.equals(wantTraits)) continue;
            }
            if (weightLbs > 0.0F) {
                float lbs = weightFor(type, candidate, mods);
                if (Math.abs(lbs - weightLbs) / weightLbs > WEIGHT_TOLERANCE) continue;
            }
            return candidate;
        }
        return null;
    }

    private static float weightFor(EntityType<?> type, UUID id, FishTraits.Modifiers mods)
    {
        float scale = FishSizing.scaleFor(type, id, mods);
        double baseVolume = type.getWidth() * type.getWidth() * type.getHeight();
        double kg = baseVolume * scale * scale * scale
                * NiceCatchConfig.SERVER.weightPerVolume.get() * mods.weight();
        return (float) kg * FishSizing.KG_TO_LBS;
    }

    private static String unreachableHint(EntityType<?> type, float weightLbs)
    {
        if (weightLbs <= 0.0F) {
            return "that trait combination may be impossible under the current trait config";
        }
        // The rough achievable band: size band cubed, times the density trait extremes.
        FishProfiles.FishProfile profile = FishProfiles.of(type);
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        float min = !Float.isNaN(profile.sizeMin) ? profile.sizeMin : cfg.sizeVarianceMin.get().floatValue();
        float max = Math.max(min, !Float.isNaN(profile.sizeMax) ? profile.sizeMax : cfg.sizeVarianceMax.get().floatValue());
        double baseVolume = type.getWidth() * type.getWidth() * type.getHeight();
        double wpv = cfg.weightPerVolume.get();
        double lo = baseVolume * Math.pow(min, 3) * wpv * 0.6D * FishSizing.KG_TO_LBS;
        double hi = baseVolume * Math.pow(max * 1.3D, 3) * wpv * 1.5D * FishSizing.KG_TO_LBS;
        return String.format(Locale.ROOT,
                "this species can weigh roughly %.1f-%.1f lbs (traits included)", lo, hi);
    }

    // ---- give ----

    private static int give(CommandContext<CommandSourceStack> ctx, float weightLbs,
                            @Nullable String traitsArg) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation typeId = ResourceLocationArgument.getId(ctx, "type");
        EntityType<?> type = resolveFishType(source, typeId);
        if (type == null) return 0;
        Set<String> wantTraits = parseTraits(source, traitsArg);
        if (wantTraits == INVALID_TRAITS) return 0;

        if (!ForgeRegistries.ITEMS.containsKey(typeId)) {
            source.sendFailure(Component.literal("No item shares the id " + typeId
                    + " (the catch system maps fish to items by registry name)"));
            return 0;
        }
        Item item = ForgeRegistries.ITEMS.getValue(typeId);
        ItemStack stack = new ItemStack(item);

        double baseVolume = type.getWidth() * type.getWidth() * type.getHeight();
        float kg = weightLbs > 0.0F ? weightLbs / FishSizing.KG_TO_LBS
                : (float) (baseVolume * NiceCatchConfig.SERVER.weightPerVolume.get());
        CompoundTag info = new CompoundTag();
        info.putFloat("Weight", kg);
        if (wantTraits != null && !wantTraits.isEmpty()) {
            ListTag list = new ListTag();
            for (FishTraits.FishTrait trait : FishTraits.all()) { // registry order, stable
                if (wantTraits.contains(trait.id)) list.add(StringTag.valueOf(trait.id));
            }
            info.put("Traits", list);
        }
        stack.addTagElement("NiceCatch", info);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal("Gave " + typeId + " ("
                + FishSizing.formatWeight(kg) + " lbs-equivalent stamped)"), true);
        return 1;
    }

    // ---- inspect ----

    private static int inspect(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PathfinderMob fish = lookTarget(player);
        if (fish == null) {
            source.sendFailure(Component.literal("Look at a fish (within 32 blocks) to inspect it"));
            return 0;
        }
        FishBehavior.FishState state = FishBehavior.state(fish);
        FishTraits.Modifiers mods = FishTraits.of(fish);
        MutableComponent traits = Component.empty();
        if (mods.isEmpty()) {
            traits.append(Component.literal("none").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            boolean first = true;
            for (FishTraits.FishTrait trait : mods.traits()) {
                if (!first) traits.append(", ");
                traits.append(Component.translatable(trait.nameKey()).withStyle(trait.color));
                first = false;
            }
        }
        String flags = (FishBehavior.isHooked(fish) ? "hooked " : "")
                + (FishBehavior.isScattering(fish) ? "fleeing " : "");
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s — %s ", idOf(fish.getType()), FishSizing.formatWeight(FishSizing.weightKg(fish))))
                .append(FishSizing.unitLabel())
                .append(String.format(Locale.ROOT, ", scale %.2fx", FishSizing.scaleOf(fish))), false);
        source.sendSuccess(() -> Component.literal("Traits: ").append(traits), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Interest %.2f, panic fatigue %.2f %s",
                state.interest, FishBehavior.panicFatigue(fish),
                flags.isEmpty() ? "" : "[" + flags.trim() + "]")), false);
        return 1;
    }

    @Nullable
    private static PathfinderMob lookTarget(ServerPlayer player)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(33.0D);
        PathfinderMob best = null;
        double bestAlong = Double.MAX_VALUE;
        for (PathfinderMob fish : player.serverLevel().getEntitiesOfClass(PathfinderMob.class, box,
                f -> f.isAlive() && FishBehavior.isFishKind(f))) {
            Vec3 to = fish.position().add(0.0D, fish.getBbHeight() * 0.5D, 0.0D).subtract(eye);
            double along = to.dot(look);
            if (along < 0.0D || along > 32.0D) continue;
            double offLine = to.subtract(look.scale(along)).length();
            if (offLine > 1.2D || along >= bestAlong) continue;
            bestAlong = along;
            best = fish;
        }
        return best;
    }

    // ---- traits / population / calm / scatter / chum ----

    private static int listTraits(CommandContext<CommandSourceStack> ctx)
    {
        CommandSourceStack source = ctx.getSource();
        var all = FishTraits.all();
        source.sendSuccess(() -> Component.literal(all.size() + " registered traits:"), false);
        for (FishTraits.FishTrait trait : all) {
            source.sendSuccess(() -> Component.translatable(trait.nameKey()).withStyle(trait.color)
                    .append(Component.literal(" (" + trait.id + ", rarity " + trait.rarity + ") — ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable(trait.descKey()).withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return all.size();
    }

    private static int population(CommandContext<CommandSourceStack> ctx, int radius)
    {
        CommandSourceStack source = ctx.getSource();
        List<PathfinderMob> fish = nearbyFish(source, radius);
        Map<EntityType<?>, Integer> byType = new HashMap<>();
        int hooked = 0, fleeing = 0;
        for (PathfinderMob f : fish) {
            byType.merge(f.getType(), 1, Integer::sum);
            if (FishBehavior.isHooked(f)) hooked++;
            if (FishBehavior.isScattering(f)) fleeing++;
        }
        int totalHooked = hooked, totalFleeing = fleeing;
        source.sendSuccess(() -> Component.literal(fish.size() + " fish within " + radius
                + " blocks (" + totalHooked + " hooked, " + totalFleeing + " fleeing)"), false);
        byType.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(entry -> source.sendSuccess(() -> Component.literal(
                        "  " + entry.getValue() + "x " + idOf(entry.getKey())), false));
        return fish.size();
    }

    private static int calm(CommandContext<CommandSourceStack> ctx, int radius)
    {
        List<PathfinderMob> fish = nearbyFish(ctx.getSource(), radius);
        for (PathfinderMob f : fish) {
            FishBehavior.FishState state = FishBehavior.state(f);
            state.scatterUntil = 0;
            state.biteCooldownUntil = 0;
            state.nibbleCooldownUntil = 0;
            state.panicFatigue = 0.0F;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Calmed " + fish.size() + " fish (fear, panic and bite cooldowns cleared)"), true);
        return fish.size();
    }

    private static int scatter(CommandContext<CommandSourceStack> ctx, int radius)
    {
        CommandSourceStack source = ctx.getSource();
        Vec3 pos = source.getPosition();
        FishBehavior.scatterAround(source.getLevel(), pos, radius, 1.0F, null);
        source.sendSuccess(() -> Component.literal("Scattered every fish within " + radius + " blocks"), true);
        return 1;
    }

    private static int chum(CommandContext<CommandSourceStack> ctx, int radius)
    {
        List<PathfinderMob> fish = nearbyFish(ctx.getSource(), radius);
        for (PathfinderMob f : fish) {
            FishBehavior.FishState state = FishBehavior.state(f);
            state.interest = Math.min(1.0F, state.interest + 0.6F);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Chummed the water: " + fish.size() + " fish are now keenly interested"), true);
        return fish.size();
    }

    // ---- helpers ----

    private static List<PathfinderMob> nearbyFish(CommandSourceStack source, int radius)
    {
        Vec3 pos = source.getPosition();
        AABB box = AABB.ofSize(pos, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        return new ArrayList<>(source.getLevel().getEntitiesOfClass(PathfinderMob.class, box,
                f -> f.isAlive() && FishBehavior.isFishKind(f)));
    }

    @Nullable
    private static EntityType<?> resolveFishType(CommandSourceStack source, ResourceLocation id)
    {
        // containsKey guard: the entity registry answers unknown ids with its default (a pig).
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            source.sendFailure(Component.literal("Unknown entity type " + id));
            return null;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (!isFishType(type, source.getLevel())) {
            source.sendFailure(Component.literal(id + " is not a fish Nice Catch manages"));
            return null;
        }
        return type;
    }

    /**
     * "feisty,cosmic" -> ids; "none" -> empty set (a trait-free fish); "random" or absent ->
     * null (whatever the lottery gives); unknown id -> INVALID_TRAITS with the error sent.
     */
    @Nullable
    private static Set<String> parseTraits(CommandSourceStack source, @Nullable String arg)
    {
        if (arg == null || arg.equalsIgnoreCase("random")) return null;
        Set<String> out = new HashSet<>();
        if (arg.equalsIgnoreCase("none")) return out;
        for (String id : arg.split(",")) {
            String trimmed = id.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) continue;
            if (FishTraits.byId(trimmed) == null) {
                source.sendFailure(Component.literal("Unknown trait '" + trimmed
                        + "' — see /nicecatch traits"));
                return INVALID_TRAITS;
            }
            out.add(trimmed);
        }
        return out;
    }

    /** Where the sender is aiming (water included), or a spot just ahead of them. */
    private static Vec3 spawnPos(CommandSourceStack source)
    {
        if (source.getEntity() instanceof ServerPlayer player) {
            Vec3 eye = player.getEyePosition();
            Vec3 reach = eye.add(player.getLookAngle().scale(24.0D));
            HitResult hit = player.level().clip(new ClipContext(eye, reach,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit.getLocation().add(0.0D, 0.25D, 0.0D);
            }
            return eye.add(player.getLookAngle().scale(4.0D));
        }
        return source.getPosition();
    }

    private static String describe(PathfinderMob fish)
    {
        FishTraits.Modifiers mods = FishTraits.of(fish);
        StringBuilder sb = new StringBuilder();
        sb.append(FishSizing.formatWeight(FishSizing.weightKg(fish)));
        sb.append(NiceCatchConfig.SERVER.weightUnit.get() == NiceCatchConfig.WeightUnit.LBS ? " lbs" : " kg");
        for (FishTraits.FishTrait trait : mods.traits()) {
            sb.append(", ").append(trait.id);
        }
        return sb.toString();
    }

    private static String idOf(EntityType<?> type)
    {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id != null ? id.toString() : type.toString();
    }
}
