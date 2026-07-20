package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.EnumSet;

/**
 * Curiosity: fish drift over to a bobber and mill around beneath it. Interest spreads —
 * a fish that sees another fish heading for a bobber may tag along even from outside the
 * bobber's own radius. Claims are capped per bobber so schools split between players.
 */
public class FollowBobberGoal extends Goal
{
    private final AbstractFish fish;
    private int repathTicks;

    public FollowBobberGoal(AbstractFish fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE));
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
        repathTicks = 0;
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
            // Nibbling: press right up under the bobber.
            if (--repathTicks <= 0 || fish.getNavigation().isDone()) {
                repathTicks = 5;
                fish.getNavigation().moveTo(hook.getX(), hook.getY() - 0.3D, hook.getZ(), 1.3D);
            }
            return;
        }

        // Milling: wander lazy circles a short distance from the bobber.
        if (--repathTicks <= 0 || fish.getNavigation().isDone()) {
            RandomSource random = fish.getRandom();
            repathTicks = 30 + random.nextInt(30);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = 0.8D + random.nextDouble() * 1.6D;
            double x = hook.getX() + Math.cos(angle) * dist;
            double y = hook.getY() - (0.4D + random.nextDouble() * 1.0D);
            double z = hook.getZ() + Math.sin(angle) * dist;
            fish.getNavigation().moveTo(x, y, z, 1.0D);
        }
    }
}
