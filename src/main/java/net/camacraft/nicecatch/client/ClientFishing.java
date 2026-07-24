package net.camacraft.nicecatch.client;

import net.camacraft.nicecatch.FightPhase;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.RodUtil;
import net.camacraft.nicecatch.network.CastMessage;
import net.camacraft.nicecatch.network.FightEndMessage;
import net.camacraft.nicecatch.network.HookSetMessage;
import net.camacraft.nicecatch.network.NiceCatchNet;
import net.camacraft.nicecatch.network.ReelMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

/** Client-side state machine for the whole fishing flow. */
public class ClientFishing
{
    public enum Phase { IDLE, CHARGING, BITE, FIGHT, REEL }

    private static Phase phase = Phase.IDLE;
    private static InteractionHand chargeHand = InteractionHand.MAIN_HAND;
    private static int chargeTicks;
    private static int castCooldown;
    private static int biteTicks;
    private static boolean biteIsEntity;
    private static int fightTicks;
    private static boolean reelHeld;

    // Reel-in of a fishless line (no fight): pulling an empty hook or a snagged loot item home.
    private static int reelTicks;
    private static boolean reelItem;
    private static double reelStartDist;

    // Latest fight state from the server, plus a smoothed copy for the HUD.
    private static float progress;
    private static float shownProgress;
    private static float tension;
    private static float fatigue;
    private static boolean fishRunning;
    private static FightPhase fightPhase = FightPhase.PULL;

    private static final ReelTracker TRACKER = new ReelTracker();
    private static float revFeedback;
    private static int celebrateTicks;

    // Sensitivity ramp after leaving reel mode, so a still-spinning hand can't whip the camera.
    private static boolean wasCapturing;
    private static int mouseRampTicks;
    private static int mouseRampTotal;

    public static Phase phase()
    {
        return phase;
    }

    public static int chargeTicks()
    {
        return chargeTicks;
    }

    public static int biteTicks()
    {
        return biteTicks;
    }

    /** True when the current bite is a real fish (hook-set flow); false for vanilla loot nibbles. */
    public static boolean isEntityBite()
    {
        return biteIsEntity;
    }

    public static int fightTicks()
    {
        return fightTicks;
    }

    public static float shownProgress()
    {
        return shownProgress;
    }

    public static float tension()
    {
        return tension;
    }

    /** 0..1, how played-out the fish is (drives the "it's worn out" coaching hint). */
    public static float fatigue()
    {
        return fatigue;
    }

    public static boolean isFishRunning()
    {
        return fishRunning;
    }

    /** The fish's current fighting tactic, so the HUD can coach the right response. */
    public static FightPhase fightPhase()
    {
        return fightPhase;
    }

    /** True while reeling in a snagged loot item (vs an empty line), for the reel HUD. */
    public static boolean isReelItem()
    {
        return reelItem;
    }

    /** Whether hold-to-reel retrieval is enabled (server config, synced to the client). */
    public static boolean gradualReelEnabled()
    {
        return NiceCatchConfig.SERVER.gradualReelEnabled.get();
    }

    /** Ticks remaining of the "full bar" flourish after landing a fish. */
    public static int celebrateTicks()
    {
        return celebrateTicks;
    }

    /** The oscillating cast power bar: sweeps 0 -> 1 -> 0 repeatedly. */
    public static float chargeValue(float partialTick)
    {
        float half = NiceCatchConfig.CLIENT.chargeSweepTicks.get();
        float t = (chargeTicks + partialTick) / half;
        float sweep = t % 2.0F;
        return sweep <= 1.0F ? sweep : 2.0F - sweep;
    }

    public static boolean canStartCharge()
    {
        return phase == Phase.IDLE && castCooldown <= 0;
    }

    public static void beginCharge(InteractionHand hand)
    {
        phase = Phase.CHARGING;
        chargeHand = hand;
        chargeTicks = 0;
    }

