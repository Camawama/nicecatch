package net.camacraft.nicecatch.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.nicecatch.client.FishCarryRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held fish render as the actual fish entity instead of the flat item sprite. This method
 * is the single funnel for in-hand item rendering — the first-person hands call it
 * directly and every third-person ItemInHandLayer (players, armor stands, mobs) routes
 * through it — with the pose stack already at the hand socket, so one hook covers every
 * perspective. When the carry renderer declines (not a fish, no entity match, feature off),
 * vanilla proceeds untouched.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin
{
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void nicecatch$renderCarriedFish(LivingEntity holder, ItemStack stack, ItemDisplayContext context,
                                             boolean leftHand, PoseStack poseStack, MultiBufferSource buffer,
                                             int light, CallbackInfo ci)
    {
        // Both arms hefting a heavy fish leaves no hand free: whatever the other hand
        // nominally holds — a torch, or even a second heavy fish — is not rendered at
        // all while the carry lasts.
        if (FishCarryRenderer.suppressedByCarry(holder, stack, leftHand)) {
            ci.cancel();
            return;
        }
        if (FishCarryRenderer.render(holder, stack, context, leftHand, poseStack, buffer, light)) {
            ci.cancel();
        }
    }
}
