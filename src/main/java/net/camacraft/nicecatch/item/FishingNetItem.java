package net.camacraft.nicecatch.item;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.camacraft.nicecatch.server.FishConversion;
import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A hand net for close-range fishing: sweep it through nearby water and any fish inside its
 * small radius may be scooped up on the spot. It shines in the shallows — big fish usually
 * thrash free and a missed swipe scatters the school, so it never replaces the rod; it
 * complements it. Sweeping up a fish that another player has on their line lands the catch
 * for them immediately (net-and-rod teamwork).
 */
public class FishingNetItem extends Item
{
    public FishingNetItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 target = findSweepTarget(level, player);
        if (target == null) return InteractionResultHolder.pass(stack);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            sweep((ServerLevel) level, serverPlayer, stack, target, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Clicking a fish directly sweeps the net at it (same rules as sweeping the water). */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand)
    {
        if (!(target instanceof PathfinderMob mob) || !FishBehavior.isFishLike(mob)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            sweep(serverPlayer.serverLevel(), serverPlayer, stack, mob.position(), hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Where the sweep lands: the water surface the player is aiming at, or just ahead when swimming. */
    private static Vec3 findSweepTarget(Level level, Player player)
    {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            return Vec3.atCenterOf(hit.getBlockPos());
        }
        if (player.isInWater()) {
            return player.position().add(player.getLookAngle().scale(1.5D)).add(0.0D, 0.2D, 0.0D);
        }
        return null;
    }

    private void sweep(ServerLevel level, ServerPlayer player, ItemStack stack, Vec3 center, InteractionHand hand)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        double radius = cfg.netCatchRadius.get();

        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.7F, 1.1F + level.random.nextFloat() * 0.3F);
        level.sendParticles(ParticleTypes.SPLASH, center.x, center.y, center.z, 12, radius * 0.4D, 0.15D, radius * 0.4D, 0.0D);

        player.getCooldowns().addCooldown(this, cfg.netCooldownTicks.get());
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));

        AABB box = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        List<PathfinderMob> inRange = level.getEntitiesOfClass(PathfinderMob.class, box,
                f -> f.isAlive() && FishBehavior.isFishLike(f) && f.distanceToSqr(center) <= radius * radius);
        if (inRange.isEmpty()) return;

        // Teamwork first: a fish someone is fighting on a line is landed for them outright.
        for (PathfinderMob fish : inRange) {
            if (FishBehavior.isHooked(fish) && ServerFishingManager.netLandHookedFish(player, fish)) {
                return;
            }
        }

        // Otherwise one free fish per sweep, nearest first. Bulk squirms out of a hand net —
        // the chance shrinks for big fish — and a fleeing fish is a hard target.
        PathfinderMob best = null;
        double bestDist = Double.MAX_VALUE;
        for (PathfinderMob fish : inRange) {
            if (FishBehavior.isHooked(fish)) continue;
            double d = fish.distanceToSqr(center);
            if (d < bestDist) {
                bestDist = d;
                best = fish;
            }
        }
        if (best == null) return;

        // Weight decides the odds — a hand net is a smallfry tool. Under ~2 lbs it's easy
        // money; every band past that collapses toward "it burst straight out of the mesh",
        // so trophy fish essentially cannot be netted free-swimming (hook one first and net
        // it cooperatively — that path stays open).
        float lbs = net.camacraft.nicecatch.server.FishSizing.weightLbs(best);
        double weightFactor = lbs < 2.0F ? 1.0D
                : lbs < 5.0F ? 0.6D
                : lbs < 10.0F ? 0.25D
                : lbs < 15.0F ? 0.08D : 0.03D;

        // Fieldcraft matters: a fish that can see the sweep coming (approached head-on)
        // mostly evades; come in from behind — sneaking keeps you unnoticed on the way —
        // or catch it glued to someone's bobber, and the odds hold up. A fleeing fish is
        // all but impossible to bag.
        double yaw = Math.toRadians(best.yBodyRot);
        Vec3 facing = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 approach = new Vec3(best.getX() - player.getX(), 0.0D, best.getZ() - player.getZ());
        double approachFactor = approach.lengthSqr() > 1.0E-4D
                && facing.dot(approach.normalize()) > 0.2D ? 1.0D : 0.45D;
        FishBehavior.FishState state = FishBehavior.state(best);
        double distractedBonus = state.bobber != null || state.biteBobber != null ? 1.4D : 1.0D;

        double chance = cfg.netCatchChance.get() * weightFactor * approachFactor * distractedBonus
                * (FishBehavior.isScattering(best) ? 0.15D : 1.0D);
        if (level.random.nextFloat() < chance) {
            FishConversion.catchDirectly(player, best);
        } else {
            // The swipe misses: the whole spot erupts.
            FishBehavior.scatter(best, center, cfg.scatterDurationTicks.get());
            FishBehavior.scatterAround(level, center, cfg.scatterRadius.get(), 0.8F, best,
                    FishBehavior.LIGHT_SCARE_COOLDOWN);
        }
    }
}