    /** True while mouse movement should feed the reel instead of turning the camera. */
    public static boolean isCapturingMouse()
    {
        Minecraft mc = Minecraft.getInstance();
        return (phase == Phase.FIGHT || phase == Phase.REEL)
                && mc.screen == null && mc.player != null && mc.options.keyUse.isDown();
    }

    /** Called from the MouseHandler mixin with the raw deltas it swallowed. */
    public static void onCapturedMouse(double dx, double dy)
    {
        TRACKER.addDelta(dx, dy);
    }

    /**
     * 0..1 multiplier on camera input right after reel capture ends: starts at 0 and eases
     * back to full sensitivity over the configured ramp, absorbing leftover spin momentum.
     */
    public static float mouseDampFactor()
    {
        if (mouseRampTicks <= 0 || mouseRampTotal <= 0) return 1.0F;
        float t = 1.0F - (float) mouseRampTicks / (float) mouseRampTotal;
        return t * t;
    }

    /**
     * Damp factor for the camera path, with frame-accurate capture-end detection. A fight can
     * end mid-tick (the catch packet lands between client ticks) while the hand is still
     * spinning — the ramp must arm on the very frame capture stops, or the next turnPlayer
     * batch whips the camera at full sensitivity before clientTick ever notices.
     */
    public static float cameraDampFrame()
    {
        if (wasCapturing && !isCapturingMouse()) {
            mouseRampTotal = NiceCatchConfig.CLIENT.mouseRampTicks.get();
            mouseRampTicks = mouseRampTotal;
            wasCapturing = false;
        }
        return mouseDampFactor();
    }

