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

    private FishPitchRenderer() {}

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event)
    {
        LivingEntity entity = event.getEntity();
        if (!FishBehavior.isFishKind(entity)) return;
        // In-hand display dummies size themselves; our world-fish scale must not compound.
        if (FishCarryRenderer.isDisplayEntity(entity)) return;

        PoseStack pose = event.getPoseStack();
        // The pitch tilt goes on the stack FIRST (outermost — world units, so its pivot is
        // the scaled hitbox center), the body scale LAST (innermost, about the feet origin,
        // so the model grows to match its individually-scaled hitbox — see FishSizing).
        float pitch = smoothedPitch(entity);
        if (Math.abs(pitch) >= 0.5F) {
            float yaw = Mth.lerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot);
            double pivotY = entity.getBbHeight() * 0.5D;
            pose.translate(0.0D, pivotY, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(0.0D, -pivotY, 0.0D);
        }
        float scale = net.camacraft.nicecatch.server.FishSizing.scaleOf(entity);
        if (Math.abs(scale - 1.0F) >= 0.001F) {
            pose.scale(scale, scale, scale);
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

        float frameTicks = Minecraft.getInstance().getDeltaFrameTime();
        float ease = 1.0F - (float) Math.pow(SMOOTHING, frameTicks);
        float pitch = SMOOTHED.getOrDefault(entity, 0.0F);
        pitch += (target - pitch) * ease;
        SMOOTHED.put(entity, pitch);
        return pitch;
    }
}
