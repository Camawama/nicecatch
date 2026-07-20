package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.RodUtil;
import net.camacraft.nicecatch.network.BiteMessage;
import net.camacraft.nicecatch.network.FightEndMessage;
import net.camacraft.nicecatch.network.FightTickMessage;
import net.camacraft.nicecatch.network.NiceCatchNet;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public class ServerFishingManager
{
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static class Session
    {
        boolean dispatchingUse;
        double pendingCastMultiplier = -1.0D;
        boolean prevBite;
        float biteCarry;
        int suppressBiteTicks;
        FishFight fight;
    }

    private static Session session(Player player)
    {
        return SESSIONS.computeIfAbsent(player.getUUID(), uuid -> new Session());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        SESSIONS.clear();
    }

    /**
     * Right-clicking a rod never casts or reels directly anymore; those flow through our packets.
     * We only let the vanilla click through for retrieving an idle bobber or a hooked entity/item.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (event.getLevel().isClientSide) return;
        if (!RodUtil.isRod(event.getItemStack())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Session session = session(player);
        if (session.dispatchingUse) return;

        FishingHook hook = player.fishing;
        boolean fishOnLine = hook != null && hook.getHookedIn() == null && hook.nibble > 0;
        if (hook == null || session.fight != null || fishOnLine) {
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
        if (hook == null || hand == null || hook.nibble <= 0 || hook.getHookedIn() != null) {
            NiceCatchNet.sendTo(player, new FightEndMessage(FightEndMessage.ESCAPED));
            return;
        }

        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        RandomSource random = player.getRandom();
        FishFight fight = new FishFight();
        fight.hand = hand;
        float min = cfg.fishStrengthMin.get().floatValue();
        float max = Math.max(min, cfg.fishStrengthMax.get().floatValue());
        fight.strength = min + random.nextFloat() * (max - min);
        if (hook.isOpenWaterFishing()) {
            fight.strength = Math.min(1.0F, fight.strength + 0.1F);
        }
        fight.progress = 0.15F;
        fight.calmTicks = 15 + random.nextInt(25);
        fight.graceTicks = cfg.escapeGraceTicks.get();
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
                    session.fight = null;
                    NiceCatchNet.sendTo(player, new FightEndMessage(FightEndMessage.ESCAPED));
                }
                if (session.prevBite) {
                    session.prevBite = false;
                    NiceCatchNet.sendTo(player, new BiteMessage(false));
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

        // No fight yet: watch for bites and stretch the vanilla bite window.
        boolean biting = hook.nibble > 0 && hook.getHookedIn() == null;
        if (biting) {
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

        if (biting != session.prevBite) {
            session.prevBite = biting;
            NiceCatchNet.sendTo(player, new BiteMessage(biting));
        }
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
                releaseFish(hook);
                endFight(player, session, FightEndMessage.ESCAPED);
                return;
            }
            fight.hand = other;
        }

        hook.nibble = 60; // keep the fish on the line for the duration of the fight
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
                fight.tension += crank * cfg.tensionPerRevolutionRun.get().floatValue()
                        + 0.004F * (0.5F + fight.strength);
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
            hook.discard(); // the line broke: bobber and fish are gone
            endFight(player, session, FightEndMessage.SNAPPED);
            return;
        }

        // Fish escapes when all progress is lost.
        if (fight.progress <= 0.0F && fight.graceTicks <= 0) {
            releaseFish(hook);
            level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                    SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.3F, 0.6F);
            endFight(player, session, FightEndMessage.ESCAPED);
            return;
        }
        fight.progress = Math.max(fight.progress, fight.graceTicks > 0 ? 0.02F : 0.0F);

        // Landed it!
        if (fight.progress >= 1.0F) {
            session.dispatchingUse = true;
            try {
                player.gameMode.useItem(player, level, player.getItemInHand(fight.hand), fight.hand);
            } finally {
                session.dispatchingUse = false;
            }
            player.swing(fight.hand, true);
            if (cfg.bonusXp.get() && fight.strength > 0.6F) {
                int bonus = Math.round((fight.strength - 0.6F) * 8.0F);
                if (bonus > 0) {
                    level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5D, player.getZ(), bonus));
                }
            }
            endFight(player, session, FightEndMessage.CAUGHT);
            return;
        }

        moveHook(player, fight, hook, level, random, run, crank);
        NiceCatchNet.sendTo(player, new FightTickMessage(fight.progress, fight.tension, run));
    }

    /** Drags the bobber around so the fight is visible in the world. */
    private static void moveHook(ServerPlayer player, FishFight fight, FishingHook hook,
                                 ServerLevel level, RandomSource random, boolean run, float crank)
    {
        if (!hook.isInWater()) return;

        Vec3 toPlayer = new Vec3(player.getX() - hook.getX(), 0.0D, player.getZ() - hook.getZ());
        double dist = toPlayer.length();

        if (run) {
            if (dist > 0.01D && dist < 28.0D) { // vanilla breaks the line beyond 32 blocks; stay inside that
                Vec3 away = toPlayer.normalize().scale(-(0.045D + 0.05D * fight.strength));
                Vec3 wobble = new Vec3((random.nextDouble() - 0.5D) * 0.05D, 0.0D, (random.nextDouble() - 0.5D) * 0.05D);
                hook.setDeltaMovement(hook.getDeltaMovement().add(away).add(wobble));
            }
            if (fight.ticks % 4 == 0) {
                level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.2D, hook.getZ(),
                        4, 0.15D, 0.05D, 0.15D, 0.0D);
            }
            if (fight.ticks % 14 == 0) {
                level.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                        SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.2F + random.nextFloat() * 0.3F);
            }
        } else if (fight.holding && crank > 0.001F && dist > 1.5D) {
            double pull = Math.min(0.09D, crank * 1.2D);
            hook.setDeltaMovement(hook.getDeltaMovement().add(toPlayer.normalize().scale(pull)));
            if (fight.ticks % 6 == 0) {
                level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + 0.1D, hook.getZ(),
                        2, 0.1D, 0.05D, 0.1D, 0.0D);
            }
        }
    }

    /** Lets vanilla clean up the bite naturally on the hook's next tick. */
    private static void releaseFish(FishingHook hook)
    {
        if (hook.nibble > 1) hook.nibble = 1;
    }

    private static void endFight(ServerPlayer player, Session session, byte result)
    {
        session.fight = null;
        session.prevBite = false;
        session.suppressBiteTicks = 5;
        NiceCatchNet.sendTo(player, new FightEndMessage(result));
    }
}
