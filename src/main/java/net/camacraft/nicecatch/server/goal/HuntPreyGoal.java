package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.camacraft.nicecatch.server.FishProfiles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Predation: species flagged as predators hunt fish much smaller than themselves. A hungry
 * predator picks the nearest suitable prey, runs it down, and eats it whole (no item spray —
 * the prey simply vanishes in a puff of scales); then it is satiated and ignores fish for a
 * while. The chase itself drives the prey's flight through the ordinary scatter system, so
 * fleeing, jinking, wall avoidance, and school panic all come along for free.
 *
 * Only much-smaller fish qualify (hitbox area ratio from the predator's profile), never the
 * predator's own kind, and never a fish a player has hooked.
 */
public class HuntPreyGoal extends Goal
{
    /** A chase the predator hasn't won by now is abandoned (prey wins on stamina). */
    private static final int CHASE_TIMEOUT_TICKS = 160;

    private final PathfinderMob fish;
    @Nullable private PathfinderMob prey;
    private int chaseTicks;

    public HuntPreyGoal(PathfinderMob fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (!NiceCatchConfig.SERVER.predationEnabled.get()) return false;
        FishProfiles.FishProfile profile = FishProfiles.of(fish);
        if (!profile.predator) return false;
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.hooked || FishBehavior.isScattering(fish)) return false;
        if (state.bobber != null || state.biteBobber != null) return false;
        if ((fish.tickCount + fish.getId()) % 40 != 0) return false;
        if (fish.level().getGameTime() < state.huntSatiatedUntil) return false;

        prey = findPrey(profile);
        if (prey == null) return false;
        chaseTicks = 0;
        return true;
    }

    @Nullable
    private PathfinderMob findPrey(FishProfiles.FishProfile profile)
    {
        double radius = NiceCatchConfig.SERVER.predatorHuntRadius.get();
        double myArea = fish.getBbWidth() * fish.getBbHeight();
        AABB box = fish.getBoundingBox().inflate(radius);
        PathfinderMob best = null;
        double bestDist = Double.MAX_VALUE;
        for (PathfinderMob other : fish.level().getEntitiesOfClass(PathfinderMob.class, box,
                o -> o != fish && o.isAlive() && o.isInWater()
                        && o.getType() != fish.getType()
                        && FishBehavior.isFishLike(o) && !FishBehavior.isHooked(o))) {
            double area = other.getBbWidth() * other.getBbHeight();
            if (myArea / Math.max(1.0E-4D, area) < profile.preyRatio) continue;
            double d = fish.distanceToSqr(other);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse()
    {
        if (prey == null || !prey.isAlive() || !prey.isInWater()) return false;
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.hooked || FishBehavior.isScattering(fish)) return false;
        if (FishBehavior.isHooked(prey)) return false; // a player got it first; theirs now
        if (chaseTicks > CHASE_TIMEOUT_TICKS) return false;
        double leash = NiceCatchConfig.SERVER.predatorHuntRadius.get() * 1.8D;
        return fish.distanceToSqr(prey) < leash * leash;
    }

    @Override
    public void stop()
    {
        prey = null;
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
        if (prey == null) return;
        chaseTicks++;

        // The prey's terror is refreshed from the predator's live position, so the ordinary
        // scatter machinery does the fleeing (jinks, wall avoidance, school panic chain).
        if (chaseTicks % 10 == 1) {
            FishBehavior.scatter(prey, fish.position(), 40, FishBehavior.LIGHT_SCARE_COOLDOWN);
        }

        // Lead the chase slightly; predators are the fastest thing in the pond.
        FishSteering.swimToward(fish, prey.position().add(prey.getDeltaMovement().scale(3.0D)),
                0.035D, 0.42D);

        double catchRange = (fish.getBbWidth() + prey.getBbWidth()) * 0.5D + 0.35D;
        if (fish.distanceToSqr(prey) < catchRange * catchRange) {
            devour((ServerLevel) fish.level(), prey);
        }
    }

    /** Gulp: the prey vanishes whole — a burst of bubbles, a snap, and a satiated predator. */
    private void devour(ServerLevel level, PathfinderMob eaten)
    {
        level.sendParticles(ParticleTypes.BUBBLE_POP, eaten.getX(), eaten.getY() + 0.2D, eaten.getZ(),
                8, 0.2D, 0.15D, 0.2D, 0.04D);
        level.sendParticles(ParticleTypes.SPLASH, eaten.getX(), eaten.getY() + 0.2D, eaten.getZ(),
                4, 0.2D, 0.1D, 0.2D, 0.0D);
        level.playSound(null, eaten.getX(), eaten.getY(), eaten.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.5F, 0.7F + level.random.nextFloat() * 0.3F);
        eaten.discard();
        prey = null;

        FishBehavior.FishState state = FishBehavior.state(fish);
        int satiation = NiceCatchConfig.SERVER.predationSatiationTicks.get();
        long now = level.getGameTime();
        state.huntSatiatedUntil = now + satiation + level.random.nextInt(Math.max(1, satiation / 2));
        state.foodSatiatedUntil = Math.max(state.foodSatiatedUntil, now + satiation / 2);
    }
}
