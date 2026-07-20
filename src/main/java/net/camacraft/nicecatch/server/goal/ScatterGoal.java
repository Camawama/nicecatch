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
 * (hooked neighbor, melee swing, damage, failed bite) or because a threat got too close.
 */
public class ScatterGoal extends Goal
{
    private final AbstractFish fish;
    private int jinkTicks;

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
        Vec3 threat = FishBehavior.findThreat(fish);
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
        fish.getNavigation().stop();
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

        // Water friction settles sustained acceleration at ~9x, so 0.024*1.9 ≈ 0.045 accel
        // tops out just above 0.4 blocks/tick — a genuine sprint without looking teleported.
        double accel = 0.024D * NiceCatchConfig.SERVER.scatterSpeed.get();
        FishSteering.swimAway(fish, from, accel, 0.22D * NiceCatchConfig.SERVER.scatterSpeed.get());

        if (--jinkTicks <= 0) {
            jinkTicks = 4 + fish.getRandom().nextInt(6);
            FishSteering.jink(fish, 0.12D);
        }
    }
}
