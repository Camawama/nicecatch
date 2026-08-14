package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.nicecatch.block.FishTankBlockEntity;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Renders the tank's occupant swimming its aquarium. The fish glides from tank cell to
 * ADJACENT tank cell (a random walk over the actual filled blocks), so in an L- or
 * T-shaped build it never drifts through open air — it can only be where glass and water
 * actually are. It faces its motion, sits upright, and fins at real-time rate.
 *
 * Tanks keep their OWN display dummies, deliberately not shared with the in-hand carry
 * renderer: the tank writes yaw and animation state into its dummy every frame, and a
 * shared dummy made the fish item in the player's hand mirror the tank fish's motion.
 */
public class FishTankRenderer implements BlockEntityRenderer<FishTankBlockEntity>
{
    /** Tank-private dummies (per type), never added to the world. */
    private static final Map<EntityType<?>, Entity> TANK_DUMMIES = new HashMap<>();
    @Nullable private static Level dummyLevel;

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
        // Fit to a single cell's interior, so the fish can follow corridors of any build.
        float span = Math.max(display.getBbWidth(), display.getBbHeight());
        float scale = Math.min(trueScale, 0.55F / Math.max(0.15F, span));
        float halfH = display.getBbHeight() * scale * 0.5F;

        // Advance the cell-to-cell glide (frame-rate independent via the world clock).
        List<BlockPos> cells = tank.tankCells();
        double now = tank.getLevel().getGameTime() + partialTick;
        if (tank.swimFrom == null || tank.swimTo == null
                || !cells.contains(tank.swimFrom) || !cells.contains(tank.swimTo)) {
            tank.swimFrom = tank.getBlockPos();
            tank.swimTo = pickNeighbor(cells, tank.swimFrom, tank.getBlockPos());
            tank.swimProgress = 0.0D;
        }
        tank.swimProgress += 0.017D * frameDelta(tank, now);
        if (tank.swimProgress >= 1.0D) {
            tank.swimProgress = 0.0D;
            tank.swimFrom = tank.swimTo;
            tank.swimTo = pickNeighbor(cells, tank.swimFrom, tank.getBlockPos());
        }

        double eased = tank.swimProgress * tank.swimProgress * (3.0D - 2.0D * tank.swimProgress);
        double px = Mth.lerp(eased, tank.swimFrom.getX(), tank.swimTo.getX()) + 0.5D;
        double py = Mth.lerp(eased, tank.swimFrom.getY(), tank.swimTo.getY()) + 0.45D
                + Math.sin(now * 0.07D) * 0.04D;
        double pz = Mth.lerp(eased, tank.swimFrom.getZ(), tank.swimTo.getZ()) + 0.5D;

        // Face travel; hovering in place keeps the last heading and idles a slow turn.
        double dx = tank.swimTo.getX() - tank.swimFrom.getX();
        double dz = tank.swimTo.getZ() - tank.swimFrom.getZ();
        float targetYaw = (dx * dx + dz * dz) > 1.0E-6D
                ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : tank.swimYaw + 0.15F;
        tank.swimYaw += Mth.wrapDegrees(targetYaw - tank.swimYaw) * 0.06F;

        // Real-time animation clock + upright in-water pose, on OUR dummy only.
        display.tickCount = (int) tank.getLevel().getGameTime();
        display.setYRot(tank.swimYaw);
        display.yRotO = tank.swimYaw;
        if (display instanceof LivingEntity living) {
            living.yBodyRot = tank.swimYaw;
            living.yBodyRotO = tank.swimYaw;
            living.yHeadRot = tank.swimYaw;
            living.yHeadRotO = tank.swimYaw;
        }

        BlockPos pos = tank.getBlockPos();
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

    /** Per-tank frame delta from the world clock (render time isn't tick time). */
    private static final Map<BlockPos, Double> LAST_RENDER = new HashMap<>();

    private static double frameDelta(FishTankBlockEntity tank, double now)
    {
        Double last = LAST_RENDER.put(tank.getBlockPos().immutable(), now);
        if (last == null) return 0.0D;
        double delta = now - last;
        return delta > 0.0D && delta < 5.0D ? delta : 0.0D;
    }

    /** A random ADJACENT filled cell (deterministic-ish, but wandering); stays put if boxed in. */
    private static BlockPos pickNeighbor(List<BlockPos> cells, BlockPos from, BlockPos seedPos)
    {
        var options = new java.util.ArrayList<BlockPos>(6);
        for (var dir : net.minecraft.core.Direction.values()) {
            BlockPos next = from.relative(dir);
            if (cells.contains(next)) options.add(next);
        }
        if (options.isEmpty()) return from;
        Random random = new Random(seedPos.asLong() * 31L + System.nanoTime() / 200_000_000L);
        return options.get(random.nextInt(options.size()));
    }

    /** Live-fish sources: stamped catch items, fish spawn eggs, and fish buckets. */
    @Nullable
    private static Entity resolveDisplay(ItemStack stack)
    {
        if (stack.getItem() instanceof SpawnEggItem egg) {
            return tankDummy(egg.getType(stack.getTag()));
        }
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && id.getPath().endsWith("_bucket")) {
            var entityId = ResourceLocation.fromNamespaceAndPath(
                    id.getNamespace(), id.getPath().substring(0, id.getPath().length() - 7));
            if (ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
                return tankDummy(ForgeRegistries.ENTITY_TYPES.getValue(entityId));
            }
        }
        if (id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            return tankDummy(ForgeRegistries.ENTITY_TYPES.getValue(id));
        }
        return null;
    }

    @Nullable
    private static Entity tankDummy(@Nullable EntityType<?> type)
    {
        Minecraft mc = Minecraft.getInstance();
        if (type == null || mc.level == null) return null;
        if (dummyLevel != mc.level) {
            TANK_DUMMIES.clear();
            LAST_RENDER.clear();
            dummyLevel = mc.level;
        }
        Entity cached = TANK_DUMMIES.get(type);
        if (cached != null) return cached;
        Entity created = type.create(mc.level);
        if (created == null || !FishBehavior.isFishKind(created)) {
            if (created != null) created.discard();
            return null;
        }
        TANK_DUMMIES.put(type, created);
        FishCarryRenderer.markDisplayEntity(created);
        return created;
    }
}
