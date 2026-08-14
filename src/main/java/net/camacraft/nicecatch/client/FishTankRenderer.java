package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.camacraft.nicecatch.block.FishTankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Renders the tank's occupant actually SWIMMING: the fish cruises a slow, wandering closed
 * path through the whole connected aquarium (the block entity's region scan), facing along
 * its motion, upright and finning at real-time animation rate.
 *
 * The two rendering traps this dodges: display dummies never tick, so their animation clock
 * is frozen — the clock is driven from world game time instead (real seconds, not frame
 * rate) — and fish renderers lay a "dry" fish on its side in a fast flop, so the dummy is
 * marked as touching water for exactly the duration of the render (and restored, because
 * the in-hand carry shares these dummies and its poses were calibrated around the flop).
 */
public class FishTankRenderer implements BlockEntityRenderer<FishTankBlockEntity>
{
    public FishTankRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FishTankBlockEntity tank, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay)
    {
        ItemStack stack = tank.getFish();
        if (stack.isEmpty() || tank.getLevel() == null) return;
        Entity display = resolveDisplay(stack);
        if (display == null) return;

        float trueScale = stack.getTagElement("NiceCatch") != null
                ? FishCarryRenderer.displayScale(display, stack) : 1.0F;

        AABB region = tank.tankRegion();
        double minDim = Math.min(region.getXsize(), Math.min(region.getYsize(), region.getZsize()));
        float span = Math.max(display.getBbWidth(), display.getBbHeight());
        // The fish fits its aquarium: bigger builds genuinely hold bigger fish.
        float scale = Math.min(trueScale, (float) (minDim - 0.5D) / Math.max(0.15F, span));
        float halfW = display.getBbWidth() * scale * 0.5F;
        float halfH = display.getBbHeight() * scale * 0.5F;

        // A slow lissajous wander filling the region: irrational-ish frequency ratios keep
        // the path from ever visibly repeating, and a per-tank phase desynchronizes a row
        // of separate aquariums.
        double t = tank.getLevel().getGameTime() + partialTick;
        BlockPos pos = tank.getBlockPos();
        double phase = (pos.asLong() * 0x9E3779B97F4A7C15L >>> 40 & 0xFFF) / 651.0D;

        double cx = (region.minX + region.maxX) * 0.5D;
        double cy = (region.minY + region.maxY) * 0.5D;
        double cz = (region.minZ + region.maxZ) * 0.5D;
        double ax = Math.max(0.0D, region.getXsize() * 0.5D - 0.25D - halfW);
        double ay = Math.max(0.0D, region.getYsize() * 0.5D - 0.30D - halfH);
        double az = Math.max(0.0D, region.getZsize() * 0.5D - 0.25D - halfW);

        double wx = 0.017D, wz = 0.011D, wy = 0.006D;
        double px = cx + Math.sin(t * wx + phase) * ax;
        double pz = cz + Math.sin(t * wz + phase * 1.7D) * az;
        double py = cy + Math.sin(t * wy + phase * 2.3D) * ay * 0.8D;

        // Face along the path's tangent, like something choosing where it swims.
        double dx = Math.cos(t * wx + phase) * wx * ax;
        double dz = Math.cos(t * wz + phase * 1.7D) * wz * az;
        float yaw = (dx * dx + dz * dz) > 1.0E-10D
                ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : 0.0F;

        // Real-time animation clock + in-water pose for the duration of this render only.
        display.tickCount = (int) tank.getLevel().getGameTime();
        display.setYRot(yaw);
        display.yRotO = yaw;
        if (display instanceof net.minecraft.world.entity.LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
        }

        pose.pushPose();
        pose.translate(px - pos.getX(), py - pos.getY() - halfH, pz - pos.getZ());
        pose.scale(scale, scale, scale);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean wasWet = display.wasTouchingWater;
        display.wasTouchingWater = true;
        dispatcher.setRenderShadow(false);
        dispatcher.render(display, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, pose, buffer, light);
        dispatcher.setRenderShadow(true);
        display.wasTouchingWater = wasWet;
        pose.popPose();
    }

    /** Live-fish sources: stamped catch items, fish spawn eggs, and fish buckets. */
    @Nullable
    private static Entity resolveDisplay(ItemStack stack)
    {
        if (stack.getItem() instanceof SpawnEggItem egg) {
            return FishCarryRenderer.displayForType(egg.getType(stack.getTag()));
        }
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && id.getPath().endsWith("_bucket")) {
            var entityId = ResourceLocation.fromNamespaceAndPath(
                    id.getNamespace(), id.getPath().substring(0, id.getPath().length() - 7));
            if (ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
                return FishCarryRenderer.displayForType(type);
            }
        }
        return FishCarryRenderer.displayFor(stack);
    }
}
