package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Curiosity: fish dart over to a bobber and orbit beneath it, inside bite range. Interest
 * spreads — a fish that sees another fish heading for a bobber may tag along even from
 * outside the bobber's own radius. Claims are capped per bobber so schools split between
 * players. Movement is direct velocity steering; no pathfinding involved.
 */
public class FollowBobberGoal extends Goal
{
    private final AbstractFish fish;
    private double orbitAngle;
    private double orbitDir = 1.0D;
    private double orbitRadius = 1.0D;
    private double orbitDepth = 0.6D;
    private int behaviorTicks;
    private int pauseTicks;

    public FollowBobberGoal(AbstractFish fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (!NiceCatchConfig.SERVER.entityFishingEnabled.get()) return false;
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.hooked || FishBehavior.isScattering(fish)) return false;
        if (state.biteBobber != null) {
            if (state.biteBobber.isAlive()) return true;
            state.biteBobber = null;
        }
        if (FishBehavior.isAttracting(state.bobber)) return true;
        state.bobber = null;

        // Staggered decision-making: roughly twice a second per fish.
        if ((fish.tickCount + fish.getId()) % 10 != 0) return false;
        RandomSource random = fish.getRandom();

        FishingHook hook = FishBehavior.findNearbyBobber(fish);
        if (hook != null && random.nextFloat() < NiceCatchConfig.SERVER.interestChance.get().floatValue()) {
            state.bobber = hook;
            return true;
        }

        // Follow a neighbor that is already interested, even without seeing the bobber ourselves.
        FishingHook followed = FishBehavior.findFollowableBobber(fish);
        if (followed != null && random.nextFloat() < NiceCatchConfig.SERVER.followFollowerChance.get().floatValue()) {
            state.bobber = followed;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse()
    {
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.hooked || FishBehavior.isScattering(fish)) return false;
        if (state.biteBobber != null) {
            if (state.biteBobber.isAlive()) return true;
            state.biteBobber = null;
        }
        FishingHook hook = state.bobber;
        if (!FishBehavior.isAttracting(hook)) {
            state.bobber = null;
            return false;
        }
        double leash = FishBehavior.attractRadius(hook) * 1.5D;
        if (fish.distanceToSqr(hook) > leash * leash) {
            state.bobber = null;
            return false;
        }
        return true;
    }

    @Override
    public void start()
    {
        fish.getNavigation().stop();
        RandomSource random = fish.getRandom();
        orbitAngle = random.nextDouble() * Math.PI * 2.0D;
        orbitDir = random.nextBoolean() ? 1.0D : -1.0D;
        rerollOrbit(random);
        behaviorTicks = 0;
        pauseTicks = 0;
    }

    @Override
    public void stop()
    {
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (!state.hooked && state.biteBobber == null) {
            state.bobber = null;
        }
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
        FishBehavior.FishState state = FishBehavior.state(fish);
        FishingHook hook = state.biteBobber != null ? state.biteBobber : state.bobber;
        if (hook == null) return;

        if (state.biteBobber != null) {
            // Nibbling: press right up under the bobber, wiggling.
            Vec3 target = new Vec3(hook.getX(), hook.getY() - 0.25D, hook.getZ());
            FishSteering.swimToward(fish, target, 0.035D, 0.3D);
            if (fish.tickCount % 4 == 0) {
                FishSteering.jink(fish, 0.04D);
            }
            return;
        }

        double distSq = fish.distanceToSqr(hook);
        if (distSq > 6.25D) { // > 2.5 blocks: dart over with purpose
            FishSteering.swimToward(fish, new Vec3(hook.getX(), hook.getY() - 0.6D, hook.getZ()), 0.03D, 0.33D);
            return;
        }

        // Close in: orbit lazily beneath the bobber, with hovers and the odd reroll,
        // staying inside bite range so nibbles actually happen.
        if (pauseTicks > 0) {
            pauseTicks--;
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.85D));
            FishSteering.faceMovement(fish);
            return;
        }
        if (++behaviorTicks % 60 == 0) {
            RandomSource random = fish.getRandom();
            rerollOrbit(random);
            if (random.nextFloat() < 0.4F) {
                pauseTicks = 10 + random.nextInt(20);
                return;
            }
        }

        orbitAngle += orbitDir * 0.09D;
        Vec3 target = new Vec3(
                hook.getX() + Math.cos(orbitAngle) * orbitRadius,
                hook.getY() - orbitDepth,
                hook.getZ() + Math.sin(orbitAngle) * orbitRadius);
        FishSteering.swimToward(fish, target, 0.022D, 0.2D);
    }

    private void rerollOrbit(RandomSource random)
    {
        orbitRadius = 0.6D + random.nextDouble() * 0.9D;
        orbitDepth = 0.35D + random.nextDouble() * 0.7D;
        if (random.nextFloat() < 0.3F) orbitDir = -orbitDir;
    }
}
