package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.RodUtil;
import net.camacraft.nicecatch.compat.AquacultureCompat;
import net.camacraft.nicecatch.network.BiteMessage;
import net.camacraft.nicecatch.network.FightEndMessage;
import net.camacraft.nicecatch.network.FightTickMessage;
import net.camacraft.nicecatch.network.NiceCatchNet;
import net.camacraft.nicecatch.server.goal.FishSteering;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.AbstractFish;
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
            clearPendingBite(player.serverLevel(), session);
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

        int luck = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_LUCK, rod);
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
            AbstractFish fish = resolveFish(player.serverLevel(), session.pendingFish);
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
                                         InteractionHand hand, AbstractFish fish)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        FishFight fight = new FishFight();
        fight.hand = hand;
        fight.fishId = fish.getUUID();
        fight.strength = sizeStrength(fish);

        ItemStack rod = player.getItemInHand(hand);
        fight.tensionScale = Math.max(1.0F, AquacultureCompat.tensionScale(rod));
        fight.doubleCatchChance = AquacultureCompat.doubleCatchChance(rod);

        FishBehavior.setHooked(fish, true);
        FishBehavior.scatterAround(player.serverLevel(), hook.position(),
                cfg.scatterRadius.get(), cfg.scatterOnHookChance.get().floatValue(), fish);
        beginFight(player, session, hook, fight);
    }

    /** Hitbox area vs the reference area, on a sub-linear curve so small fish already differ a lot. */
    private static float sizeStrength(AbstractFish fish)
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
        fight.progress = 0.15F;
        fight.calmTicks = 15 + random.nextInt(25);
        fight.graceTicks = NiceCatchConfig.SERVER.escapeGraceTicks.get();
        session.fight = fight;
        session.prevBite = true;

        ServerLevel level = player.serverLevel();
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.4F, 1.2F);
        NiceCatchNet.sendTo(player, new FightTickMessage(fight.progress, fight.tension, false));
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
                clearPendingBite(player.serverLevel(), session);
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

        // An active nibble window: keep it honest and keep the bobber jiggling.
        if (session.pendingFish != null) {
            AbstractFish fish = resolveFish(level, session.pendingFish);
            if (fish == null || FishBehavior.isScattering(fish) || fish.distanceToSqr(hook) > 25.0D) {
                clearPendingBite(level, session);
                return;
            }
            session.pendingBiteTicks--;
            if (session.pendingBiteTicks <= 0) {
                clearPendingBite(level, session);
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

        // While fish are being courted, vanilla's invisible loot-fish never lures.
        if (hook.nibble <= 0 && FishBehavior.claimedCount(hook) > 0) {
            hook.timeUntilHooked = 0;
            if (hook.timeUntilLured < 100) hook.timeUntilLured = 100;
        }
        if (hook.nibble > 0) return; // an already-started loot bite plays out normally

        List<AbstractFish> candidates = FishBehavior.biteCandidates(hook);
        if (candidates.isEmpty()) return;

        InteractionHand hand = RodUtil.findRodHand(player);
        ItemStack rod = hand != null ? player.getItemInHand(hand) : ItemStack.EMPTY;
        int lure = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_SPEED, rod);
        float chancePerTick = cfg.biteChancePerSecond.get().floatValue() / 20.0F
                * (1.0F + 0.35F * lure)
                * AquacultureCompat.biteChanceMultiplier(rod);
        if (level.random.nextFloat() >= chancePerTick) return;

        // Luck of the Sea nudges the bite toward the biggest fish in the group.
        int luck = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_LUCK, rod);
        AbstractFish biter;
        if (luck > 0 && level.random.nextFloat() < 0.25F * luck) {
            biter = candidates.stream()
                    .max((a, b) -> Float.compare(a.getBbWidth() * a.getBbHeight(), b.getBbWidth() * b.getBbHeight()))
                    .orElse(candidates.get(0));
        } else {
            biter = candidates.get(level.random.nextInt(candidates.size()));
        }

        session.pendingFish = biter.getUUID();
        session.pendingBiteTicks = cfg.biteWindowTicks.get();
        FishBehavior.state(biter).biteBobber = hook;
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.18D, 0.0D));
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.35F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.2D, hook.getZ(),
                4, 0.12D, 0.05D, 0.12D, 0.0D);
    }

    private static void clearPendingBite(ServerLevel level, Session session)
    {
        if (session.pendingFish == null) return;
        AbstractFish fish = resolveFish(level, session.pendingFish);
        if (fish != null) {
            FishBehavior.state(fish).biteBobber = null;
        }
        session.pendingFish = null;
        session.pendingBiteTicks = 0;
    }

    @Nullable
    private static AbstractFish resolveFish(ServerLevel level, @Nullable UUID id)
    {
        if (id == null) return null;
        return level.getEntity(id) instanceof AbstractFish fish && fish.isAlive() ? fish : null;
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
                endFight(player, session, FightEndMessage.ESCAPED);
                return;
            }
            fight.hand = other;
        }

        AbstractFish fish = resolveFish(level, fight.fishId);
        if (fish == null) {
            // The fish died or unloaded mid-fight; nothing left on the line.
            endFight(player, session, FightEndMessage.ESCAPED);
            return;
        }
        // No loot-fish may sneak onto the line while a real one is hooked.
        hook.timeUntilHooked = 0;
        if (hook.timeUntilLured < 100) hook.timeUntilLured = 100;

        fight.ticks++;
        if (fight.graceTicks > 0) fight.graceTicks--;

        // Run/calm phases.
        boolean run;
        if (fight.runTicks > 0) {
            fight.runTicks--;
            run = fight.runTicks > 0;
            if (!run) {
                fight.calmTicks = 30 + random.nextInt(40);
            }
        } else {
            fight.calmTicks--;
            run = fight.calmTicks <= 0;
            if (run) {
                fight.runTicks = (int) ((20 + random.nextInt(20)) * (0.7F + 0.6F * fight.strength));
                level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                        SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.4F, 0.7F);
            }
        }

        float crank = Math.min(fight.pendingCrank, cfg.maxRevolutionsPerTick.get().floatValue());
        float lift = Math.min(fight.pendingLift, 0.6F);
        fight.pendingCrank = 0.0F;
        fight.pendingLift = 0.0F;

        float revRate = cfg.progressPerRevolution.get().floatValue();
        if (fight.holding) {
            if (run) {
                fight.progress += crank * revRate * cfg.runReelEffectiveness.get().floatValue();
                fight.tension += (crank * cfg.tensionPerRevolutionRun.get().floatValue()
                        + 0.004F * (0.5F + fight.strength)) / fight.tensionScale;
                float resist = Mth.clamp(lift * cfg.liftRunResistance.get().floatValue(), 0.0F, 0.85F);
                fight.progress -= cfg.runPullPerTick.get().floatValue() * (0.5F + 0.8F * fight.strength) * (1.0F - resist);
            } else {
                fight.progress += crank * revRate + lift * cfg.liftProgressBonus.get().floatValue();
                fight.tension -= cfg.tensionRecoveryPerTick.get().floatValue();
            }
        } else {
            float decay = cfg.slackLossPerTick.get().floatValue() * (0.5F + fight.strength);
            fight.progress -= run ? decay * 2.5F : decay;
            fight.tension -= cfg.tensionRecoveryPerTick.get().floatValue() * 2.0F;
        }
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

        // Fish escapes when all progress is lost.
        if (fight.progress <= 0.0F && fight.graceTicks <= 0) {
            freeFish(fish, hook.position());
            level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                    SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.3F, 0.6F);
            endFight(player, session, FightEndMessage.ESCAPED);
            return;
        }
        fight.progress = Math.max(fight.progress, fight.graceTicks > 0 ? 0.02F : 0.0F);

        // Worn down — but you still have to bring it in close before it counts as landed.
        if (fight.progress >= 1.0F) {
            double landDist = cfg.landDistance.get();
            if (fish.distanceToSqr(player) <= landDist * landDist) {
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
            fight.progress = 1.0F; // hold full: the fish is beaten, keep cranking it closer
        }

        moveHookedFish(player, fight, hook, fish, level, random, run, crank);
        NiceCatchNet.sendTo(player, new FightTickMessage(fight.progress, fight.tension, run));
    }

    /** Launch the beaten fish out of the water toward the player, vanilla-loot style. */
    private static void landEntityFish(ServerPlayer player, Session session, FishingHook hook,
                                       FishFight fight, AbstractFish fish, ServerLevel level)
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
            InteractionHand hand = fight.hand;
            rod.hurtAndBreak(rodDamage, player, p -> p.broadcastBreakEvent(hand));
        }
        level.playSound(null, fish.getX(), fish.getY(), fish.getZ(),
                SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY(), fish.getZ(), 10, 0.2D, 0.1D, 0.2D, 0.0D);
        player.swing(fight.hand, true);
        hook.discard();
    }

    /** The fish is the thing that runs; the bobber tracks it across the surface. */
    private static void moveHookedFish(ServerPlayer player, FishFight fight, FishingHook hook,
                                       AbstractFish fish, ServerLevel level, RandomSource random,
                                       boolean run, float crank)
    {
        Vec3 toPlayer = new Vec3(player.getX() - fish.getX(), 0.0D, player.getZ() - fish.getZ());
        double dist = toPlayer.length();

        if (run) {
            if (dist > 0.01D && dist < 26.0D) { // vanilla breaks the line beyond 32 blocks; stay inside that
                Vec3 away = toPlayer.normalize().scale(-(0.05D + 0.06D * fight.strength));
                Vec3 wobble = new Vec3((random.nextDouble() - 0.5D) * 0.06D, 0.0D, (random.nextDouble() - 0.5D) * 0.06D);
                fish.setDeltaMovement(fish.getDeltaMovement().scale(0.7D).add(away).add(wobble));
            } else if (dist >= 26.0D) {
                fish.setDeltaMovement(fish.getDeltaMovement().scale(0.5D));
            }
            if (fish.getY() > hook.getY() - 0.3D) {
                fish.setDeltaMovement(fish.getDeltaMovement().add(0.0D, -0.02D, 0.0D));
            }
            if (fight.ticks % 4 == 0) {
                level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.3D, fish.getZ(),
                        4, 0.15D, 0.05D, 0.15D, 0.0D);
            }
            if (fight.ticks % 14 == 0) {
                level.playSound(null, fish.getX(), fish.getY(), fish.getZ(),
                        SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.2F + random.nextFloat() * 0.3F);
            }
        } else if (fight.holding && crank > 0.001F && dist > 1.2D) {
            double pull = Math.min(0.16D, crank * 1.8D);
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.8D).add(toPlayer.normalize().scale(pull)));
            if (fight.ticks % 6 == 0) {
                level.sendParticles(ParticleTypes.FISHING, fish.getX(), fish.getY() + 0.1D, fish.getZ(),
                        2, 0.1D, 0.05D, 0.1D, 0.0D);
            }
        } else if (fish.distanceToSqr(hook) > 4.0D) {
            Vec3 toHook = hook.position().subtract(fish.position()).normalize().scale(0.03D);
            fish.setDeltaMovement(fish.getDeltaMovement().add(toHook));
        }
        FishSteering.faceMovement(fish);

        // The bobber chases the fish across the surface by velocity (teleporting it every tick
        // starves the client of movement it can interpolate, and the bobber looks frozen).
        Vec3 toFish = new Vec3(fish.getX() - hook.getX(), 0.0D, fish.getZ() - hook.getZ());
        double gap = toFish.length();
        if (gap > 0.25D) {
            double chase = Math.min(0.5D, gap * 0.3D);
            Vec3 hv = hook.getDeltaMovement();
            hook.setDeltaMovement(hv.x * 0.3D + toFish.x / gap * chase, hv.y,
                    hv.z * 0.3D + toFish.z / gap * chase);
        }
    }

    /** A real fish comes off the line: unhook it and let it bolt. */
    private static void freeFish(AbstractFish fish, Vec3 from)
    {
        FishBehavior.setHooked(fish, false);
        FishBehavior.scatter(fish, from, NiceCatchConfig.SERVER.scatterDurationTicks.get());
    }

    /** Fight teardown that releases the fish if one was on the line (logout, bobber gone...). */
    private static void unhookFish(ServerLevel level, @Nullable FishFight fight)
    {
        if (fight == null || fight.fishId == null) return;
        AbstractFish fish = resolveFish(level, fight.fishId);
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
