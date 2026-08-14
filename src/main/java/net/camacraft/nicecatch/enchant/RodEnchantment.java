package net.camacraft.nicecatch.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.common.ToolActions;

/**
 * The family of fishing-rod-only enchantments. One category covers vanilla rods and any
 * modded rod that declares the fishing-rod-cast tool action (Aquaculture's rods do), so
 * the whole set lands on the enchanting table and anvil for every rod in the pack.
 */
public class RodEnchantment extends Enchantment
{
    public static final EnchantmentCategory ROD = EnchantmentCategory.create("NICECATCH_ROD",
            item -> item instanceof FishingRodItem
                    || item.getDefaultInstance().canPerformAction(ToolActions.FISHING_ROD_CAST));

    private final int maxLevel;

    public RodEnchantment(Rarity rarity, int maxLevel)
    {
        super(rarity, ROD, new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND });
        this.maxLevel = maxLevel;
    }

    @Override
    public int getMaxLevel()
    {
        return maxLevel;
    }

    @Override
    public int getMinCost(int level)
    {
        return 10 + (level - 1) * 9;
    }

    @Override
    public int getMaxCost(int level)
    {
        return getMinCost(level) + 25;
    }
}
