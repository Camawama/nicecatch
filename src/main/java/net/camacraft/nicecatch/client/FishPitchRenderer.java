package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fish pitch with their motion: swimming upward tips the nose up, diving tips it down, level
 * cruising stays level — smoothed per entity so it sways instead of snapping. Purely visual
 * and purely client-side: the render pose is tilted around the fish's own lateral axis (its
 * mid-body as the pivot), so AI, hitboxes, and server state are untouched. Fish out of water
 * are left alone — vanilla's flopping animation stays exactly as it was.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID, value = Dist.CLIENT)
public final class FishPitchRenderer
{
    /** Steepest visual pitch; real fish rarely porpoise vertically. */
    private static final float MAX_PITCH_DEGREES = 40.0F;
    /** Per-tick smoothing retention (applied frame-rate independently). */
    private static final double SMOOTHING = 0.82D;

    /** Smoothed pitch per fish; weak keys evaporate with the entities. */
    private static final Map<LivingEntity, Float> SMOOTHED = new WeakHashMap<>();
    /** Entities whose render we pushed a matrix for; popped in Post so the SHADOW (drawn by
     * the dispatcher after the model) stays flat and unscaled instead of tilting with the fish. */
    private static final java.util.Set<LivingEntity> PUSHED =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private FishPitchRenderer() {}

    /** LOWEST: runs after any canceller, so a cancelled render can never leak our push. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event)
    {
        LivingEntity entity = event.getEntity();
        if (!FishBehavior.isFishKind(entity)) return;

        float pitch;
        float scale;
        if (entity.isAddedToWorld()) {
            pitch = smoothedPitch(entity);
            scale = net.camacraft.nicecatch.server.FishSizing.scaleOf(entity);
        } else {
            // A display dummy, not a world fish — its UUID is a fresh roll every creation,
            // so the world-size variance must NEVER touch it (an Aquaculture mount's fish
            // was changing size on every placement). In-hand carry dummies size themselves;
            // a mount's fish is sized from its item's stamped weight — the actual caught
            // individual; anything else (some mod's preview) is left entirely alone.
            if (FishCarryRenderer.isDisplayEntity(entity)) return;
            var mounted = net.camacraft.nicecatch.compat.AquacultureCompat.mountItemForDisplayEntity(entity);
            if (mounted == null) return;
            pitch = 0.0F;
            scale = FishCarryRenderer.displayScale(entity, mounted);
        }
        boolean tilt = Math.abs(pitch) >= 0.5F;
        boolean resize = Math.abs(scale - 1.0F) >= 0.001F;
        if (!tilt && !resize) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose(); // scoped to the model render only; popped in Post (shadows stay flat)
        PUSHED.add(entity);
        // The pitch tilt goes on the stack FIRST (outermost — world units, so its pivot is
        // the scaled hitbox center), the body scale LAST (innermost, about the feet origin,
        // so the model grows to match its individually-scaled hitbox — see FishSizing).
        if (tilt) {
            float yaw = Mth.lerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot);
            double pivotY = entity.getBbHeight() * 0.5D;
            pose.translate(0.0D, pivotY, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(0.0D, -pivotY, 0.0D);
        }
        if (resize) {
            pose.scale(scale, scale, scale);
        }
    }

    /** HIGHEST: pops our push before anyone else reads the stack in Post. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event)
    {
        if (PUSHED.remove(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    /**
     * Advance and return this fish's smoothed swim pitch; 0 out of water (vanilla's land
     * flopping stays untouched) or when the feature is off.
     */
    private static float smoothedPitch(LivingEntity entity)
    {
        if (!NiceCatchConfig.CLIENT.fishPitchEnabled.get()) return 0.0F;
        if (!entity.isInWater()) {
            SMOOTHED.remove(entity);
            return 0.0F;
        }

        // Last-tick movement (client-interpolated positions update xOld/yOld/zOld every tick).
        double dx = entity.getX() - entity.xOld;
        double dy = entity.getY() - entity.yOld;
        double dz = entity.getZ() - entity.zOld;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float target = 0.0F;
        if (horiz > 1.0E-3D || Math.abs(dy) > 1.0E-3D) {
            target = Mth.clamp((float) -Math.toDegrees(Mth.atan2(dy, Math.max(horiz, 0.02D))),
                    -MAX_PITCH_DEGREES, MAX_PITCH_DEGREES);
        }

        // Schooled fish MOVE in near-lockstep (boids alignment), which made their pitch a
        // single synchronized wave that read as robotic. Two per-fish breakers: each fish
        // smooths at its own slightly different rate, and each carries its own quiet
        // undulation offset — same water, individual bodies.
        int seed = entity.getId();
        float personalSmoothing = (float) SMOOTHING + ((seed % 7) - 3) * 0.015F;
        float undulation = Mth.sin((entity.tickCount + Minecraft.getInstance().getPartialTick()
                + (seed & 63) * 5.1F) * (0.055F + (seed % 5) * 0.012F)) * 2.5F;

        float frameTicks = Minecraft.getInstance().getDeltaFrameTime();
        float ease = 1.0F - (float) Math.pow(personalSmoothing, frameTicks);
        float pitch = SMOOTHED.getOrDefault(entity, 0.0F);
        pitch += (target - pitch) * ease;
        SMOOTHED.put(entity, pitch);
        return pitch + undulation;
    }
}
