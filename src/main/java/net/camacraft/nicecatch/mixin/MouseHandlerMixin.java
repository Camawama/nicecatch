package net.camacraft.nicecatch.mixin;

import net.camacraft.nicecatch.client.ClientFishing;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the player is reeling a fish, harvest the accumulated mouse deltas for the reel tracker
 * and zero them out so the camera stays locked on the fish. The accumulator fields are made
 * public by our access transformer rather than shadowed.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin
{
    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void nicecatch$captureReelInput(CallbackInfo ci)
    {
        MouseHandler self = (MouseHandler) (Object) this;
        if (ClientFishing.isCapturingMouse()) {
            ClientFishing.onCapturedMouse(self.accumulatedDX, self.accumulatedDY);
            self.accumulatedDX = 0.0D;
            self.accumulatedDY = 0.0D;
            return;
        }
        // Right after reel mode ends the hand is often still circling; ease sensitivity back in
        // instead of letting the leftover spin whip the camera. turnPlayer consumes and zeroes
        // the accumulators every call, so scaling here applies exactly once per batch.
        float damp = ClientFishing.mouseDampFactor();
        if (damp < 1.0F) {
            self.accumulatedDX *= damp;
            self.accumulatedDY *= damp;
        }
    }
}
