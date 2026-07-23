package net.camacraft.nicecatch.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the bobber ride a hooked fish with zero desync. FishingHook.lerpTo is empty — the
 * client bobber ignores every position correction from the server and simulates its own
 * physics, so no amount of server-side velocity matching can keep the two in step. Vanilla's
 * HOOKED_IN_ENTITY state is the one mechanism that can: both sides re-pin the hook to the
 * hooked entity's position every tick, so the client glues it to its own interpolated fish.
 *
 * Vanilla only enters that state from FLYING (a thrown bobber striking an entity). These two
 * transitions let the fight system enter it from BOBBING when a real fish takes the line, and
 * fall back to BOBBING when the fish gets off, on both sides symmetrically (hookedIn syncs
 * via entity data). Fields are AT'd public rather than shadowed.
 *
 * The trigger is simply "hookedIn is set": the only thing that points a bobbing hook at an
 * entity is the fight's own setHookedEntity(fish), so no entity-type check is needed here —
 * which also keeps this client-side path free of any server-config lookups.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin
{
    @Inject(method = "tick", at = @At("HEAD"))
    private void nicecatch$glueToHookedFish(CallbackInfo ci)
    {
        FishingHook self = (FishingHook) (Object) this;
        if (self.hookedIn != null) {
            if (self.currentState == FishingHook.FishHookState.BOBBING) {
                self.currentState = FishingHook.FishHookState.HOOKED_IN_ENTITY;
            }
        } else if (self.currentState == FishingHook.FishHookState.HOOKED_IN_ENTITY) {
            // The fish came off the line; resume bobbing so the hook floats back up.
            self.currentState = FishingHook.FishHookState.BOBBING;
        }
    }
}
