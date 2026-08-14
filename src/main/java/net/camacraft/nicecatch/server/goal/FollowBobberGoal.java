package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.PathfinderMob;
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
    /** Ticks a committed fish keeps faith with a bobber that momentarily stops attracting. */
    private static final int ATTENTION_GRACE_TICKS = 30;

    private final PathfinderMob fish;
    private double orbitAngle;
    private double orbitDir = 1.0D;
    private double orbitRadius = 1.0D;
    private double orbitDepth = 0.6D;
    private int behaviorTicks;
    private int pauseTicks;
    private int loseTicks;

    public FollowBobberGoal(PathfinderMob fish)
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
        if (hook != null) {
            // Seeing a bobber builds curiosity every check (faster with Aquaculture bait, and
            // faster for curious species); the more interested the fish, the sooner it commits.
            // Bold species need less convincing; cold water makes everything sluggish.
            var profile = net.camacraft.nicecatch.server.FishProfiles.of(fish);
            float cold = FishBehavior.coldFactor(fish.level(), hook.blockPosition());
            state.interest = Math.min(1.0F, state.interest
                    + NiceCatchConfig.SERVER.interestGainPerCheck.get().floatValue()
                    * FishBehavior.interestGainMultiplier(hook) * profile.curiosity * cold);
            float approach = NiceCatchConfig.SERVER.interestChance.get().floatValue()
                    * (0.25F + 0.75F * state.interest * 2.0F)
                    * (0.5F + profile.boldness) * cold;
            if (random.nextFloat() < approach) {
                state.bobber = hook;
                return true;
            }
        } else {
            state.interest = Math.max(0.0F, state.interest - 0.01F);
        }

        // Follow a neighbor that is already interested, even without seeing the bobber ourselves.
        FishingHook followed = FishBehavior.findFollowableBobber(fish);
        if (followed != null && random.nextFloat() < NiceCatchConfig.SERVER.followFollowerChance.get().floatValue()) {
            state.bobber = followed;
            state.interest = Math.max(state.interest, 0.2F);
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
        if (hook == null) return false;
        if (!FishBehavior.isAttracting(hook)) {
            // Attention holds through a brief wobble (owner busy for a moment, odd hook
            // state blip) — a committed fish must never snap away to its school and back.
            if (hook.isAlive() && ++loseTicks <= ATTENTION_GRACE_TICKS) return true;
            state.bobber = null;
            return false;
        }
        loseTicks = 0;
        // Horizontal leash only — a fish climbing up from the depths is still on its way.
        double leash = FishBehavior.attractRadius(hook) * 1.5D;
        if (FishBehavior.horizontalDistSqr(fish, hook) > leash * leash) {
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
        loseTicks = 0;
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

        // The vanilla V-wake, honestly attached: it rides the surface directly above THIS
        // fish and trails its real motion — so every wake near a bobber is a genuine fish
        // that is interested, closing in, or nibbling. (The old invisible-loot-fish wake is
        // suppressed whenever real fish are around.)
        if ((fish.tickCount + fish.getId()) % 3 == 0) {
            emitSurfaceWake();
        }

        // A bobber can drift — water current, a boat towing the line, a player reeling it in.
        // Aim where it's heading and put on enough extra speed to keep pace, or a moving bobber
        // is impossible for the fish to catch up to and it never gets close enough to bite.
        Vec3 hookVel = hook.getDeltaMovement();
        double hookSpeed = hookVel.horizontalDistance();
        double lead = 3.0D;
        double chaseBoost = hookSpeed * 1.6D;

        // Every depth below is measured for the fish's BODY, not its feet origin: aiming a
        // trophy's origin just under the bobber used to shove its whole back into the air,
        // where the surface clamp slapped it down, the goal pushed it back up, and an entire
        // school bobbed against the surface in eerie robotic unison. The fish's TOP stays
        // under the bobber; only a nibble presses the mouth up to it.
        double body = fish.getBbHeight();

        if (state.biteBobber != null) {
            // Nibbling: press the MOUTH right up under the bobber, wiggling, tracking drift.
            Vec3 target = new Vec3(hook.getX() + hookVel.x * lead,
                    hook.getY() - 0.1D - body, hook.getZ() + hookVel.z * lead);
            FishSteering.swimToward(fish, target, 0.035D + hookSpeed * 0.05D, 0.3D + chaseBoost);
            if (fish.tickCount % 4 == 0) {
                FishSteering.jink(fish, 0.04D);
            }
            return;
        }

        double closeSq = Math.pow(2.5D + body * 0.5D, 2.0D);
        double distSq = fish.distanceToSqr(hook);
        if (distSq > closeSq) { // beyond loitering range: dart over with purpose, leading the drift
            Vec3 target = new Vec3(hook.getX() + hookVel.x * lead,
                    hook.getY() - 0.4D - body, hook.getZ() + hookVel.z * lead);
            FishSteering.swimToward(fish, target, 0.03D + hookSpeed * 0.05D, 0.33D + chaseBoost);
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
        // Hanging around the bait keeps curiosity simmering upward.
        if (++behaviorTicks % 20 == 0) {
            state.interest = Math.min(1.0F, state.interest
                    + 0.5F * NiceCatchConfig.SERVER.interestGainPerCheck.get().floatValue()
                    * FishBehavior.interestGainMultiplier(hook));
        }
        if (behaviorTicks % 60 == 0) {
            RandomSource random = fish.getRandom();
            rerollOrbit(random);
            if (random.nextFloat() < 0.4F) {
                pauseTicks = 10 + random.nextInt(20);
                return;
            }
        }

        // Slightly different orbital speeds per fish, so schoolmates circling one bobber
        // drift out of phase instead of wheeling in lockstep.
        orbitAngle += orbitDir * (0.07D + ((fish.getId() & 7) * 0.008D));
        Vec3 target = new Vec3(
                hook.getX() + hookVel.x * lead + Math.cos(orbitAngle) * orbitRadius,
                hook.getY() - orbitDepth - fish.getBbHeight(),
                hook.getZ() + hookVel.z * lead + Math.sin(orbitAngle) * orbitRadius);
        FishSteering.swimToward(fish, target, 0.022D, 0.2D + chaseBoost);
    }

    private void rerollOrbit(RandomSource random)
    {
        orbitRadius = 0.6D + random.nextDouble() * 0.9D;
        orbitDepth = 0.35D + random.nextDouble() * 0.7D;
        if (random.nextFloat() < 0.3F) orbitDir = -orbitDir;
    }

    /**
     * The surface wake above this fish, angled along its real swimming direction. Only a
     * moving fish near the surface (within ~4 blocks of it) leaves one — a deep or hovering
     * fish shows nothing, exactly like watching real water.
     */
    private void emitSurfaceWake()
    {
        Vec3 v = fish.getDeltaMovement();
        double speed = v.horizontalDistance();
        if (speed < 0.03D || !fish.isInWater()) return;
        var level = (net.minecraft.server.level.ServerLevel) fish.level();
        net.minecraft.core.BlockPos pos = fish.blockPosition();
        if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) return;
        int climbed = 0;
        while (climbed < 4 && level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER)) {
            pos = pos.above();
            climbed++;
        }
        if (level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER)) return; // too deep to read
        double surfaceY = pos.getY() + level.getFluidState(pos).getHeight(level, pos);
        double fx = v.x / speed * 0.04D;
        double fz = v.z / speed * 0.04D;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FISHING,
                fish.getX(), surfaceY, fish.getZ(), 0, fz, 0.01D, -fx, 1.0D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FISHING,
                fish.getX(), surfaceY, fish.getZ(), 0, -fz, 0.01D, fx, 1.0D);
    }
}
