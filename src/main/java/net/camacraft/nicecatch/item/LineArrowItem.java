package net.camacraft.nicecatch.item;

import net.camacraft.nicecatch.entity.LineArrowEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The fishing-line arrow item. Being an {@link ArrowItem}, any vanilla (or modded) bow will
 * accept it as ammo and fire it; it just spawns a {@link LineArrowEntity} instead of a plain arrow.
 */
public class LineArrowItem extends ArrowItem
{
    public LineArrowItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter)
    {
        return new LineArrowEntity(level, shooter);
    }
}
