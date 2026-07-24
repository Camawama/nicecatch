package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The moment of the catch: a landed fish flies toward the player like vanilla loot and
 * silently becomes its item on arrival — no death, no corpse, just a splash and a pickup.
 *
 * Every catch also opens a short catch-and-release window: tossing the matching item back
 * into water within that window revives the exact fish (same NBT) where the item landed.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public final class FishConversion
{
    private static final List<PendingPull> PULLS = new ArrayList<>();
    private static final Map<UUID, List<RecentCatch>> CATCHES = new HashMap<>();

    private static class PendingPull
    {
        UUID fishId;
        UUID playerId;
        float doubleCatchChance;
        boolean skipRelease;
        int ticksLeft;
    }

    private static class RecentCatch
    {
        Item item;
        EntityType<?> type;
        CompoundTag nbt;
        ResourceKey<Level> dimension;
        long expiryGameTime;
        @Nullable UUID tossedItemId;
    }

    /** Called by the fight when the fish is launched out of the water toward the player. */
    public static void beginPull(ServerPlayer player, PathfinderMob fish, float doubleCatchChance)
    {
        beginPull(player, fish, doubleCatchChance, false);
    }

    /**
     * As above, but {@code skipRelease} omits the caught fish from catch-and-release — used for
     * line-arrow catches, whose item can't be tossed back into water to revive the fish.
     */
    public static void beginPull(ServerPlayer player, PathfinderMob fish, float doubleCatchChance, boolean skipRelease)
    {
        PendingPull pull = new PendingPull();
        pull.fishId = fish.getUUID();
        pull.playerId = player.getUUID();
        pull.doubleCatchChance = doubleCatchChance;
        pull.skipRelease = skipRelease;
        pull.ticksLeft = NiceCatchConfig.SERVER.pullTimeoutTicks.get();
        PULLS.add(pull);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        tickPulls(server);
        tickReleases(server);
    }

    private static void tickPulls(MinecraftServer server)
    {
        if (PULLS.isEmpty()) return;
        Iterator<PendingPull> it = PULLS.iterator();
        while (it.hasNext()) {
            PendingPull pull = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(pull.playerId);
            if (player == null) {
                // Owner vanished mid-pull; wake the fish back up wherever it is.
                for (ServerLevel level : server.getAllLevels()) {
                    PathfinderMob stranded = resolveFish(level, pull.fishId);
                    if (stranded != null) {
                        FishBehavior.setHooked(stranded, false);
                        FishBehavior.scatter(stranded, stranded.position(), 60);
                        break;
                    }
                }
                it.remove();
                continue;
            }
            PathfinderMob fish = resolveFish(player.serverLevel(), pull.fishId);
            if (fish == null) {
                it.remove();
                continue;
            }
            pull.ticksLeft--;
            if (pull.ticksLeft <= 0 || fish.distanceTo(player) < NiceCatchConfig.SERVER.convertDistance.get()) {
                convert(player, fish, pull.doubleCatchChance, pull.skipRelease);
                it.remove();
            }
        }
    }

    @Nullable
    private static PathfinderMob resolveFish(ServerLevel level, UUID id)
    {
        return level.getEntity(id) instanceof PathfinderMob fish && fish.isAlive() ? fish : null;
    }

    /** Swap the flying fish for its item: splash, pickup sound, straight into the inventory. */
    private static void convert(ServerPlayer player, PathfinderMob fish, float doubleCatchChance, boolean skipRelease)
    {
        ServerLevel level = player.serverLevel();
        ItemStack stack = itemFor(level, fish);

        CompoundTag nbt = new CompoundTag();
        fish.saveWithoutId(nbt);
        nbt.remove("UUID"); // the revived fish gets a fresh identity

        level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.2D, fish.getZ(), 8, 0.2D, 0.15D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.BUBBLE_POP, fish.getX(), fish.getY() + 0.2D, fish.getZ(), 4, 0.15D, 0.1D, 0.15D, 0.02D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F + level.random.nextFloat() * 0.4F);
        fish.discard();

        if (doubleCatchChance > 0.0F && level.random.nextFloat() < doubleCatchChance) {
            stack.grow(1);
        }

        // Line-arrow catches skip catch-and-release: the item can't be tossed back to revive the fish.
        if (!skipRelease) {
            recordCatch(player, stack.getItem(), fish.getType(), nbt, level);
        }

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.awardStat(Stats.FISH_CAUGHT, 1);
        level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5D, player.getZ(),
                1 + level.random.nextInt(5)));
    }

    /**
     * Which item is this fish? Convention first: most fish entities share a registry name with
     * their item (minecraft:cod, aquaculture:atlantic_cod...). Otherwise roll the entity's own
     * loot table, and as a last resort hand over a cod rather than nothing.
     */
    private static ItemStack itemFor(ServerLevel level, PathfinderMob fish)
    {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(fish.getType());
        if (key != null) {
            Item item = ForgeRegistries.ITEMS.getValue(key);
            if (item != null && item != Items.AIR) {
                return new ItemStack(item);
            }
        }

        LootTable table = level.getServer().getLootData().getLootTable(fish.getType().getDefaultLootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, fish)
                .withParameter(LootContextParams.ORIGIN, fish.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        for (ItemStack stack : table.getRandomItems(params)) {
            if (!stack.isEmpty()) return stack.copy();
        }
        return new ItemStack(Items.COD);
    }

    private static void recordCatch(ServerPlayer player, Item item, EntityType<?> type, CompoundTag nbt, ServerLevel level)
    {
        int window = NiceCatchConfig.SERVER.releaseWindowTicks.get();
        if (window <= 0) return;
        RecentCatch recent = new RecentCatch();
        recent.item = item;
        recent.type = type;
        recent.nbt = nbt;
        recent.dimension = level.dimension();
        recent.expiryGameTime = level.getGameTime() + window;
        CATCHES.computeIfAbsent(player.getUUID(), u -> new ArrayList<>()).add(recent);
    }

    // ---- Catch and release ----

    /** A tossed item that matches a fresh catch gets tracked; if it lands in water, the fish comes back. */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event)
    {
        if (event.getPlayer().level().isClientSide) return;
        List<RecentCatch> recents = CATCHES.get(event.getPlayer().getUUID());
        if (recents == null) return;

        ItemEntity entity = event.getEntity();
        long now = event.getPlayer().level().getGameTime();
        for (RecentCatch recent : recents) {
            if (recent.tossedItemId == null && now < recent.expiryGameTime
                    && entity.getItem().is(recent.item)) {
                recent.tossedItemId = entity.getUUID();
                break;
            }
        }
    }

    private static void tickReleases(MinecraftServer server)
    {
        if (CATCHES.isEmpty()) return;
        Iterator<Map.Entry<UUID, List<RecentCatch>>> players = CATCHES.entrySet().iterator();
        while (players.hasNext()) {
            List<RecentCatch> recents = players.next().getValue();
            Iterator<RecentCatch> it = recents.iterator();
            while (it.hasNext()) {
                RecentCatch recent = it.next();
                ServerLevel level = server.getLevel(recent.dimension);
                if (level == null || level.getGameTime() >= recent.expiryGameTime) {
                    it.remove();
                    continue;
                }
                if (recent.tossedItemId == null) continue;

                if (!(level.getEntity(recent.tossedItemId) instanceof ItemEntity item) || !item.isAlive()) {
                    recent.tossedItemId = null; // picked back up or despawned; the window stays open
                    continue;
                }
                if (item.isInWater()) {
                    release(level, item, recent);
                    it.remove();
                }
            }
        }
    }

    private static void release(ServerLevel level, ItemEntity item, RecentCatch recent)
    {
        var entity = recent.type.create(level);
        if (!(entity instanceof PathfinderMob fish)) return;
        fish.load(recent.nbt.copy());
        fish.moveTo(item.getX(), item.getY(), item.getZ(), level.random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(fish);

        // Freed fish darts away and stays wary for a while.
        FishBehavior.scatter(fish, Vec3.atCenterOf(item.blockPosition()).add(0.0D, 1.0D, 0.0D),
                NiceCatchConfig.SERVER.scatterDurationTicks.get());

        level.sendParticles(ParticleTypes.SPLASH, item.getX(), item.getY() + 0.1D, item.getZ(), 10, 0.25D, 0.1D, 0.25D, 0.0D);
        level.playSound(null, item.getX(), item.getY(), item.getZ(),
                SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);

        ItemStack stack = item.getItem();
        stack.shrink(1);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setItem(stack.copy());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        PULLS.clear();
        CATCHES.clear();
    }
}
