package net.camacraft.nicecatch.server.goal;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.block.FishTrapBlockEntity;
import net.camacraft.nicecatch.server.FishBehavior;
import net.camacraft.nicecatch.server.FishProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * Food thrown into the water pulls fish in — but not like a magnet. The splash of a fresh
 * item startles the timid before curiosity can win; the fish that do commit swim over,
 * circle the morsel a moment, then eat it. A fed fish's bobber interest rises (chumming a
 * spot genuinely works) and it ignores further food for a while.
 *
 * A baited fish trap counts as a standing morsel too: fish drift over to investigate it,
 * which is exactly how the trap gets its catches.
 */
public class FoodInterestGoal extends Goal
{
    /** Hard cap on one food pursuit, reachable or not; the fish gives up and moves on. */
    private static final int PURSUIT_TIMEOUT_TICKS = 600;

    private final PathfinderMob fish;
    @Nullable private ItemEntity foodItem;
    @Nullable private BlockPos trapPos;
    private int eatTicks;
    private int lifeTicks;
    private double circleAngle;
    private double circleDir = 1.0D;
    private int circleTicks;

    public FoodInterestGoal(PathfinderMob fish)
    {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * Anything a player could eat, raw fish (chum), and Aquaculture's dedicated bait items
     * (worms, minnows, leeches...) — all of it works thrown in the water AND in the trap's
     * bait slot.
     */
    public static boolean isFishFood(ItemStack stack)
    {
        return !stack.isEmpty() && (stack.getItem().isEdible() || stack.is(ItemTags.FISHES)
                || net.camacraft.nicecatch.compat.AquacultureCompat.isBaitItem(stack));
    }

    @Override
    public boolean canUse()
    {
        if (!NiceCatchConfig.SERVER.foodAttractionEnabled.get()) return false;
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.hooked || FishBehavior.isScattering(fish)) return false;
        if (state.bobber != null || state.biteBobber != null) return false;
        if ((fish.tickCount + fish.getId()) % 20 != 0) return false;

        long now = fish.level().getGameTime();
        if (now < state.foodSatiatedUntil) return false;

        FishProfiles.FishProfile profile = FishProfiles.of(fish);
        RandomSource random = fish.getRandom();

        ItemEntity item = findFood();
        if (item != null) {
            // A fresh splash is a disturbance first and a meal second: the timid bolt.
            if (item.tickCount < 40) {
                float startle = NiceCatchConfig.SERVER.foodStartleChance.get().floatValue()
                        * (1.0F - 0.7F * profile.boldness);
                if (random.nextFloat() < startle) {
                    FishBehavior.scatter(fish, item.position(),
                            NiceCatchConfig.SERVER.scatterDurationTicks.get() / 2,
                            FishBehavior.LIGHT_SCARE_COOLDOWN);
                    return false;
                }
            }
            float commit = 0.35F * profile.foodAffinity * (0.4F + 0.6F * profile.boldness);
            if (random.nextFloat() < commit) {
                foodItem = item;
                trapPos = null;
                begin(random);
                return true;
            }
            return false;
        }

        // No food afloat — maybe a baited trap is putting scent in the water.
        BlockPos trap = FishTrapBlockEntity.findBaitedTrapNear(fish.level(), fish.blockPosition(),
                NiceCatchConfig.SERVER.trapAttractRadius.get());
        if (trap != null && random.nextFloat() < 0.25F * profile.foodAffinity) {
            foodItem = null;
            trapPos = trap;
            begin(random);
            return true;
        }
        return false;
    }

    private void begin(RandomSource random)
    {
        eatTicks = 0;
        lifeTicks = 0;
        // A trap visit is a long nosing-around (that dwell time is what gets fish caught);
        // circling a floating morsel is brief.
        circleTicks = trapPos != null ? 120 + random.nextInt(120) : 20 + random.nextInt(40);
        circleAngle = random.nextDouble() * Math.PI * 2.0D;
        circleDir = random.nextBoolean() ? 1.0D : -1.0D;
    }

