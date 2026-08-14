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

    /**
     * A stand-parked line anchors to the STAND, not the absent rod: vanilla computes the
     * line's rod-tip end (locals d4/d5/d6, world position) from the owner's arm, which for
     * a parked rod would draw the line to a player standing empty-handed across the pond.
     * Each coordinate's store is swapped for the stand tip when the hook is registered as
     * stand-held; ordinary lines are untouched.
     */
    // require = 0 on all three: these lean on vanilla's local-variable ordering, which a
    // Forge or mapping update could shuffle. If they ever fail to apply, the line merely
    // falls back to drawing toward the player instead of crashing the game on load.
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "render", at = @At("STORE"), ordinal = 4, require = 0)
    private double nicecatch$standAnchorX(double value, FishingHook hook)
    {
        var anchor = net.camacraft.nicecatch.client.StandLines.lineAnchor(hook.getId());
        return anchor != null ? anchor.x : value;
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "render", at = @At("STORE"), ordinal = 5, require = 0)
    private double nicecatch$standAnchorY(double value, FishingHook hook)
    {
        var anchor = net.camacraft.nicecatch.client.StandLines.lineAnchor(hook.getId());
        return anchor != null ? anchor.y : value;
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "render", at = @At("STORE"), ordinal = 6, require = 0)
    private double nicecatch$standAnchorZ(double value, FishingHook hook)
    {
        var anchor = net.camacraft.nicecatch.client.StandLines.lineAnchor(hook.getId());
        return anchor != null ? anchor.z : value;
    }
}
