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
 *
 * Species additionally flagged {@code aggressive:true} (sharks, pike, thrashers) go after
 * ANY living creature swimming in their water that is small enough to be prey by the same
 * hitbox-ratio rule — a rabbit paddling over a big pike is having a bad day. Non-fish prey
 * takes a real bite of damage rather than being swallowed whole, and players are exempt
 * unless the config says otherwise.
 */
public class HuntPreyGoal extends Goal
{
    /** A chase the predator hasn't won by now is abandoned (prey wins on stamina). */
    private static final int CHASE_TIMEOUT_TICKS = 160;

    private final PathfinderMob fish;
    @Nullable private net.minecraft.world.entity.LivingEntity prey;
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
    private net.minecraft.world.entity.LivingEntity findPrey(FishProfiles.FishProfile profile)
    {
        double radius = NiceCatchConfig.SERVER.predatorHuntRadius.get();
        double myArea = fish.getBbWidth() * fish.getBbHeight();
        AABB box = fish.getBoundingBox().inflate(radius);
        net.minecraft.world.entity.LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (var other : fish.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                o -> o != fish && o.isAlive() && o.isInWater() && isValidPrey(profile, o))) {
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

    /** Fish prey follows the classic rules; anything else needs the aggressive flag. */
    private boolean isValidPrey(FishProfiles.FishProfile profile, net.minecraft.world.entity.LivingEntity o)
    {
        if (o instanceof PathfinderMob mob && FishBehavior.isFishKind(mob)) {
            return o.getType() != fish.getType() && FishBehavior.isFishLike(mob)
                    && !FishBehavior.isHooked(mob);
        }
        if (!profile.aggressive) return false;
        if (o instanceof net.minecraft.world.entity.decoration.ArmorStand) return false;
        // Somebody's pet paddling behind its owner is off the menu.
        if (o instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isTame()) return false;
        if (o instanceof net.minecraft.world.entity.player.Player player) {
            return NiceCatchConfig.SERVER.aggressiveAttackPlayers.get()
                    && !player.isCreative() && !player.isSpectator();
        }
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        if (prey == null || !prey.isAlive() || !prey.isInWater()) return false;
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.hooked || FishBehavior.isScattering(fish)) return false;
        if (prey instanceof PathfinderMob mob && FishBehavior.isHooked(mob)) return false; // a player got it first
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
        // Only fish speak that language; a paddling rabbit panics on its own once bitten.
        if (chaseTicks % 10 == 1 && prey instanceof PathfinderMob mob && FishBehavior.isFishLike(mob)) {
            FishBehavior.scatter(mob, fish.position(), 40, FishBehavior.LIGHT_SCARE_COOLDOWN);
        }

        // Lead the chase slightly; predators are the fastest thing in the pond.
        FishSteering.swimToward(fish, prey.position().add(prey.getDeltaMovement().scale(3.0D)),
                0.035D, 0.42D);

        double catchRange = (fish.getBbWidth() + prey.getBbWidth()) * 0.5D + 0.35D;
        if (fish.distanceToSqr(prey) < catchRange * catchRange) {
            if (prey instanceof PathfinderMob mob && FishBehavior.isFishLike(mob)) {
                devour((ServerLevel) fish.level(), mob);
            } else {
                bite((ServerLevel) fish.level(), prey);
            }
        }
    }

    /**
     * A non-fish victim is BITTEN, not swallowed: real damage scaled by the attacker's bulk,
     * a thrash of water, and the predator considers its point made for a while. Ordinary
     * loot rules apply if the bite kills — no free item printer, just a mean pond.
     */
    private void bite(ServerLevel level, net.minecraft.world.entity.LivingEntity victim)
    {
        float damage = (float) net.minecraft.util.Mth.clamp(
                2.0D + fish.getBbWidth() * fish.getBbHeight() * 3.0D, 2.0D, 12.0D);
        victim.hurt(level.damageSources().mobAttack(fish), damage);
        level.sendParticles(ParticleTypes.SPLASH, victim.getX(), victim.getY() + 0.2D, victim.getZ(),
                8, 0.25D, 0.15D, 0.25D, 0.0D);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.7F, 0.55F);
        prey = null;

        FishBehavior.FishState state = FishBehavior.state(fish);
        int satiation = NiceCatchConfig.SERVER.predationSatiationTicks.get();
        state.huntSatiatedUntil = level.getGameTime() + satiation / 2;
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
