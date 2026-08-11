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
     * One clipped step of a reeled bobber: never through solid blocks. A pull that would
     * enter terrain climbs straight up instead — the line hauling the bobber up and over
     * the lip, the way a dragged line actually behaves — and when even up is blocked
     * (overhangs), it stops just short of the surface. Used by both the server's retrieve
     * and the client's mirrored bobber slide, so the two never disagree about walls.
     */
    public static Vec3 clipBobberStep(Entity hook, Vec3 from, Vec3 next, double step)
    {
        var level = hook.level();
        BlockHitResult hit = level.clip(new ClipContext(from, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hook));
        if (hit.getType() == HitResult.Type.MISS) return next;

        Vec3 up = from.add(0.0D, Math.max(step, 0.15D), 0.0D);
        BlockHitResult upHit = level.clip(new ClipContext(from, up,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hook));
        if (upHit.getType() == HitResult.Type.MISS) return up;

        Vec3 dir = next.subtract(from);
        double length = dir.length();
        if (length < 1.0E-6D) return from;
        double hitFrac = hit.getLocation().subtract(from).length() / length;
        return from.add(dir.scale(Math.max(0.0D, hitFrac - 0.1D)));
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