    @Nullable
    private ItemEntity findFood()
    {
        double radius = NiceCatchConfig.SERVER.foodAttractRadius.get();
        AABB box = fish.getBoundingBox().inflate(radius, radius * 0.6D, radius);
        List<ItemEntity> items = fish.level().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && e.isInWater() && isFishFood(e.getItem()));
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            double d = fish.distanceToSqr(item);
            if (d < bestDist) {
                bestDist = d;
                best = item;
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse()
    {
        FishBehavior.FishState state = FishBehavior.state(fish);
        if (state.hooked || FishBehavior.isScattering(fish)) return false;
        if (state.bobber != null || state.biteBobber != null) return false;
        if (lifeTicks > PURSUIT_TIMEOUT_TICKS) return false;
        double leash = NiceCatchConfig.SERVER.foodAttractRadius.get() * 1.5D;
        if (foodItem != null) {
            return foodItem.isAlive() && foodItem.isInWater()
                    && fish.distanceToSqr(foodItem) < leash * leash;
        }
        if (trapPos != null) {
            // Mill around the trap for a while, then drift off; the trap itself rolls catches.
            return circleTicks > 0
                    && FishTrapBlockEntity.isBaitedTrap(fish.level(), trapPos)
                    && trapPos.distSqr(fish.blockPosition()) < leash * leash;
        }
        return false;
    }

    @Override
    public void stop()
    {
        foodItem = null;
        trapPos = null;
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
        lifeTicks++;
        // A trap visit runs on the clock whether or not the fish ever reaches it, so an
        // unreachable trap (behind a wall, say) can't hold a fish hostage forever.
        if (trapPos != null) circleTicks--;
        Vec3 target = foodItem != null ? foodItem.position()
                : trapPos != null ? Vec3.atCenterOf(trapPos) : null;
        if (target == null) return;

        double distSq = fish.distanceToSqr(target.x, target.y, target.z);
        if (distSq > 1.2D) {
            // Swim over with a light touch of caution: circle in rather than beeline.
            circleAngle += circleDir * 0.06D;
            double standoff = Math.min(2.0D, Math.sqrt(distSq) * 0.3D);
            Vec3 aim = target.add(Math.cos(circleAngle) * standoff, -0.1D, Math.sin(circleAngle) * standoff);
            FishSteering.swimToward(fish, aim, 0.02D, 0.24D);
            return;
        }

        if (trapPos != null) {
            // At the trap: hover and nose about. The trap's own tick decides if it catches.
            circleAngle += circleDir * 0.1D;
            Vec3 aim = target.add(Math.cos(circleAngle) * 0.8D, -0.2D, Math.sin(circleAngle) * 0.8D);
            FishSteering.swimToward(fish, aim, 0.015D, 0.12D);
            return;
        }

        // Right at the morsel: nibble at it, then swallow it.
        eatTicks++;
        fish.setDeltaMovement(fish.getDeltaMovement().scale(0.8D));
        FishSteering.faceMovement(fish);
        ServerLevel level = (ServerLevel) fish.level();
        if (eatTicks % 5 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE, target.x, target.y, target.z,
                    2, 0.08D, 0.05D, 0.08D, 0.02D);
        }
        if (eatTicks >= 18 && foodItem != null) {
            eat(level, foodItem);
        }
    }

    private void eat(ServerLevel level, ItemEntity item)
    {
        FishBehavior.FishState state = FishBehavior.state(fish);
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;

        ItemStack stack = item.getItem();
        stack.shrink(1);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setItem(stack.copy());
        }
        level.playSound(null, fish.getX(), fish.getY(), fish.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.3F, 1.6F + level.random.nextFloat() * 0.4F);
        level.sendParticles(ParticleTypes.BUBBLE_POP, item.getX(), item.getY(), item.getZ(),
                4, 0.1D, 0.05D, 0.1D, 0.02D);

        // A fed fish trusts the water: chumming a spot makes the fishing better.
        state.interest = Math.min(1.0F, state.interest + cfg.foodInterestBoost.get().floatValue());
        int satiation = cfg.foodSatiationTicks.get();
        state.foodSatiatedUntil = level.getGameTime() + satiation
                + level.random.nextInt(Math.max(1, satiation / 2));
        foodItem = null;
    }
}
