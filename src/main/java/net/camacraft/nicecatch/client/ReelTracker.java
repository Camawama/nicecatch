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

    private float crank;
    private float lift;

    public void addDelta(double dx, double dy)
    {
        double sens = NiceCatchConfig.CLIENT.reelSensitivity.get();
        smoothX = smoothX * 0.55D + dx * 0.45D;
        smoothY = smoothY * 0.55D + dy * 0.45D;

        double mag = Math.hypot(smoothX, smoothY);
        if (mag > 1.2D) {
            stillFrames = 0;
            float angle = (float) Math.atan2(smoothY, smoothX);
            if (!Float.isNaN(lastAngle)) {
                float d = wrapRadians(angle - lastAngle);
                float abs = Math.abs(d);
                if (abs > 0.015F && abs < (float) (Math.PI / 2.0D)) {
                    crank += (float) (abs / (2.0D * Math.PI) * sens);
                }
            }
            lastAngle = angle;
        } else if (++stillFrames > 8) {
            lastAngle = Float.NaN;
        }

        // Pulling the mouse up (negative dy) with mostly-vertical motion counts as lifting the rod.
        if (dy < 0.0D && -smoothY > Math.abs(smoothX) * 1.2D) {
            lift += (float) (-dy * 0.01D * sens);
        }
    }

    /** Consumes and returns this tick's input. */
    public Result consume(boolean holding)
    {
        float outCrank = Math.min(crank, 0.35F);
        float outLift = Math.min(lift, 0.8F);
        crank = 0.0F;
        lift = 0.0F;
        if (holding && !NiceCatchConfig.CLIENT.requireCircularMotion.get()) {
            outCrank = Math.max(outCrank, 0.12F);
        }
        if (!holding) {
            outCrank = 0.0F;
            outLift = 0.0F;
        }
        return new Result(outCrank, outLift);
    }

    public void reset()
    {
        smoothX = smoothY = 0.0D;
        lastAngle = Float.NaN;
        stillFrames = 0;
        crank = 0.0F;
        lift = 0.0F;
    }

    private static float wrapRadians(float angle)
    {
        while (angle > Math.PI) angle -= (float) (2.0D * Math.PI);
        while (angle < -Math.PI) angle += (float) (2.0D * Math.PI);
        return angle;
    }

    public record Result(float crank, float lift) {}
}
