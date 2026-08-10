package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.server.FishBehavior;
import net.camacraft.nicecatch.server.FishProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * The fish's ordinary day, replacing vanilla's random-swim pathfinding crawl: cruising on a
 * meandering heading, loitering in place, drifting between depths, and — for species that
 * like cover — lurking in kelp and seagrass. Which of these a fish favors comes from its
 * species profile, so little open-water schoolers dart about near the surface while a big
 * wary bottom-dweller spends its time hanging in the weeds.
 *
 * Runs at the lowest priority: every other behavior (fleeing, bobbers, food, hunting,
 * schooling) preempts it, so this is only what a fish does when nothing is happening.
 * All decisions are made on mode changes, not per tick; per-tick work is steering math.
 */
public class HabitatGoal extends Goal
{
    private enum Mode { CRUISE, LOITER, HIDE }

    private final PathfinderMob fish;
    private Mode mode = Mode.CRUISE;
    private int modeTicks;
    private double wanderAngle;
    private double targetY;
    private int pauseTicks;
    @Nullable private BlockPos coverPos;
    private double hoverAngle;

    public HabitatGoal(PathfinderMob fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        // The idle driver: anything more important preempts via goal priority, so the only
        // real conditions are "in water" and "not currently claimed by the fishing systems".
        if (!fish.isInWater()) return false;
        FishBehavior.FishState state = FishBehavior.state(fish);
        return !state.hooked && !FishBehavior.isScattering(fish);
    }

    @Override
    public boolean canContinueToUse()
    {
        return canUse();
    }

    @Override
    public void start()
    {
        wanderAngle = fish.getRandom().nextDouble() * Math.PI * 2.0D;
        pickMode();
    }

    @Override
    public void stop()
    {
        fish.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        if (--modeTicks <= 0) {
            pickMode();
        }
        if (pauseTicks > 0) {
            // Hanging in the water: fins barely moving, drifting to a stop.
            pauseTicks--;
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.88D));
            FishSteering.faceMovement(fish);
            return;
        }

        switch (mode) {
            case CRUISE -> {
                wanderAngle += (fish.getRandom().nextDouble() - 0.5D) * 0.3D;
                double vertical = Mth.clamp((targetY - fish.getY()) * 0.25D, -0.6D, 0.6D);
                Vec3 aim = fish.position().add(
                        Math.cos(wanderAngle) * 4.0D, vertical * 2.0D, Math.sin(wanderAngle) * 4.0D);
                FishSteering.swimToward(fish, aim, 0.011D, 0.13D);
                // The odd whim: a short pause, or a little burst of speed.
                if (fish.getRandom().nextFloat() < 0.008F) {
                    pauseTicks = 10 + fish.getRandom().nextInt(25);
                } else if (fish.getRandom().nextFloat() < 0.006F) {
                    FishSteering.jink(fish, 0.06D);
                }
            }
            case LOITER -> {
                // Mill about a spot: tiny corrections toward the preferred depth, little else.
                double vertical = Mth.clamp((targetY - fish.getY()) * 0.2D, -0.3D, 0.3D);
                wanderAngle += (fish.getRandom().nextDouble() - 0.5D) * 0.6D;
                Vec3 aim = fish.position().add(
                        Math.cos(wanderAngle) * 1.5D, vertical, Math.sin(wanderAngle) * 1.5D);
                FishSteering.swimToward(fish, aim, 0.005D, 0.06D);
                if (fish.getRandom().nextFloat() < 0.02F) {
                    pauseTicks = 8 + fish.getRandom().nextInt(16);
                }
            }
            case HIDE -> {
                if (coverPos == null) {
                    pickMode();
                    return;
                }
                // Hang in the weeds, swaying gently around the plant.
                hoverAngle += 0.03D;
                Vec3 aim = Vec3.atCenterOf(coverPos).add(
                        Math.cos(hoverAngle) * 0.6D, 0.1D, Math.sin(hoverAngle) * 0.6D);
                FishSteering.swimToward(fish, aim, 0.008D, 0.07D);
            }
        }
    }

    /**
     * Re-decide what to do for the next stretch. All the block probing (water column bounds,
     * cover search) happens here — once per mode change, never per tick.
     */
    private void pickMode()
    {
        RandomSource random = fish.getRandom();
        FishProfiles.FishProfile profile = FishProfiles.of(fish);
        modeTicks = 80 + random.nextInt(140);
        pauseTicks = 0;

        // Cover lovers try to keep a plant address on file; drop it if the plant is gone.
        if (coverPos != null && !isCover(fish.level().getBlockState(coverPos))) {
            coverPos = null;
        }
        if (profile.cover > 0.0F && coverPos == null && random.nextFloat() < 0.5F) {
            coverPos = findCover(random);
        }

        float wCruise = 1.0F;
        float wLoiter = 0.7F + (1.0F - profile.boldness) * 0.5F;
        float wHide = coverPos != null ? profile.cover * 1.6F : 0.0F;
        float roll = random.nextFloat() * (wCruise + wLoiter + wHide);
        mode = (roll -= wCruise) < 0.0F ? Mode.CRUISE
                : (roll - wLoiter) < 0.0F ? Mode.LOITER : Mode.HIDE;

        targetY = pickTargetDepth(profile, random);
        hoverAngle = random.nextDouble() * Math.PI * 2.0D;
    }

    /** Preferred cruising depth inside the local water column, per species. */
    private double pickTargetDepth(FishProfiles.FishProfile profile, RandomSource random)
    {
        BlockPos pos = fish.blockPosition();
        var level = fish.level();
        int up = 0;
        while (up < 24 && level.getFluidState(pos.above(up + 1)).is(FluidTags.WATER)) up++;
        int down = 0;
        while (down < 24 && level.getFluidState(pos.below(down + 1)).is(FluidTags.WATER)) down++;
        double surfaceY = fish.getY() + up + 0.8D;
        double bottomY = fish.getY() - down;

        double jitter = random.nextDouble() * 1.5D;
        return switch (profile.depth) {
            case SURFACE -> surfaceY - 1.2D - jitter;
            case BOTTOM -> bottomY + 0.6D + jitter;
            case OPEN -> bottomY + (surfaceY - bottomY) * (0.35D + random.nextDouble() * 0.3D);
        };
    }

    /** A handful of random probes for a kelp/seagrass block nearby; misses are fine and cheap. */
    @Nullable
    private BlockPos findCover(RandomSource random)
    {
        BlockPos center = fish.blockPosition();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 10; i++) {
            probe.setWithOffset(center,
                    random.nextInt(13) - 6, random.nextInt(7) - 3, random.nextInt(13) - 6);
            if (isCover(fish.level().getBlockState(probe))) {
                return probe.immutable();
            }
        }
        return null;
    }

    private static boolean isCover(BlockState state)
    {
        return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }
}
