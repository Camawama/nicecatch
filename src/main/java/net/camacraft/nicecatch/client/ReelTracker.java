package net.camacraft.nicecatch.client;

import net.camacraft.nicecatch.NiceCatchConfig;

/**
 * Turns raw mouse deltas into "crank revolutions" (circular motion) and "lift" (pulling the mouse up).
 * Straight back-and-forth shaking produces ~180 degree angle flips which are rejected, so only genuine
 * circular motion cranks the reel.
 */
public class ReelTracker
{
    private double smoothX;
    private double smoothY;
    private float lastAngle = Float.NaN;
    private int stillFrames;
    /** Consecutive same-direction rotation samples; crank needs a real, coherent circle. */
    private int coherentSpin;
    private float spinSign;
    /** Frames since rotation was last in progress; lift is only read when this hits zero. */
    private int circlingFrames;

    private float crank;
    /**
     * How high the rod is held, 0..1 — a LEVEL, not a rate. One upward pull raises it and
     * holding the mouse still KEEPS it raised (a real rod held high costs nothing to keep
     * there); pushing the mouse back down lowers it. It survives cranking untouched, so
     * "lift, then reel" works without pumping the mouse off the pad.
     */
    private float liftLevel;
    private float side;
    private float scrollCrank;

    public void addDelta(double dx, double dy)
    {
        double sens = NiceCatchConfig.CLIENT.reelSensitivity.get();
        smoothX = smoothX * 0.55D + dx * 0.45D;
        smoothY = smoothY * 0.55D + dy * 0.45D;

        // Sideways swing (signed, right-positive) with mostly-horizontal motion: the input
        // for answering a darting fish's PULL LEFT / PULL RIGHT prompt. Gated off while a
        // circle is in progress — a crank sweeps through every direction, and it must never
        // answer a dart by accident. Break the circle, then yank.
        if (circlingFrames == 0 && Math.abs(dx) > Math.abs(dy) * 1.2D) {
            side += (float) (dx * 0.01D * sens);
        }

        double mag = Math.hypot(smoothX, smoothY);
        if (mag > 1.2D) {
            stillFrames = 0;
            float angle = (float) Math.atan2(smoothY, smoothX);
            if (!Float.isNaN(lastAngle)) {
                float d = wrapRadians(angle - lastAngle);
                float abs = Math.abs(d);
                if (abs > 0.015F && abs < (float) (Math.PI / 2.0D)) {
                    // Crank demands COHERENT rotation: several samples spinning the same
                    // way. The incidental angle-jitter of a hard straight pull flips signs
                    // and never cranks — lifting the rod must not secretly reel.
                    float sign = Math.signum(d);
                    coherentSpin = sign == spinSign ? coherentSpin + 1 : 1;
                    spinSign = sign;
                    if (coherentSpin >= 3) {
                        crank += (float) (abs / (2.0D * Math.PI) * sens);
                        circlingFrames = 6;
                    }
                } else if (abs >= (float) (Math.PI / 2.0D)) {
                    coherentSpin = 0;
                }
            }
            lastAngle = angle;
        } else if (++stillFrames > 8) {
            lastAngle = Float.NaN;
            coherentSpin = 0;
        }

        if (circlingFrames > 0) circlingFrames--;
        // Lift level moves only on deliberate straight strokes — never inside a circle
        // (whose up- and down-strokes must neither raise nor lower the held rod). Up
        // raises and stays; down lowers a touch faster (dropping the tip is easy).
        if (circlingFrames == 0) {
            if (dy < 0.0D && -smoothY > Math.abs(smoothX) * 1.2D) {
                liftLevel = Math.min(1.0F, liftLevel + (float) (-dy * 0.008D * sens));
            } else if (dy > 0.0D && smoothY > Math.abs(smoothX) * 1.2D) {
                liftLevel = Math.max(0.0F, liftLevel - (float) (dy * 0.012D * sens));
            }
        }
    }

    /** Extra crank from the scroll wheel (an alternative to circling). */
    public void addScrollCrank(float amount)
    {
        scrollCrank += amount;
    }

    /**
     * Consumes and returns this tick's input. While a dart prompt is up the accessibility
     * hold-to-reel floor is suspended: the server only accepts the answering yank from a
     * stopped reel, and for hold-to-reel users merely gripping the rod IS reeling — without
     * this pause they could never answer a dart at all.
     */
    public Result consume(boolean holding, boolean dartActive)
    {
        // Scroll and circling are ALTERNATIVE cranks, not additive — the faster of the two
        // wins, so working both at once buys nothing. The cap leaves headroom above the
        // server's base full-crank rate: genuinely fast circling overdrives the reel.
        float outCrank = Math.min(Math.max(crank, scrollCrank), 0.5F);
        float outLift = liftLevel;
        float outSide = net.minecraft.util.Mth.clamp(side, -1.2F, 1.2F);
        crank = 0.0F;
        side = 0.0F;
        scrollCrank = 0.0F;
        // The held rod eases down only very slowly on its own — an occasional small re-tug
        // keeps it pinned high; releasing the grip drops it entirely.
        liftLevel *= 0.99F;
        if (holding && !dartActive && !NiceCatchConfig.CLIENT.requireCircularMotion.get()) {
            outCrank = Math.max(outCrank, 0.12F);
        }
        if (!holding) {
            outCrank = 0.0F;
            outLift = 0.0F;
            outSide = 0.0F;
            liftLevel = 0.0F;
        }
        return new Result(outCrank, outLift, outSide);
    }

    public void reset()
    {
        smoothX = smoothY = 0.0D;
        lastAngle = Float.NaN;
        stillFrames = 0;
        coherentSpin = 0;
        spinSign = 0.0F;
        circlingFrames = 0;
        crank = 0.0F;
        liftLevel = 0.0F;
        side = 0.0F;
        scrollCrank = 0.0F;
    }

    private static float wrapRadians(float angle)
    {
        while (angle > Math.PI) angle -= (float) (2.0D * Math.PI);
        while (angle < -Math.PI) angle += (float) (2.0D * Math.PI);
        return angle;
    }

    public record Result(float crank, float lift, float side) {}
}
