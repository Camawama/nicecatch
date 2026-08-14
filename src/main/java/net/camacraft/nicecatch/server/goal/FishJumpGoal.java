package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.camacraft.nicecatch.server.FishProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The leap: species flagged {@code jumps:true} in fishProfiles (salmon, tropical fish by
 * default) occasionally throw themselves clear of the water, splash, and drop back in.
 * Pure spectacle — a living pond breaks its own surface now and then — and an honest tell
 * that fish are around before a single cast is made.
 *
 * One-shot: the goal fires the impulse and immediately yields. The fish is briefly marked
 * jumping so the global keep-under-the-surface clamp lets the leap actually leave the
 * water instead of slapping it back down mid-launch.
 */
public class FishJumpGoal extends Goal
{
    private final PathfinderMob fish;

    public FishJumpGoal(PathfinderMob fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.JUMP));
    }

    @Override
    public boolean canUse()
    {
        float chance = NiceCatchConfig.SERVER.fishJumpChance.get().floatValue();
        if (chance <= 0.0F || !FishProfiles.of(fish).jumps) return false;
        if (!fish.isInWater() || FishBehavior.isHooked(fish) || FishBehavior.isScattering(fish)) return false;
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.bobber != null || state.biteBobber != null) return false;
        // A leisurely roll roughly every two seconds per fish, staggered.
        if ((fish.tickCount + fish.getId()) % 40 != 0) return false;
        if (fish.getRandom().nextFloat() >= chance) return false;

        // Near the surface with open air above it — no leaping at a ceiling of ice.
        BlockPos pos = fish.blockPosition();
        var level = fish.level();
        int up = 0;
        while (up < 3 && level.getFluidState(pos.above(up + 1)).is(FluidTags.WATER)) up++;
        return up < 3 && level.getBlockState(pos.above(up + 1)).isAir();
    }

    @Override
    public void start()
    {
        FishBehavior.state(fish).jumpingUntil = fish.level().getGameTime() + 30;
        Vec3 v = fish.getDeltaMovement();
        // Leap along the direction it is actually SWIMMING; a fish hanging still picks a
        // fresh heading instead of trusting a stale yaw.
        double heading;
        if (v.horizontalDistanceSqr() > 1.0E-4D) {
            heading = Math.atan2(-v.x, v.z);
        } else {
            heading = fish.getRandom().nextDouble() * Math.PI * 2.0D;
        }
        fish.setDeltaMovement(
                v.x - Math.sin(heading) * 0.22D,
                0.46D + fish.getRandom().nextDouble() * 0.18D,
                v.z + Math.cos(heading) * 0.22D);
        fish.setYRot((float) Math.toDegrees(heading));
        fish.yBodyRot = fish.getYRot();
        fish.hurtMarked = true; // the leap must reach clients whole, not as a stutter
        if (fish.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.3D, fish.getZ(),
                    8, 0.25D, 0.1D, 0.25D, 0.0D);
            level.playSound(null, fish.getX(), fish.getY(), fish.getZ(),
                    SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.5F,
                    1.1F + fish.getRandom().nextFloat() * 0.4F);
        }
    }

    @Override
    public boolean canContinueToUse()
    {
        return false; // one-shot: the impulse is the whole behavior
    }
}
