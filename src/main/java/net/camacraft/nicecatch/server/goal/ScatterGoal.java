package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Panicked burst away from a threat. Entered either because the manager flagged this fish
 * (hooked neighbor, melee swing, failed bite) or because something big is swimming nearby.
 */
public class ScatterGoal extends Goal
{
    private final AbstractFish fish;
    private int repathTicks;

    public ScatterGoal(AbstractFish fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (FishBehavior.isHooked(fish)) return false;
        if (FishBehavior.isScattering(fish)) return true;

        // Cheap staggered check for swimmers splashing around near this fish.
        if ((fish.tickCount + fish.getId()) % 10 != 0) return false;
        Vec3 threat = FishBehavior.findSwimmerThreat(fish);
        if (threat == null) return false;
        if (fish.getRandom().nextFloat() >= NiceCatchConfig.SERVER.swimScareChance.get().floatValue()) return false;

        FishBehavior.scatter(fish, threat, NiceCatchConfig.SERVER.scatterDurationTicks.get());
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        return !FishBehavior.isHooked(fish) && FishBehavior.isScattering(fish);
    }

    @Override
    public void start()
    {
        repathTicks = 0;
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
        if (--repathTicks > 0 && !fish.getNavigation().isDone()) return;
        repathTicks = 10;

        Vec3 from = FishBehavior.state(fish).scatterFrom;
        if (from == null) from = fish.position();
        Vec3 away = DefaultRandomPos.getPosAway(fish, 12, 4, from);
        if (away != null) {
            fish.getNavigation().moveTo(away.x, away.y, away.z, NiceCatchConfig.SERVER.scatterSpeed.get());
        }
    }
}
