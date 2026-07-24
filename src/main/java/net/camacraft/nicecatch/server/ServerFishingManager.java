package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.FightPhase;
import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.RodUtil;
import net.camacraft.nicecatch.compat.AquacultureCompat;
import net.camacraft.nicecatch.network.BiteMessage;
import net.camacraft.nicecatch.network.FightEndMessage;
import net.camacraft.nicecatch.network.FightTickMessage;
import net.camacraft.nicecatch.network.NiceCatchNet;
import net.camacraft.nicecatch.server.goal.FishSteering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public class ServerFishingManager
{
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final TagKey<Item> FORGE_RAW_FISHES =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "raw_fishes"));

    private static class Session
    {
        boolean dispatchingUse;
        double pendingCastMultiplier = -1.0D;
        boolean prevBite;
        boolean prevBiteEntity;
        float biteCarry;
        int suppressBiteTicks;
        FishFight fight;
        /** A real fish currently nibbling at the bobber, waiting for the hook-set. */
        @Nullable UUID pendingFish;
        int pendingBiteTicks;
        /** A fish that committed to biting and is closing in behind the vanilla particle wake. */
        @Nullable UUID approachFish;
        int approachTicks;
        /** A fish teasing the bobber — dips and bubbles, but nothing to hook. */
        @Nullable UUID nibbleFish;
        int nibbleTicks;
        /** Cached "any fish near the bobber" check, refreshed every 20 ticks. */
        boolean fishNearby;
        int fishNearbyCheckIn;
    }

    private static Session session(Player player)
    {
        return SESSIONS.computeIfAbsent(player.getUUID(), uuid -> new Session());
    }

    /** Used by FishBehavior: a bobber whose owner is mid-fight stops attracting new fish. */
    public static boolean isFighting(ServerPlayer player)
    {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.fight != null;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        Session session = SESSIONS.remove(event.getEntity().getUUID());
        if (session != null && event.getEntity() instanceof ServerPlayer player) {
            unhookFish(player.serverLevel(), session.fight);
            clearBiteFlow(player.serverLevel(), session);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        SESSIONS.clear();
    }

    /**
     * Right-clicking a rod never casts directly anymore; casting flows through our packets.
     * Everything else — retrieving an idle bobber, reeling in a loot-table nibble (items
     * never fight back), pulling a snagged entity — stays plain vanilla. Only an active
     * entity bite or fight locks the click, since those belong to the reel system.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (event.getLevel().isClientSide) return;
        if (!RodUtil.isRod(event.getItemStack())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Session session = session(player);
        if (session.dispatchingUse) return;

        if (player.fishing == null || session.fight != null || session.pendingFish != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    /** Applies the pending cast power to a freshly spawned bobber (works for modded FishingHook subclasses too). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof FishingHook hook)) return;

        Player owner = hook.getPlayerOwner();
        if (owner == null) return;
        Session session = SESSIONS.get(owner.getUUID());
        if (session == null || session.pendingCastMultiplier <= 0) return;

        double m = session.pendingCastMultiplier;
        session.pendingCastMultiplier = -1.0D;
        Vec3 v = hook.getDeltaMovement();
        hook.setDeltaMovement(v.x * m, v.y * Math.sqrt(m), v.z * m);
    }

    /**
     * With real fish in play, fish items never come from the rod's loot table: catching in a
     * fishless spot yields junk with a shot at treasure (enchanted books and co.) instead.
     */
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event)
    {
        if (!NiceCatchConfig.SERVER.entityFishingEnabled.get() || !NiceCatchConfig.SERVER.removeFishFromLoot.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        boolean hasFish = false;
        for (ItemStack stack : event.getDrops()) {
            if (stack.is(ItemTags.FISHES) || stack.is(FORGE_RAW_FISHES)) {
                hasFish = true;
                break;
            }
        }
        if (!hasFish) return;

        event.setCanceled(true);

        ServerLevel level = player.serverLevel();
        FishingHook hook = event.getHookEntity();
        InteractionHand hand = RodUtil.findRodHand(player);
        ItemStack rod = hand != null ? player.getItemInHand(hand) : ItemStack.EMPTY;

        int luck = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_LUCK, rod)
                + AquacultureCompat.bonusLuck(rod);
        float treasureChance = NiceCatchConfig.SERVER.treasureReplacementChance.get().floatValue()
                + luck * NiceCatchConfig.SERVER.luckTreasureBonus.get().floatValue()
                + player.getLuck() * 0.02F;

        var tableId = level.random.nextFloat() < treasureChance
                ? BuiltInLootTables.FISHING_TREASURE : BuiltInLootTables.FISHING_JUNK;
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withParameter(LootContextParams.KILLER_ENTITY, player)
                .withLuck(luck + player.getLuck())
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().getLootData().getLootTable(tableId);
        List<ItemStack> drops = table.getRandomItems(params);

        for (ItemStack drop : drops) {
            ItemEntity item = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), drop);
            double dx = player.getX() - hook.getX();
            double dy = player.getY() - hook.getY();
            double dz = player.getZ() - hook.getZ();
            item.setDeltaMovement(dx * 0.1D, dy * 0.1D + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08D, dz * 0.1D);
            level.addFreshEntity(item);
        }
        level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5D, player.getZ() + 0.5D,
                level.random.nextInt(6) + 1));
    }

    public static void onCast(ServerPlayer player, float power, InteractionHand hand)
    {
        if (player.fishing != null || player.isSpectator()) return;
        if (!RodUtil.isRod(player.getItemInHand(hand))) {
            hand = RodUtil.findRodHand(player);
            if (hand == null) return;
        }

        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        power = Mth.clamp(power, 0.0F, 1.0F);
        double multiplier = Mth.lerp(power, cfg.castPowerMin.get(), cfg.castPowerMax.get());

        Session session = session(player);
        session.pendingCastMultiplier = multiplier;
        session.dispatchingUse = true;
        try {
            InteractionResult result = player.gameMode.useItem(player, player.serverLevel(), player.getItemInHand(hand), hand);
            if (result.consumesAction()) {
                player.swing(hand, true);
            }
        } finally {
            session.dispatchingUse = false;
            session.pendingCastMultiplier = -1.0D;
        }
    }

    public static void onHookSet(ServerPlayer player)
    {
        Session session = session(player);
        if (session.fight != null) return;

        FishingHook hook = player.fishing;
        InteractionHand hand = RodUtil.findRodHand(player);
        if (hook == null || hand == null) {
            NiceCatchNet.sendTo(player, new FightEndMessage(FightEndMessage.ESCAPED));
            return;
        }

        // Only a real fish starts a fight; loot-table nibbles retrieve vanilla-style instead.
        if (session.pendingFish != null) {
            PathfinderMob fish = resolveFish(player.serverLevel(), session.pendingFish);
            session.pendingFish = null;
            if (fish != null && fish.distanceToSqr(hook) < 25.0D) {
                startEntityFight(player, session, hook, hand, fish);
                return;
            }
            if (fish != null) {
                FishBehavior.scatter(fish, hook.position(), NiceCatchConfig.SERVER.scatterDurationTicks.get());
            }
        }
        NiceCatchNet.sendTo(player, new FightEndMessage(FightEndMessage.ESCAPED));
    }

    private static void startEntityFight(ServerPlayer player, Session session, FishingHook hook,
                                         InteractionHand hand, PathfinderMob fish)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        FishFight fight = new FishFight();
        fight.hand = hand;
        fight.fishId = fish.getUUID();
        fight.strength = sizeStrength(fish);

        ItemStack rod = player.getItemInHand(hand);
        fight.tensionScale = Math.max(1.0F, AquacultureCompat.tensionScale(rod));
        fight.reelScale = Math.max(1.0F, AquacultureCompat.reelEffectiveness(rod));
        fight.doubleCatchChance = AquacultureCompat.doubleCatchChance(rod);

        FishBehavior.setHooked(fish, true);
        // Pin the bobber to the fish via vanilla's hooked-entity glue (see FishingHookMixin):
        // both sides re-attach it every tick, which is the only zero-desync way to track it.
        hook.setHookedEntity(fish);
        // Only a few neighbors spook, and only briefly — a bite shouldn't empty the spot.
        FishBehavior.scatterAround(player.serverLevel(), hook.position(),
                cfg.scatterRadius.get(), cfg.scatterOnHookChance.get().floatValue(), fish,
                FishBehavior.LIGHT_SCARE_COOLDOWN);
        beginFight(player, session, hook, fight);
    }

    /** Hitbox area vs the reference area, on a sub-linear curve so small fish already differ a lot. */
    private static float sizeStrength(PathfinderMob fish)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        double area = fish.getBbWidth() * fish.getBbHeight();
        double factor = Math.pow(area / cfg.sizeReferenceArea.get(), cfg.sizeStrengthExponent.get());
        float min = cfg.fishStrengthMin.get().floatValue();
        float max = Math.max(min, cfg.fishStrengthMax.get().floatValue());
        return Mth.clamp((float) factor, min, max);
    }

    private static void beginFight(ServerPlayer player, Session session, FishingHook hook, FishFight fight)
    {
        RandomSource random = player.getRandom();
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        double dist = player.distanceTo(hook);
        // Give at least a few blocks of line-room beyond wherever the hook was set,
        // but never enough that vanilla's 32-block check breaks the line before we can.
        fight.lineLength = (float) Math.min(31.0D, Math.max(cfg.lineLength.get(), dist + 4.0D));
        fight.progress = closeness(fight, dist);
        // Setting the hook always triggers a panicked first run — even a close fish
        // tears line off before you can start gaining any.
        fight.phase = FightPhase.PULL;
        fight.phaseTicks = 25 + random.nextInt(15);
        fight.graceTicks = cfg.escapeGraceTicks.get();
        session.fight = fight;
        session.prevBite = true;

        ServerLevel level = player.serverLevel();
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.4F, 1.2F);
        NiceCatchNet.sendTo(player, new FightTickMessage(fight.progress, fight.tension, fight.fatigue,
                fight.phase.isRun(), fight.phase.id()));
    }

    public static void onReelInput(ServerPlayer player, float crank, float lift, boolean holding)
    {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.fight == null) {
            // Client believes it is fighting but we have no fight; reset it.
            if (holding) {
                NiceCatchNet.sendTo(player, new FightEndMessage(FightEndMessage.ESCAPED));
            }
            return;
        }
        FishFight fight = session.fight;
        fight.pendingCrank += Mth.clamp(crank, 0.0F, 0.5F);
        fight.pendingLift += Mth.clamp(lift, 0.0F, 2.0F);
        fight.holding = holding;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        Session session = SESSIONS.get(player.getUUID());
        FishingHook hook = player.fishing;

        if (hook == null || !hook.isAlive()) {
            if (session != null) {
                if (session.fight != null) {
                    unhookFish(player.serverLevel(), session.fight);
                    session.fight = null;
                    NiceCatchNet.sendTo(player, new FightEndMessage(FightEndMessage.ESCAPED));
                }
                clearBiteFlow(player.serverLevel(), session);
                if (session.prevBite) {
                    session.prevBite = false;
                    session.prevBiteEntity = false;
                    NiceCatchNet.sendTo(player, new BiteMessage(false, false));
                }
                session.biteCarry = 0.0F;
                session.suppressBiteTicks = 0;
            }
            return;
        }

        session = session(player);
        if (session.suppressBiteTicks > 0) {
            session.suppressBiteTicks--;
            return;
        }

        if (session.fight != null) {
            tickFight(player, session, hook);
            return;
        }

        if (NiceCatchConfig.SERVER.entityFishingEnabled.get()) {
            tickEntityBites(player, session, hook);
        }

        // Loot-table bites: watch vanilla nibbles and stretch the bite window.
        boolean nibbleBite = session.pendingFish == null && hook.nibble > 0 && hook.getHookedIn() == null;
        if (nibbleBite) {
            double windowMult = NiceCatchConfig.SERVER.biteWindowMultiplier.get();
            if (windowMult > 1.0D) {
                // Vanilla removes 1 nibble/tick; give back (1 - 1/mult) so the net drain is 1/mult per tick.
                session.biteCarry += (float) (1.0D - 1.0D / windowMult);
                int giveBack = (int) session.biteCarry;
                if (giveBack > 0) {
                    hook.nibble += giveBack;
                    session.biteCarry -= giveBack;
                }
            }
        } else {
            session.biteCarry = 0.0F;
        }

        boolean biting = nibbleBite || session.pendingFish != null;
        boolean biteEntity = session.pendingFish != null;
        if (biting != session.prevBite || biteEntity != session.prevBiteEntity) {
            session.prevBite = biting;
            session.prevBiteEntity = biteEntity;
            NiceCatchNet.sendTo(player, new BiteMessage(biting, biteEntity));
        }
    }

    /** Court the fish milling around the bobber: hold off vanilla loot, roll for real bites. */
    private static void tickEntityBites(ServerPlayer player, Session session, FishingHook hook)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        ServerLevel level = player.serverLevel();

        // Loot-table fishing only exists where no fish live. With any fish around the bobber,
        // vanilla's invisible loot-fish never lures; in truly fishless water the "something
        // approaching" wake is skipped instead (items don't swim) — collapsing the timer to 1
        // lands the loot nibble on the next tick, splash and all.
        if (--session.fishNearbyCheckIn <= 0) {
            session.fishNearbyCheckIn = 20;
            session.fishNearby = FishBehavior.anyFishNear(hook);
        }
        if (hook.nibble <= 0) {
            if (session.fishNearby) {
                hook.timeUntilHooked = 0;
                if (hook.timeUntilLured < 100) hook.timeUntilLured = 100;
            } else if (hook.timeUntilHooked > 1) {
                hook.timeUntilHooked = 1;
            }
        }

        // An active bite window: keep it honest and keep the bobber jiggling.
        if (session.pendingFish != null) {
            PathfinderMob fish = resolveFish(level, session.pendingFish);
            if (fish == null || FishBehavior.isScattering(fish) || fish.distanceToSqr(hook) > 25.0D) {
                clearBiteFlow(level, session);
                return;
            }
            session.pendingBiteTicks--;
            if (session.pendingBiteTicks <= 0) {
                clearBiteFlow(level, session);
                FishBehavior.scatter(fish, hook.position(), cfg.scatterDurationTicks.get());
                level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                        SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.3F, 0.8F);
                return;
            }
            if (session.pendingBiteTicks % 8 == 0) {
                hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.1D, 0.0D));
                level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), hook.getY() + 0.1D, hook.getZ(),
                        3, 0.1D, 0.05D, 0.1D, 0.02D);
            }
            return;
        }

        // A committed fish closing in behind the vanilla wake; the bite lands when it arrives.
        if (session.approachFish != null) {
            PathfinderMob fish = resolveFish(level, session.approachFish);
            if (fish == null || FishBehavior.isScattering(fish) || fish.distanceToSqr(hook) > 64.0D) {
                clearBiteFlow(level, session);
                return;
            }
            session.approachTicks--;
            // A touch more forgiving than point-blank so a drifting bobber can still be reached.
            if (fish.distanceToSqr(hook) < 1.4D || session.approachTicks <= 0) {
                session.approachFish = null;
                beginBite(session, level, hook, fish);
                return;
            }
            sendWakeParticles(level, hook, fish);
            return;
        }

        // A teasing nibble: the bobber dips a few times but there is nothing to hook (yet).
        if (session.nibbleFish != null) {
            PathfinderMob fish = resolveFish(level, session.nibbleFish);
            if (fish == null || FishBehavior.isScattering(fish) || fish.distanceToSqr(hook) > 9.0D) {
                clearBiteFlow(level, session);
                return;
            }
            session.nibbleTicks--;
            if (session.nibbleTicks % 5 == 0) {
                hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.07D, 0.0D));
                level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), hook.getY() + 0.1D, hook.getZ(),
                        2, 0.08D, 0.03D, 0.08D, 0.02D);
                level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                        SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.12F, 1.6F);
            }
            if (session.nibbleTicks <= 0) {
                session.nibbleFish = null;
                FishBehavior.FishState state = FishBehavior.state(fish);
                InteractionHand nibbleHand = RodUtil.findRodHand(player);
                ItemStack nibbleRod = nibbleHand != null ? player.getItemInHand(nibbleHand) : ItemStack.EMPTY;
                float setChance = Math.min(0.9F, cfg.nibbleToBiteChance.get().floatValue()
                        * AquacultureCompat.nibbleBiteMultiplier(nibbleRod));
                if (level.random.nextFloat() < setChance) {
                    beginBite(session, level, hook, fish);
                } else {
                    // Just tasting. It liked it, though.
                    state.biteBobber = null;
                    state.interest = Math.min(1.0F, state.interest + 0.1F);
                    state.nibbleCooldownUntil = level.getGameTime() + 80 + level.random.nextInt(80);
                }
            }
            return;
        }

        if (hook.nibble > 0) return; // an already-started loot bite plays out normally

        // Bites can come from any interested fish in wake range, so the approach is visible.
        List<PathfinderMob> candidates = FishBehavior.biteCandidates(hook, Math.max(cfg.biteRange.get(), 5.0D));
        if (candidates.isEmpty()) return;

        InteractionHand hand = RodUtil.findRodHand(player);
        ItemStack rod = hand != null ? player.getItemInHand(hand) : ItemStack.EMPTY;
        int lure = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_SPEED, rod);
        float topInterest = 0.0F;
        for (PathfinderMob fish : candidates) {
            topInterest = Math.max(topInterest, FishBehavior.state(fish).interest);
        }
        float chancePerTick = cfg.biteChancePerSecond.get().floatValue() / 20.0F
                * (1.0F + 0.35F * lure)
                * AquacultureCompat.biteChanceMultiplier(rod)
                * (0.3F + 0.7F * topInterest);
        if (level.random.nextFloat() < chancePerTick) {
            PathfinderMob biter = pickBiter(level, candidates, rod);
            // The fish commits: it beelines for the hook behind a vanilla wake, then bites on arrival.
            session.approachFish = biter.getUUID();
            session.approachTicks = (int) Mth.clamp(Math.sqrt(biter.distanceToSqr(hook)) * 8.0D, 15.0D, 70.0D);
            FishBehavior.state(biter).biteBobber = hook;
            return;
        }

        // No bite this tick — maybe a tease from a fish already at the bobber.
        long now = level.getGameTime();
        List<PathfinderMob> close = candidates.stream()
                .filter(f -> f.distanceToSqr(hook) < cfg.biteRange.get() * cfg.biteRange.get()
                        && now >= FishBehavior.state(f).nibbleCooldownUntil)
                .toList();
        if (!close.isEmpty() && level.random.nextFloat() < cfg.nibbleChancePerSecond.get().floatValue() / 20.0F) {
            PathfinderMob nibbler = close.get(level.random.nextInt(close.size()));
            session.nibbleFish = nibbler.getUUID();
            session.nibbleTicks = 12 + level.random.nextInt(10);
            FishBehavior.state(nibbler).biteBobber = hook;
        }
    }

    /** Luck of the Sea favors the biggest fish; otherwise the most interested ones bite first. */
    private static PathfinderMob pickBiter(ServerLevel level, List<PathfinderMob> candidates, ItemStack rod)
    {
        int luck = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_LUCK, rod)
                + AquacultureCompat.bonusLuck(rod);
        if (luck > 0 && level.random.nextFloat() < 0.25F * luck) {
            return candidates.stream()
                    .max((a, b) -> Float.compare(a.getBbWidth() * a.getBbHeight(), b.getBbWidth() * b.getBbHeight()))
                    .orElse(candidates.get(0));
        }
        float total = 0.0F;
        for (PathfinderMob fish : candidates) {
            total += FishBehavior.state(fish).interest + 0.2F;
        }
        float roll = level.random.nextFloat() * total;
        for (PathfinderMob fish : candidates) {
            roll -= FishBehavior.state(fish).interest + 0.2F;
            if (roll <= 0.0F) return fish;
        }
        return candidates.get(candidates.size() - 1);
    }

    /** The moment of the bite itself: bobber dips, splash, and the hook-set window opens. */
    private static void beginBite(Session session, ServerLevel level, FishingHook hook, PathfinderMob fish)
    {
        session.pendingFish = fish.getUUID();
        session.pendingBiteTicks = NiceCatchConfig.SERVER.biteWindowTicks.get();
        FishBehavior.state(fish).biteBobber = hook;
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.18D, 0.0D));
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.35F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.2D, hook.getZ(),
                4, 0.12D, 0.05D, 0.12D, 0.0D);
    }

    /** The vanilla V-shaped surface wake, trailing the approaching fish toward the bobber. */
    private static void sendWakeParticles(ServerLevel level, FishingHook hook, PathfinderMob fish)
    {
        Vec3 to = hook.position().subtract(fish.position());
        if (to.horizontalDistanceSqr() < 1.0E-4D) return;
        to = new Vec3(to.x, 0.0D, to.z).normalize();
        double f3 = to.x * 0.04D;
        double f4 = to.z * 0.04D;
        double y = hook.getY() + 0.1D;
        level.sendParticles(ParticleTypes.FISHING, fish.getX(), y, fish.getZ(), 0, f4, 0.01D, -f3, 1.0D);
        level.sendParticles(ParticleTypes.FISHING, fish.getX(), y, fish.getZ(), 0, -f4, 0.01D, f3, 1.0D);
    }

    /** Clears any in-flight bite (approach, nibble, or bite window) and frees the fish involved. */
    private static void clearBiteFlow(ServerLevel level, Session session)
    {
        UUID involved = session.pendingFish != null ? session.pendingFish
                : session.approachFish != null ? session.approachFish : session.nibbleFish;
        if (involved != null) {
            PathfinderMob fish = resolveFish(level, involved);
            if (fish != null) {
                FishBehavior.state(fish).biteBobber = null;
            }
        }
        session.pendingFish = null;
        session.pendingBiteTicks = 0;
        session.approachFish = null;
        session.approachTicks = 0;
        session.nibbleFish = null;
        session.nibbleTicks = 0;
    }

    @Nullable
    private static PathfinderMob resolveFish(ServerLevel level, @Nullable UUID id)
    {
        if (id == null) return null;
        return level.getEntity(id) instanceof PathfinderMob fish && fish.isAlive() ? fish : null;
    }

    private static void tickFight(ServerPlayer player, Session session, FishingHook hook)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        FishFight fight = session.fight;
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();

        if (!RodUtil.isRod(player.getItemInHand(fight.hand))) {
            InteractionHand other = RodUtil.findRodHand(player);
            if (other == null) {
                // Rod put away mid-fight; the hook will discard itself, just end cleanly.
                unhookFish(level, fight);
                hook.setHookedEntity(null);
                endFight(player, session, FightEndMessage.ESCAPED);
                return;
            }
            fight.hand = other;
        }

        PathfinderMob fish = resolveFish(level, fight.fishId);
        if (fish == null) {
            // The fish died or unloaded mid-fight; nothing left on the line.
            hook.setHookedEntity(null);
            endFight(player, session, FightEndMessage.ESCAPED);
            return;
        }
        // No loot-fish may sneak onto the line while a real one is hooked.
        hook.timeUntilHooked = 0;
        if (hook.timeUntilLured < 100) hook.timeUntilLured = 100;

        fight.ticks++;
        if (fight.graceTicks > 0) fight.graceTicks--;

        // The fish cycles between its tactics — holding, thrashing side to side, boring
        // straight away, sounding for the deep, or charging back at you. Each phase changes
        // what the angler must do; a tiring fish favors the calm ones so it can be landed.
        if (--fight.phaseTicks <= 0) {
            advancePhase(fight, random, level, hook);
        }
        boolean run = fight.phase.isRun();

        float crank = Math.min(fight.pendingCrank, cfg.maxRevolutionsPerTick.get().floatValue());
        float lift = Math.min(fight.pendingLift, 0.6F);
        fight.pendingCrank = 0.0F;
        fight.pendingLift = 0.0F;

        // Fatigue is the fish's stamina: fighting the crank drains it, runs are exhausting,
        // slack lets it recover. Big fish have deeper reserves.
        float wear = 1.0F / (0.5F + 1.5F * fight.strength);
        boolean charging = fight.phase == FightPhase.CHARGE;
        if (fight.holding) {
            if (run) {
                // Winching up a charging fish's slack tires it fast; boring and sounding runs
                // are the fish's own exertion, so cranking through them costs mostly tension.
                float chargeDrain = charging ? crank * cfg.fatiguePerRevolution.get().floatValue() : 0.0F;
                fight.fatigue += (cfg.fatiguePerRunTick.get().floatValue()
                        + crank * cfg.fatiguePerRevolution.get().floatValue() * 0.5F + chargeDrain) * wear;
                fight.tension += (crank * cfg.tensionPerRevolutionRun.get().floatValue()
                        + 0.004F * (0.5F + fight.strength)) / fight.tensionScale;
            } else {
                fight.fatigue += crank * cfg.fatiguePerRevolution.get().floatValue() * wear;
                fight.tension += crank * cfg.tensionPerRevolutionCalm.get().floatValue()
                        * (0.5F + fight.strength) / fight.tensionScale;
                fight.tension -= cfg.tensionRecoveryPerTick.get().floatValue();
            }
        } else {
            // Not cranking. A charging fish you don't answer gets to rest and recover — that's
            // why you must crank fast through a charge; other runs are still its own exertion,
            // and calm phases (hold/thrash) simply let it catch its breath.
            if (charging) {
                fight.fatigue -= cfg.fatigueRecoverPerTick.get().floatValue() * 2.0F;
            } else if (run) {
                fight.fatigue += cfg.fatiguePerRunTick.get().floatValue() * wear * 0.5F;
            } else {
                fight.fatigue -= cfg.fatigueRecoverPerTick.get().floatValue();
            }
            fight.tension -= cfg.tensionRecoveryPerTick.get().floatValue() * 2.0F;
        }
        fight.fatigue = Mth.clamp(fight.fatigue, 0.0F, 1.0F);
        fight.tension = Mth.clamp(fight.tension, 0.0F, 1.0F);

        // Line snap.
        if (fight.tension >= 1.0F && cfg.lineSnapEnabled.get()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 1.0F, 1.3F);
            int snapDamage = cfg.snapRodDamage.get();
            if (snapDamage > 0) {
                ItemStack rod = player.getItemInHand(fight.hand);
                InteractionHand hand = fight.hand;
                rod.hurtAndBreak(snapDamage, player, p -> p.broadcastBreakEvent(hand));
            }
            freeFish(fish, hook.position());
            hook.discard(); // the line broke: bobber and fish are gone
            endFight(player, session, FightEndMessage.SNAPPED);
            return;
        }

        moveHookedFish(player, fight, hook, fish, level, random, run, crank, lift);

        // The bar IS the line: distance decides everything from here.
        double dist = fish.distanceTo(player);
        fight.progress = closeness(fight, dist);

        // Spooled — the fish took every last block of line and tears free.
        if (dist >= fight.lineLength - 0.25D && fight.graceTicks <= 0) {
            freeFish(fish, hook.position());
            hook.setHookedEntity(null);
            level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                    SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.3F, 0.6F);
            endFight(player, session, FightEndMessage.ESCAPED);
            return;
        }

        // Landed — hauled all the way in while it isn't running.
        if (!run && dist <= cfg.landDistance.get() && fight.graceTicks <= 0) {
            landEntityFish(player, session, hook, fight, fish, level);
            if (cfg.bonusXp.get() && fight.strength > 0.6F) {
                int bonus = Math.round((fight.strength - 0.6F) * 8.0F);
                if (bonus > 0) {
                    level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5D, player.getZ(), bonus));
                }
            }
            endFight(player, session, FightEndMessage.CAUGHT);
            return;
        }

        NiceCatchNet.sendTo(player, new FightTickMessage(fight.progress, fight.tension, fight.fatigue,
                run, fight.phase.id()));
    }

    /**
     * Chooses the fish's next tactic when a phase ends. Runs dominate a fresh fish and calm
     * phases take over as it tires (a played-out fish gives up running entirely so it can be
     * landed); the same phase rarely repeats back to back. Sets the phase's duration and gives
     * the fish a fresh heading and a splash whenever a run kicks off.
     */
    private static void advancePhase(FishFight fight, RandomSource random, ServerLevel level, FishingHook hook)
    {
        FightPhase prev = fight.phase;
        FightPhase next = pickPhase(fight, random, prev);
        fight.phase = next;
        fight.veerTicks = 0; // pick a fresh heading on the very next movement tick

        switch (next) {
            case HOLD -> fight.phaseTicks = 20 + random.nextInt(30);
            case SWEEP -> fight.phaseTicks = 30 + random.nextInt(35);
            case PULL, SOUND -> fight.phaseTicks = Math.max(10, (int) ((20 + random.nextInt(20))
                    * (0.7F + 0.6F * fight.strength) * (1.0F - 0.55F * fight.fatigue)));
            case CHARGE -> fight.phaseTicks = 14 + random.nextInt(14);
        }

        if (next.isRun() && !prev.isRun()) {
            level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                    SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.4F, 0.7F);
        }
    }

    /** Weighted random next phase, biased by fatigue and away from immediately repeating. */
    private static FightPhase pickPhase(FishFight fight, RandomSource random, FightPhase prev)
    {
        float f = fight.fatigue;
        // Played out: no run left in it, just hold or thrash weakly so it can be reeled home.
        if (f >= 0.9F) {
            return random.nextFloat() < 0.6F ? FightPhase.HOLD : FightPhase.SWEEP;
        }
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        float chargePref = cfg.chargeChance.get().floatValue();
        float wHold = 0.6F + 1.8F * f;
        float wSweep = 1.0F + 0.8F * f;
        float wPull = 1.4F * (1.0F - 0.6F * f) * (0.6F + fight.strength);
        float wSound = 1.1F * (1.0F - 0.6F * f);
        float wCharge = 4.0F * chargePref * (1.0F - 0.7F * f);
        switch (prev) { // discourage repeating the same tactic twice in a row
            case HOLD -> wHold *= 0.15F;
            case SWEEP -> wSweep *= 0.2F;
            case PULL -> wPull *= 0.1F;
            case SOUND -> wSound *= 0.2F;
            case CHARGE -> wCharge *= 0.05F;
        }
        float roll = random.nextFloat() * (wHold + wSweep + wPull + wSound + wCharge);
        if ((roll -= wHold) < 0.0F) return FightPhase.HOLD;
        if ((roll -= wSweep) < 0.0F) return FightPhase.SWEEP;
        if ((roll -= wPull) < 0.0F) return FightPhase.PULL;
        if ((roll -= wSound) < 0.0F) return FightPhase.SOUND;
        return FightPhase.CHARGE;
    }

    /** 0..1 line-retrieved fraction for the HUD bar: 1 at landing range, 0 with all line out. */
    private static float closeness(FishFight fight, double dist)
    {
        double landDist = NiceCatchConfig.SERVER.landDistance.get();
        return (float) Mth.clamp((fight.lineLength - dist) / Math.max(1.0D, fight.lineLength - landDist), 0.0D, 1.0D);
    }

    /** Launch the beaten fish out of the water toward the player, vanilla-loot style. */
    private static void landEntityFish(ServerPlayer player, Session session, FishingHook hook,
                                       FishFight fight, PathfinderMob fish, ServerLevel level)
    {
        double dx = player.getX() - fish.getX();
        double dy = player.getY() - fish.getY();
        double dz = player.getZ() - fish.getZ();
        fish.setDeltaMovement(dx * 0.1D, dy * 0.1D + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.12D, dz * 0.1D);
        fish.hurtMarked = true; // force a velocity sync so the leap is visible

        // Keep the AI suppressed for the flight; the conversion discards the entity.
        FishConversion.beginPull(player, fish, fight.doubleCatchChance);

        int rodDamage = NiceCatchConfig.SERVER.entityCatchRodDamage.get();
        if (rodDamage > 0) {
            ItemStack rod = player.getItemInHand(fight.hand);
            // Aquaculture hooks with a durability bonus can spare the rod entirely.
            if (!AquacultureCompat.skipRodDamage(rod, player.getRandom())) {
                InteractionHand hand = fight.hand;
                rod.hurtAndBreak(rodDamage, player, p -> p.broadcastBreakEvent(hand));
            }
        }
        level.playSound(null, fish.getX(), fish.getY(), fish.getZ(),
                SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY(), fish.getZ(), 10, 0.2D, 0.1D, 0.2D, 0.0D);
        player.swing(fight.hand, true);
        hook.discard();
    }

    /**
     * The fish is the thing that moves; the bobber rides it via the hooked-entity glue. Its
     * drive this tick depends on its current phase — holding still, thrashing side to side,
     * boring straight away, sounding for the bottom, or charging the angler — and the reel
     * winch is layered on top, hauling it in against whatever it's doing.
     */
    private static void moveHookedFish(ServerPlayer player, FishFight fight, FishingHook hook,
                                       PathfinderMob fish, ServerLevel level, RandomSource random,
                                       boolean run, float crank, float lift)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        Vec3 toPlayer = new Vec3(player.getX() - fish.getX(), 0.0D, player.getZ() - fish.getZ());
        double dist = toPlayer.length();
        if (dist < 0.01D) {
            keepUnderSurface(fish);
            FishSteering.faceMovement(fish);
            return;
        }
        Vec3 toward = toPlayer.normalize();

        // Erratic swimming: ease the heading toward a lateral offset re-picked every few ticks,
        // with the odd hard juke. A thrashing (SWEEP) fish veers wider and jukes far more often.
        boolean sweeping = fight.phase == FightPhase.SWEEP;
        if (--fight.veerTicks <= 0) {
            fight.veerTicks = 6 + random.nextInt(sweeping ? 6 : 10);
            fight.veerTarget = (random.nextFloat() - 0.5F) * (sweeping ? 2.4F : 1.4F);
            if (random.nextFloat() < (sweeping ? 0.4F : 0.2F)) {
                fight.veerTarget = -fight.veerTarget * 1.4F;
            }
            fight.diveTarget = random.nextFloat() * 0.9F - 0.65F; // mostly down, occasionally up
        }
        fight.veer += (fight.veerTarget - fight.veer) * 0.3F;
        fight.dive += (fight.diveTarget - fight.dive) * 0.3F;

        // Top reel-in speed (blocks/tick) the crank drags the fish player-ward this tick. How
        // effective the crank is depends on the phase: a clean pull while it holds, near-nothing
        // against a straight run, near-nothing on a sounding fish until you pump the rod up to
        // lift it, and a fast take-up of a charging fish's slack.
        double target = 0.0D;
        if (fight.holding && crank > 0.001F && dist > 1.2D) {
            double crankFrac = Math.min(1.0D, crank / cfg.maxRevolutionsPerTick.get());
            double resist = 0.5D * fight.strength * (1.0D - 0.6D * fight.fatigue);
            double liftFrac = Math.min(1.0D, lift * cfg.liftPumpBonus.get());
            double eff = switch (fight.phase) {
                case HOLD -> 1.0D + liftFrac;                       // pump the rod to reel faster
                case SWEEP -> 0.75D;                                // thrashing fights the crank a little
                case PULL -> cfg.runReelEffectiveness.get();        // you barely winch against a run
                case SOUND -> cfg.runReelEffectiveness.get()
                        * (0.4D + 0.9D * Math.min(1.0D, lift * 2.0D)); // lift a diver before it will come
                case CHARGE -> 1.3D;                                // take up its slack fast
            };
            target = cfg.reelInSpeed.get() / 20.0D * crankFrac * (1.0D - resist) * eff * fight.reelScale;
        }

        // The fish's own drive this tick: which way it wants to go, and how hard.
        double baseForce = (0.05D + 0.06D * fight.strength) * (1.0D - 0.7D * fight.fatigue);
        Vec3 heading;
        double damping;
        switch (fight.phase) {
            case HOLD -> {
                heading = Vec3.ZERO;   // parks in place; only the reel moves it
                damping = 0.6D;
            }
            case SWEEP -> {
                // Thrash mostly sideways (perpendicular to the line), leaning to the veer side.
                Vec3 side = new Vec3(-toward.z, 0.0D, toward.x).scale(fight.veer >= 0.0F ? 1.0D : -1.0D);
                heading = side.add(toward.scale(-0.15D)).normalize();
                damping = 0.72D;
                baseForce *= 0.9D;
            }
            case PULL -> {
                heading = rotateY(toward.scale(-1.0D), fight.veer);
                heading = new Vec3(heading.x, fight.dive * 0.4D, heading.z).normalize();
                damping = 0.7D;
            }
            case SOUND -> {
                // Sound for the bottom: mostly straight down, a little away. Pulling the rod up
                // (lift) fights the dive, so the fish can be lifted and then reeled in.
                double liftResist = Mth.clamp(lift * cfg.liftRunResistance.get(), 0.0D, 0.9D);
                Vec3 awaySlight = rotateY(toward.scale(-1.0D), fight.veer * 0.5F).scale(0.5D);
                heading = new Vec3(awaySlight.x, -0.85D, awaySlight.z).normalize();
                damping = 0.7D;
                baseForce *= (1.0D - liftResist);
            }
            case CHARGE -> {
                // Rush the angler and dump slack. Break off into an away-run if it gets too
                // close, so it never beaches itself or lands the fight during a run.
                if (dist < cfg.landDistance.get() + 2.0D) {
                    heading = rotateY(toward.scale(-1.0D), fight.veer);
                } else {
                    heading = rotateY(toward, fight.veer * 0.5F);
                    heading = new Vec3(heading.x, fight.dive * 0.3D, heading.z).normalize();
                }
                damping = 0.65D;
                baseForce *= 1.1D;
            }
            default -> {
                heading = Vec3.ZERO;
                damping = 0.7D;
            }
        }

        Vec3 wobble = new Vec3((random.nextDouble() - 0.5D) * 0.05D, 0.0D, (random.nextDouble() - 0.5D) * 0.05D);
        Vec3 v = fish.getDeltaMovement().scale(damping).add(heading.scale(baseForce)).add(wobble);

        // The reel winch, layered on top: hauls the fish toward the player, and upward when it
        // has sounded below the rod (landing is gated on 3D distance, so a deep diver must be
        // brought up before it can come in).
        if (target > 0.0D) {
            double up = Mth.clamp((player.getY() - fish.getY()) * 0.1D, 0.0D, 0.8D);
            Vec3 pull = rotateY(toward, fight.veer * 0.3F).add(0.0D, up, 0.0D).normalize();
            v = v.add(pull.scale(target * (run ? 0.3D : 0.2D)));
            // In a calm phase the hard clamp means the fish can never be flung in faster than
            // the reel speed — it always eases toward the player, thrash and all.
            if (!run) {
                double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
                if (horiz > target) {
                    v = new Vec3(v.x * target / horiz, v.y, v.z * target / horiz);
                }
            }
        }

        // At the very end of the spool a run may not tear the fish clean past the line — the
        // escape check holds it here until the grace period lapses, then it breaks free.
        if (run && dist >= fight.lineLength) {
            v = new Vec3(v.x * 0.5D, v.y, v.z * 0.5D);
        }

        fish.setDeltaMovement(v);

        // A running fish churns the surface; a calm reeled one leaves a gentler fishing wake.
        if (run) {
            if (fight.ticks % 4 == 0) {
                level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.3D, fish.getZ(),
                        4, 0.15D, 0.05D, 0.15D, 0.0D);
            }
            if (fight.ticks % 14 == 0) {
                level.playSound(null, fish.getX(), fish.getY(), fish.getZ(),
                        SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.2F + random.nextFloat() * 0.3F);
            }
        } else if (target > 0.0D && fight.ticks % 6 == 0) {
            level.sendParticles(ParticleTypes.FISHING, fish.getX(), fish.getY() + 0.1D, fish.getZ(),
                    2, 0.1D, 0.05D, 0.1D, 0.0D);
        }

        keepUnderSurface(fish);
        FishSteering.faceMovement(fish);
    }

    /** Rotates a vector's horizontal components around Y by the given angle (radians). */
    private static Vec3 rotateY(Vec3 v, float angle)
    {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    /**
     * The global surface clamp in FishBehavior exempts hooked fish (the catch launch must
     * clear the water), so the fight applies its own: ease the fish back down whenever its
     * back nears the actual fluid plane. The launch happens after the fight ends, untouched.
     */
    private static void keepUnderSurface(PathfinderMob fish)
    {
        BlockPos pos = fish.blockPosition();
        FluidState fluid = fish.level().getFluidState(pos);
        if (!fluid.is(FluidTags.WATER)) return;
        if (fish.level().getFluidState(pos.above()).is(FluidTags.WATER)) return; // deep water, fine

        double surfaceY = pos.getY() + fluid.getHeight(fish.level(), pos);
        if (fish.getY() + fish.getBbHeight() > surfaceY - 0.05D) {
            Vec3 v = fish.getDeltaMovement();
            fish.setDeltaMovement(v.x, Math.min(v.y, 0.0D) - 0.02D, v.z);
        }
    }

    /** A real fish comes off the line: unhook it and let it bolt. */
    private static void freeFish(PathfinderMob fish, Vec3 from)
    {
        FishBehavior.setHooked(fish, false);
        FishBehavior.scatter(fish, from, NiceCatchConfig.SERVER.scatterDurationTicks.get());
    }

    /** Fight teardown that releases the fish if one was on the line (logout, bobber gone...). */
    private static void unhookFish(ServerLevel level, @Nullable FishFight fight)
    {
        if (fight == null || fight.fishId == null) return;
        PathfinderMob fish = resolveFish(level, fight.fishId);
        if (fish != null && FishBehavior.isHooked(fish)) {
            freeFish(fish, fish.position());
        }
    }

    private static void endFight(ServerPlayer player, Session session, byte result)
    {
        session.fight = null;
        session.prevBite = false;
        session.suppressBiteTicks = 5;
        NiceCatchNet.sendTo(player, new FightEndMessage(result));
    }
}
