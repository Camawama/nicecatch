package net.camacraft.nicecatch.server.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.phys.Vec3;

/**
 * Direct velocity steering for fish, bypassing pathfinding entirely.
 *
 * Vanilla water navigation is slow to start, gives up in shallow lakes, and caps out at a
 * leisurely cruise — none of which reads as "alive". These helpers accelerate the fish
 * toward/away from a point every tick, deflect off solid blocks, keep it submerged, and
 * turn the body into the motion. Water friction is 0.9/tick, so a sustained acceleration
 * `a` settles at roughly `9a` blocks/tick of speed.
 */
public final class FishSteering
{
    private FishSteering() {}

    public static void swimToward(AbstractFish fish, Vec3 target, double accel, double maxSpeed)
    {
        Vec3 dir = target.subtract(fish.position());
        if (dir.lengthSqr() < 1.0E-4D) return;
        steer(fish, dir.normalize(), accel, maxSpeed);
    }

    public static void swimAway(AbstractFish fish, Vec3 from, double accel, double maxSpeed)
    {
        Vec3 dir = fish.position().subtract(from);
        dir = new Vec3(dir.x, dir.y * 0.3D, dir.z); // flee mostly horizontally
        if (dir.lengthSqr() < 1.0E-4D) {
            double angle = fish.getRandom().nextDouble() * Math.PI * 2.0D;
            dir = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        }
        steer(fish, dir.normalize(), accel, maxSpeed);
    }

    /** Sideways impulse for evasive zig-zags while fleeing. */
    public static void jink(AbstractFish fish, double strength)
    {
        Vec3 v = fish.getDeltaMovement();
        Vec3 side = new Vec3(-v.z, 0.0D, v.x);
        if (side.lengthSqr() < 1.0E-6D) return;
        double sign = fish.getRandom().nextBoolean() ? 1.0D : -1.0D;
        fish.setDeltaMovement(v.add(side.normalize().scale(strength * sign)));
    }

    private static void steer(AbstractFish fish, Vec3 dir, double accel, double maxSpeed)
    {
        if (!fish.isInWater()) return;
        dir = avoidWalls(fish, dir);
        dir = staySubmerged(fish, dir);

        Vec3 v = fish.getDeltaMovement().add(dir.scale(accel));
        double horiz = v.horizontalDistance();
        if (horiz > maxSpeed) {
            v = new Vec3(v.x * maxSpeed / horiz, v.y, v.z * maxSpeed / horiz);
        }
        v = new Vec3(v.x, Mth.clamp(v.y, -maxSpeed * 0.6D, maxSpeed * 0.6D), v.z);
        fish.setDeltaMovement(v);
        faceMovement(fish);
    }

    /** If the heading runs into a solid block within ~1.5 blocks, rotate away until it doesn't. */
    private static Vec3 avoidWalls(AbstractFish fish, Vec3 dir)
    {
        if (isOpen(fish, dir)) return dir;
        for (int step = 1; step <= 3; step++) {
            double angle = step * (Math.PI / 3.0D);
            Vec3 left = rotateY(dir, angle);
            if (isOpen(fish, left)) return left;
            Vec3 right = rotateY(dir, -angle);
            if (isOpen(fish, right)) return right;
        }
        return dir.scale(-1.0D);
    }

    private static boolean isOpen(AbstractFish fish, Vec3 dir)
    {
        Vec3 probe = fish.position().add(dir.scale(1.5D)).add(0.0D, fish.getBbHeight() * 0.5D, 0.0D);
        BlockPos pos = BlockPos.containing(probe);
        return fish.level().getFluidState(pos).is(FluidTags.WATER);
    }

    /** Nudge the heading downward when it would breach the surface. */
    private static Vec3 staySubmerged(AbstractFish fish, Vec3 dir)
    {
        if (dir.y <= 0.0D) return dir;
        BlockPos above = BlockPos.containing(fish.getX(), fish.getY() + 0.8D, fish.getZ());
        if (fish.level().getFluidState(above).is(FluidTags.WATER)) return dir;
        return new Vec3(dir.x, -0.1D, dir.z).normalize();
    }

    private static Vec3 rotateY(Vec3 v, double angle)
    {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    public static void faceMovement(AbstractFish fish)
    {
        Vec3 v = fish.getDeltaMovement();
        if (v.horizontalDistanceSqr() < 1.0E-5D) return;
        float yaw = (float) (Mth.atan2(v.z, v.x) * (180.0D / Math.PI)) - 90.0F;
        fish.setYRot(Mth.approachDegrees(fish.getYRot(), yaw, 30.0F));
        fish.yBodyRot = fish.getYRot();
        fish.yHeadRot = fish.getYRot();
    }
}
