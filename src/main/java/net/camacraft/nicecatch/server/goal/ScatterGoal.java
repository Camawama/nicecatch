package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Panicked burst away from a threat: fast, zig-zagging, and steered directly (no pathfinding)
 * so it actually looks like fleeing. Entered either because the manager flagged this fish
 * (hooked neighbor, melee swing, damage, failed bite) or because a threat got too close —
 * swimmers always spook fish; a looming player rolls the scare chance.
 *
 * The fish commits to a chosen escape heading for a stretch instead of re-deciding every
 * tick, and when cornered it re-plans with a compass sweep that will gladly route it right
 * past the threat rather than let it tremble against a wall.
 */
public class ScatterGoal extends Goal
{
    private final AbstractFish fish;
    private Vec3 fleeDir = Vec3.ZERO;
    private int commitTicks;
    private int jinkTicks;
    private int slowTicks;

    public ScatterGoal(AbstractFish fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (FishBehavior.isHooked(fish)) return false;
        if (FishBehavior.isScattering(fish)) return true;

        // Cheap staggered threat check, ~4x a second per fish.
        if ((fish.tickCount + fish.getId()) % 5 != 0) return false;
        FishBehavior.Threat threat = FishBehavior.findThreat(fish);
        if (threat == null) return false;
        if (!threat.certain()
                && fish.getRandom().nextFloat() >= NiceCatchConfig.SERVER.swimScareChance.get().floatValue()) {
            return false;
        }

        FishBehavior.scatter(fish, threat.pos(), NiceCatchConfig.SERVER.scatterDurationTicks.get());
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
        fish.getNavigation().stop();
        commitTicks = 0;
        slowTicks = 0;
        jinkTicks = 4 + fish.getRandom().nextInt(5);
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        Vec3 from = FishBehavior.state(fish).scatterFrom;
        if (from == null) from = fish.position();

        // Barely moving for a while = pressed against something; re-plan immediately.
        if (fish.getDeltaMovement().horizontalDistanceSqr() < 0.01D) {
            if (++slowTicks > 5) commitTicks = 0;
        } else {
            slowTicks = 0;
        }

        if (--commitTicks <= 0) {
            commitTicks = 12 + fish.getRandom().nextInt(8);
            fleeDir = FishSteering.chooseEscapeDir(fish, from);
        }

        double scatterSpeed = NiceCatchConfig.SERVER.scatterSpeed.get();
        Vec3 target = fish.position().add(fleeDir.scale(5.0D));
        FishSteering.swimToward(fish, target, 0.024D * scatterSpeed, 0.22D * scatterSpeed);

        if (--jinkTicks <= 0) {
            jinkTicks = 4 + fish.getRandom().nextInt(6);
            FishSteering.jink(fish, 0.08D);
        }
    }
}
