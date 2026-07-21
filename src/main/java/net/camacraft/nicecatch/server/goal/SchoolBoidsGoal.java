package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Loose boids schooling: same-species fish cruise together with cohesion, alignment and
 * separation, steered directly like everything else (vanilla's FollowFlockLeaderGoal is a
 * pathfinding conga line and gets removed when this is enabled). Right after a scatter ends
 * the school actively regroups — wider search, harder pull toward the others — so a spooked
 * shoal flash-expands away from the threat and then knits itself back together, instead of
 * ending up as loners drifting in corners.
 *
 * The goal duty-cycles: a school swims as one for a while, then breaks up briefly so fish
 * still potter about on their own and don't read as a rigid formation.
 */
public class SchoolBoidsGoal extends Goal
{
    /** Ticks after a scatter ends during which the school urgently regroups. */
    private static final int REGROUP_WINDOW = 160;

    private final AbstractFish fish;
    private List<AbstractFish> neighbors = List.of();
    private int refreshTicks;
    private int runTicksLeft;
    private int restUntilTick;
    private double wanderAngle;

    public SchoolBoidsGoal(AbstractFish fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (!NiceCatchConfig.SERVER.entityFishingEnabled.get()
                || !NiceCatchConfig.SERVER.boidSchoolingEnabled.get()) return false;
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.hooked || FishBehavior.isScattering(fish)) return false;
        if (state.bobber != null || state.biteBobber != null) return false;

        boolean regroup = regrouping();
        if (!regroup && fish.tickCount < restUntilTick) return false;
        if (!regroup && (fish.tickCount + fish.getId()) % 10 != 0) return false;

        neighbors = findNeighbors(regroup);
        return neighbors.size() >= (regroup ? 1 : 2);
    }

    @Override
    public boolean canContinueToUse()
    {
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.hooked || FishBehavior.isScattering(fish)) return false;
        if (state.bobber != null || state.biteBobber != null) return false;
        if (runTicksLeft <= 0 && !regrouping()) return false;
        return !neighbors.isEmpty();
    }

    @Override
    public void start()
    {
        fish.getNavigation().stop();
        refreshTicks = 0;
        runTicksLeft = 300 + fish.getRandom().nextInt(300);
        wanderAngle = fish.getRandom().nextDouble() * Math.PI * 2.0D;
    }

    @Override
    public void stop()
    {
        neighbors = List.of();
        // Break time: mill around solo before falling back into formation.
        restUntilTick = fish.tickCount + 80 + fish.getRandom().nextInt(120);
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        boolean regroup = regrouping();
        runTicksLeft--;
        if (--refreshTicks <= 0) {
            refreshTicks = 10;
            neighbors = findNeighbors(regroup);
        }
        if (neighbors.isEmpty()) return;

        Vec3 centroid = Vec3.ZERO;
        Vec3 avgVel = Vec3.ZERO;
        Vec3 separation = Vec3.ZERO;
        for (AbstractFish other : neighbors) {
            centroid = centroid.add(other.position());
            avgVel = avgVel.add(other.getDeltaMovement());
            Vec3 away = fish.position().subtract(other.position());
            double d = away.length();
            if (d > 1.0E-4D && d < 1.2D) {
                separation = separation.add(away.scale(1.0D / (d * d) * 0.5D));
            }
        }
        centroid = centroid.scale(1.0D / neighbors.size());
        avgVel = avgVel.scale(1.0D / neighbors.size());

        // Each fish carries its own slowly-drifting whim; the school's shared heading emerges
        // from alignment averaging the whims out, and the whole shoal meanders organically.
        wanderAngle += (fish.getRandom().nextDouble() - 0.5D) * 0.35D;

        Vec3 toCentroid = centroid.subtract(fish.position());
        double distC = toCentroid.length();
        double cohesionW = regroup ? 1.0D : 0.4D * Math.min(1.0D, distC / 3.0D);
        Vec3 steer = Vec3.ZERO;
        if (distC > 1.0E-4D) {
            steer = steer.add(toCentroid.scale(cohesionW / distC));
        }
        if (avgVel.lengthSqr() > 1.0E-6D) {
            steer = steer.add(avgVel.normalize().scale(0.5D));
        }
        steer = steer.add(separation);
        steer = steer.add(new Vec3(Math.cos(wanderAngle), 0.0D, Math.sin(wanderAngle)).scale(regroup ? 0.1D : 0.3D));
        if (steer.lengthSqr() < 1.0E-6D) return;

        double speed = NiceCatchConfig.SERVER.boidSpeed.get();
        double accel = (regroup ? 0.022D : 0.012D) * speed;
        double maxSpeed = (regroup ? 0.3D : 0.14D) * speed;
        FishSteering.swimToward(fish, fish.position().add(steer.normalize().scale(4.0D)), accel, maxSpeed);
    }

    /** Just came off a scatter: the school pulls back together with real urgency. */
    private boolean regrouping()
    {
        long sinceScatter = fish.level().getGameTime() - FishBehavior.state(fish).scatterUntil;
        return sinceScatter >= 0 && sinceScatter < REGROUP_WINDOW;
    }

    private List<AbstractFish> findNeighbors(boolean regroup)
    {
        double radius = NiceCatchConfig.SERVER.boidNeighborRadius.get() + (regroup ? 3.0D : 0.0D);
        AABB box = fish.getBoundingBox().inflate(radius);
        List<AbstractFish> found = fish.level().getEntitiesOfClass(AbstractFish.class, box,
                f -> f != fish && f.getType() == fish.getType() && f.isAlive()
                        && !FishBehavior.isHooked(f) && !FishBehavior.isScattering(f));
        // A handful of schoolmates is plenty for the math; cap it so megaschools stay cheap.
        return found.size() > 8 ? found.subList(0, 8) : found;
    }
}
