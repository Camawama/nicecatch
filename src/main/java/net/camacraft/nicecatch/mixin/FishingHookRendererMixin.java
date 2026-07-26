package net.camacraft.nicecatch.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.projectile.FishingHook;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The bobber renders as a flat, camera-facing billboard: the sprite is oriented by
 * {@code poseStack.mulPose(entityRenderDispatcher.cameraOrientation())}, and the hook is the
 * little tine at the bottom of that sprite. The companion {@code FishingHookMixin} already
 * slides the bobber forward to a hooked fish's snout; this rolls the billboard in the screen
 * plane so that hook swings up and clips into the fish's mouth instead of dangling below it.
 *
 * Redirecting the camera-orientation call (rather than injecting between the two mulPose calls)
 * keeps this robust: we return the same orientation, optionally pre-rolled about the view axis,
 * and never touch the dispatcher's shared quaternion instance. Only a bobber hooked into a fish
 * (a WaterAnimal) is affected; a thrown bobber or an item snag renders exactly as vanilla.
 */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin
{
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;cameraOrientation()Lorg/joml/Quaternionf;"))
    private Quaternionf nicecatch$tiltHookIntoMouth(EntityRenderDispatcher dispatcher,
                                                    FishingHook hook, float entityYaw, float partialTicks,
                                                    PoseStack poseStack, MultiBufferSource buffer, int light)
    {
        Quaternionf orientation = dispatcher.cameraOrientation();
        if (!NiceCatchConfig.CLIENT.bobberHookInMouth.get()) return orientation;
        if (hook.currentState != FishingHook.FishHookState.HOOKED_IN_ENTITY) return orientation;
        if (!(hook.hookedIn instanceof WaterAnimal)) return orientation;
        float degrees = NiceCatchConfig.CLIENT.bobberHookRotationDegrees.get().floatValue();
        if (degrees == 0.0F) return orientation;
        // Copy first — the dispatcher hands back a shared quaternion that must not be mutated.
        return new Quaternionf(orientation).mul(Axis.ZP.rotationDegrees(degrees));
    }
}
