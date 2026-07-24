package net.camacraft.nicecatch.entity;

import net.camacraft.nicecatch.registry.ModEntities;
import net.camacraft.nicecatch.registry.ModItems;
import net.camacraft.nicecatch.server.FishBehavior;
import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * An arrow with a fishing line attached. Fired from a bow like any arrow, but when it strikes a
 * fish it doesn't wound it — the line takes hold and the reel fight begins for that fish (see
 * {@link ServerFishingManager#tryStartArrowFight}). Against anything else it behaves like a
 * normal arrow. A fish landed this way is deliberately kept out of catch-and-release.
 */
public class LineArrowEntity extends AbstractArrow
{
    public LineArrowEntity(EntityType<? extends LineArrowEntity> type, Level level)
    {
        super(type, level);
    }

    public LineArrowEntity(Level level, LivingEntity shooter)
    {
        super(ModEntities.LINE_ARROW.get(), shooter, level);
    }

    @Override
    protected ItemStack getPickupItem()
    {
        return new ItemStack(ModItems.LINE_ARROW.get());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket()
    {
        // Forge's spawn packet reliably reconstructs a modded projectile (with its owner) client-side.
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        Entity target = result.getEntity();
        if (!level().isClientSide && target instanceof PathfinderMob mob
                && FishBehavior.isFishLike(mob)
                && getOwner() instanceof ServerPlayer shooter
                && ServerFishingManager.tryStartArrowFight(shooter, mob)) {
            // The line took hold: the fight tracks the fish from here, so the arrow is spent.
            discard();
            return;
        }
        super.onHitEntity(result);
    }
}
