package net.camacraft.nicecatch;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;

import javax.annotation.Nullable;

public class RodUtil
{
    /**
     * One clipped step of a reeled bobber: never through solid blocks, and never up a wall
     * it could simply go around. When the direct pull is blocked, the line feels its way: a
     * fan of horizontal detours (wrapping around corners, up to slightly backward for tight
     * ones) and a straight climb all get probed, and the OPEN step that ends nearest the
     * angler wins — with the climb carrying a penalty of half a step, so going up only wins
     * when up genuinely helps (a ledge below the angler, or pocketed on every side). Used by
     * both the server's retrieve and the client's mirrored bobber slide, so the two never
     * disagree about walls.
     */
    public static Vec3 clipBobberStep(Entity hook, Vec3 from, Vec3 next, double step, Vec3 aim)
    {
        var level = hook.level();
        if (isOpen(level, hook, from, next)) return next;

        Vec3 pull = next.subtract(from);
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;

        Vec3 lateral = new Vec3(pull.x, 0.0D, pull.z);
        if (lateral.lengthSqr() > 1.0E-6D) {
            lateral = lateral.normalize().scale(step);
            for (double degrees : DETOUR_ANGLES) {
                Vec3 candidate = from.add(rotateY(lateral, Math.toRadians(degrees)));
                if (!isOpen(level, hook, from, candidate)) continue;
                double score = candidate.distanceTo(aim);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        Vec3 up = from.add(0.0D, Math.max(step, 0.15D), 0.0D);
        if (isOpen(level, hook, from, up)) {
            double score = up.distanceTo(aim) + step * 0.5D;
            if (score < bestScore) {
                best = up;
            }
        }
        if (best != null) return best;

        // Walled in on every side: stop just short of the surface.
        BlockHitResult hit = level.clip(new ClipContext(from, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hook));
        double length = pull.length();
        if (length < 1.0E-6D || hit.getType() == HitResult.Type.MISS) return from;
        double hitFrac = hit.getLocation().subtract(from).length() / length;
        return from.add(pull.scale(Math.max(0.0D, hitFrac - 0.1D)));
    }

    /** Detour fan, degrees off the pull direction; near-side first, tight-corner wraps last. */
    private static final double[] DETOUR_ANGLES = {35.0D, -35.0D, 70.0D, -70.0D, 105.0D, -105.0D};

    private static boolean isOpen(net.minecraft.world.level.Level level, Entity hook, Vec3 from, Vec3 to)
    {
        return level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hook)).getType() == HitResult.Type.MISS;
    }

    private static Vec3 rotateY(Vec3 v, double radians)
    {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    /** True for the vanilla rod and any modded rod that declares the fishing-cast tool action (Aquaculture etc.). */
    public static boolean isRod(ItemStack stack)
    {
        return !stack.isEmpty() && stack.canPerformAction(ToolActions.FISHING_ROD_CAST);
    }

    @Nullable
    public static InteractionHand findRodHand(Player player)
    {
        if (isRod(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isRod(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }
}
