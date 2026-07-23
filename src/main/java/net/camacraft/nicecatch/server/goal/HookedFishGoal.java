package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.PathfinderMob;

import java.util.EnumSet;

/**
 * While hooked, the fish's own AI stands down: it holds the movement flags so nothing else
 * paths it around, and the fight in ServerFishingManager drives its velocity directly.
 */
public class HookedFishGoal extends Goal
{
    private final PathfinderMob fish;

    public HookedFishGoal(PathfinderMob fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse()
    {
        return FishBehavior.isHooked(fish);
    }

    @Override
    public boolean canContinueToUse()
    {
        return FishBehavior.isHooked(fish);
    }

    @Override
    public void start()
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
        if (!fish.getNavigation().isDone()) {
            fish.getNavigation().stop();
        }
    }
}
