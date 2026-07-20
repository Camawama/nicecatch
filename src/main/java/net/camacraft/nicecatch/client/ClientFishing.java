package net.camacraft.nicecatch.client;

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
import net.minecraft.world.InteractionHand;

/** Client-side state machine for the whole fishing flow. */
public class ClientFishing
{
    public enum Phase { IDLE, CHARGING, BITE, FIGHT }

    private static Phase phase = Phase.IDLE;
    private static InteractionHand chargeHand = InteractionHand.MAIN_HAND;
    private static int chargeTicks;
    private static int castCooldown;
    private static int biteTicks;
    private static boolean biteIsEntity;
    private static int fightTicks;
    private static boolean reelHeld;

    // Latest fight state from the server, plus a smoothed copy for the HUD.
    private static float progress;
    private static float shownProgress;
    private static float tension;
    private static boolean fishRunning;

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

    public static boolean isFishRunning()
    {
        return fishRunning;
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
        return phase == Phase.FIGHT && mc.screen == null && mc.player != null && mc.options.keyUse.isDown();
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
            case IDLE -> {}
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
                // Only real fish get a hook-set; a loot nibble's right-click is a vanilla
                // retrieve and passes through the click handlers untouched.
                if (biteIsEntity && mc.screen == null && mc.options.keyUse.isDown()) {
                    NiceCatchNet.sendToServer(new HookSetMessage());
                    startFight();
                }
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

    public static void handleFightTick(float newProgress, float newTension, boolean running)
    {
        if (phase != Phase.FIGHT) return;
        progress = newProgress;
        tension = newTension;
        fishRunning = running;
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
        progress = shownProgress = 0.0F;
        tension = 0.0F;
        revFeedback = 0.0F;
        TRACKER.reset();
    }

    private static void startFight()
    {
        phase = Phase.FIGHT;
        fightTicks = 0;
        progress = shownProgress = 0.15F;
        tension = 0.0F;
        fishRunning = false;
        revFeedback = 0.0F;
        TRACKER.reset();
    }
}