    public static void clientTick(Minecraft mc)
    {
        LocalPlayer player = mc.player;
        if (player == null) {
            reset();
            return;
        }
        if (castCooldown > 0) castCooldown--;
        if (celebrateTicks > 0) celebrateTicks--;

        boolean capturing = isCapturingMouse();
        if (wasCapturing && !capturing) {
            mouseRampTotal = NiceCatchConfig.CLIENT.mouseRampTicks.get();
            mouseRampTicks = mouseRampTotal;
        } else if (capturing) {
            mouseRampTicks = 0;
        } else if (mouseRampTicks > 0) {
            mouseRampTicks--;
        }
        wasCapturing = capturing;

        switch (phase) {
            case IDLE -> {
                // A bobber is sitting in the water with nothing on it: hold right-click to
                // reel it back in gradually instead of the old instant retract.
                if (gradualReelEnabled() && player.fishing != null && mc.screen == null
                        && mc.options.keyUse.isDown() && !player.isHandsBusy()
                        && RodUtil.findRodHand(player) != null) {
                    startReel(player, false);
                }
            }
            case CHARGING -> {
                if (player.fishing != null || mc.screen != null || !RodUtil.isRod(player.getItemInHand(chargeHand))) {
                    phase = Phase.IDLE;
                    break;
                }
                if (!mc.options.keyUse.isDown()) {
                    NiceCatchNet.sendToServer(new CastMessage(chargeValue(0.0F), chargeHand));
                    phase = Phase.IDLE;
                    castCooldown = 4;
                } else {
                    chargeTicks++;
                }
            }
            case BITE -> {
                biteTicks++;
                if (player.fishing == null || biteTicks > 300) {
                    phase = Phase.IDLE;
                    break;
                }
                if (mc.screen == null && mc.options.keyUse.isDown()) {
                    if (biteIsEntity) {
                        // A real fish: set the hook and start the fight.
                        NiceCatchNet.sendToServer(new HookSetMessage());
                        startFight();
                    } else if (gradualReelEnabled()) {
                        // A loot item on the line: reel it in (no fight, no snapping).
                        startReel(player, true);
                    }
                }
            }
            case REEL -> {
                reelTicks++;
                if (player.fishing == null) {
                    phase = Phase.IDLE;
                    break;
                }
                boolean held = mc.screen == null && mc.options.keyUse.isDown();
                if (!held) {
                    // Let go: pause the reel-in; the camera pans back to the player.
                    NiceCatchNet.sendToServer(new ReelMessage(0.0F, 0.0F, false));
                    phase = Phase.IDLE;
                    break;
                }
                ReelTracker.Result input = TRACKER.consume(true);
                NiceCatchNet.sendToServer(new ReelMessage(input.crank(), input.lift(), true));

                if (NiceCatchConfig.CLIENT.reelClickSounds.get()) {
                    revFeedback += input.crank();
                    while (revFeedback >= 0.25F) {
                        revFeedback -= 0.25F;
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                                SoundEvents.LEVER_CLICK, 1.7F + player.getRandom().nextFloat() * 0.3F, 0.15F));
                    }
                }

                pullReelBobber(player, input.crank());
                double dist = player.distanceTo(player.fishing);
                double complete = NiceCatchConfig.SERVER.reelCompleteDistance.get();
                progress = (float) Mth.clamp((reelStartDist - dist) / Math.max(1.0D, reelStartDist - complete), 0.0D, 1.0D);
                shownProgress += (progress - shownProgress) * 0.4F;
            }
            case FIGHT -> {
                fightTicks++;
                if (player.fishing == null) {
                    phase = Phase.IDLE;
                    break;
                }
                reelHeld = mc.screen == null && mc.options.keyUse.isDown();
                ReelTracker.Result input = TRACKER.consume(reelHeld);
                NiceCatchNet.sendToServer(new ReelMessage(input.crank(), input.lift(), reelHeld));

                if (reelHeld && NiceCatchConfig.CLIENT.reelClickSounds.get()) {
                    revFeedback += input.crank();
                    while (revFeedback >= 0.25F) {
                        revFeedback -= 0.25F;
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                                SoundEvents.LEVER_CLICK, 1.7F + player.getRandom().nextFloat() * 0.3F, 0.15F));
                    }
                }
                shownProgress += (progress - shownProgress) * 0.35F;
            }
        }
    }

    // Where the follow camera is actually aimed: a point that eases after the bobber,
    // so the view trails the fish instead of pinning its every wobble.
    private static double followX, followY, followZ;
    private static boolean followInit;

    /**
     * Per-frame camera follow, called from the render tick. Rotates via player.turn() — the
     * same path as mouse input, which updates the previous-frame rotation too — so the pan
     * is perfectly smooth instead of stepping at tick rate.
     */
    public static void followFishFrame(Minecraft mc, float partialTick, float frameTicks)
    {
        LocalPlayer player = mc.player;
        if (player == null || (phase != Phase.FIGHT && phase != Phase.REEL) || player.fishing == null
                || !NiceCatchConfig.CLIENT.cameraFollowFish.get() || !isCapturingMouse()) {
            followInit = false;
            return;
        }

        Vec3 hookPos = player.fishing.getPosition(partialTick);
        if (!followInit) {
            followX = hookPos.x;
            followY = hookPos.y;
            followZ = hookPos.z;
            followInit = true;
        }
        // The aim point chases the fish at ~0.2/tick — this is the trailing lag.
        float trail = 1.0F - (float) Math.pow(0.8D, frameTicks);
        followX += (hookPos.x - followX) * trail;
        followY += (hookPos.y - followY) * trail;
        followZ += (hookPos.z - followZ) * trail;

        double dx = followX - player.getX();
        double dy = followY - player.getEyeY();
        double dz = followZ - player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.5D) return; // fish is basically underfoot; nothing sensible to aim at

        float wantYaw = (float) Math.toDegrees(Mth.atan2(dz, dx)) - 90.0F;
        float wantPitch = Mth.clamp((float) -Math.toDegrees(Mth.atan2(dy, horiz)), -30.0F, 80.0F);

        // Frame-rate-independent easing toward the aim point, rate-capped so it pans, never snaps.
        float strength = NiceCatchConfig.CLIENT.cameraFollowStrength.get().floatValue();
        float k = 1.0F - (float) Math.pow(1.0D - strength, frameTicks);
        float dYaw = Mth.clamp(Mth.wrapDegrees(wantYaw - player.getYRot()) * k, -10.0F * frameTicks, 10.0F * frameTicks);
        float dPitch = Mth.clamp((wantPitch - player.getXRot()) * k, -6.0F * frameTicks, 6.0F * frameTicks);
        player.turn(dYaw / 0.15D, dPitch / 0.15D);
    }

    // ---- Packet handlers (client main thread) ----

    public static void handleBite(boolean biting, boolean entity)
    {
        if (biting) {
            biteIsEntity = entity;
            if (phase == Phase.IDLE) {
                phase = Phase.BITE;
                biteTicks = 0;
                Minecraft mc = Minecraft.getInstance();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1.4F, 0.5F));
            }
        } else if (phase == Phase.BITE) {
            phase = Phase.IDLE;
            biteIsEntity = false;
        }
    }

    public static void handleFightTick(float newProgress, float newTension, float newFatigue, boolean running, byte phaseId)
    {
        if (phase != Phase.FIGHT) return;
        progress = newProgress;
        tension = newTension;
        fatigue = newFatigue;
        fishRunning = running;
        fightPhase = FightPhase.byId(phaseId);
    }

    public static void handleFightEnd(byte result)
    {
        boolean caught = result == FightEndMessage.CAUGHT;
        reset();
        if (caught) {
            // Linger on a full bar for a moment so the catch lands visually.
            celebrateTicks = 20;
        }
    }

    public static void reset()
    {
        phase = Phase.IDLE;
        reelHeld = false;
        // wasCapturing intentionally survives reset(): the tick after a fight ends must still
        // see the capture->free transition to start the sensitivity ramp.
        fishRunning = false;
        fightPhase = FightPhase.PULL;
        reelItem = false;
        progress = shownProgress = 0.0F;
        tension = 0.0F;
        fatigue = 0.0F;
        revFeedback = 0.0F;
        TRACKER.reset();
    }

    private static void startFight()
    {
        phase = Phase.FIGHT;
        fightTicks = 0;
        // The bar shows line retrieved; the server's first fight tick fills in the real value.
        progress = shownProgress = 0.0F;
        tension = 0.0F;
        fatigue = 0.0F;
        fishRunning = false;
        fightPhase = FightPhase.PULL;
        revFeedback = 0.0F;
        TRACKER.reset();
    }

    /** Enter reel-in mode for a fishless line (empty hook, or a snagged loot item). */
    private static void startReel(LocalPlayer player, boolean item)
    {
        phase = Phase.REEL;
        reelItem = item;
        reelTicks = 0;
        reelStartDist = player.fishing != null ? Math.max(2.0D, player.distanceTo(player.fishing)) : 2.0D;
        progress = shownProgress = 0.0F;
        revFeedback = 0.0F;
        TRACKER.reset();
    }

    /**
     * Slides the client's own bobber toward the player for a smooth reel-in — the server pulls
     * its copy in lockstep and is what actually decides when the line is landed. The client hook
     * self-simulates and ignores server position, so moving it locally is what the player sees.
     */
    private static void pullReelBobber(LocalPlayer player, float crank)
    {
        FishingHook hook = player.fishing;
        if (hook == null) return;
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        double speedBps = reelItem ? cfg.itemReelSpeed.get() : cfg.emptyReelSpeed.get();
        double crankFrac = Mth.clamp(crank / cfg.maxRevolutionsPerTick.get(), 0.0D, 1.0D);
        double step = speedBps / 20.0D * (0.45D + 0.55D * crankFrac);

        Vec3 aim = new Vec3(player.getX(), player.getY() + 0.3D, player.getZ());
        Vec3 pos = hook.position();
        Vec3 to = aim.subtract(pos);
        double dist = to.length();
        if (dist <= cfg.reelCompleteDistance.get() + 0.1D) return; // let the server land and remove it
        Vec3 next = pos.add(to.scale(step / dist));
        hook.setPos(next.x, next.y, next.z);
    }
}
