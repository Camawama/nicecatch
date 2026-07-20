package net.camacraft.nicecatch;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

import javax.annotation.Nullable;

public class RodUtil
{
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
