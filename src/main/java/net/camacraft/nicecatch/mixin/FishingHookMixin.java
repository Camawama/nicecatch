package net.camacraft.nicecatch.mixin;

import net.minecraft.world.entity.animal.WaterAnimal;
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

    /**
     * Vanilla pins a hooked entity's bobber to the middle of its model (setPos to x, y(0.8), z),
     * so on a fish the line looks stuck through its spine. Slide the bobber forward along the
     * fish's body facing instead, so it reads as set in the head/mouth and swings around as the
     * fish thrashes and turns. Runs at every tick return so it lands right after vanilla's own
     * pin inside the HOOKED_IN_ENTITY branch, and on both sides (the client re-pins each tick
     * too, or the offset would desync). Purely cosmetic: the fight always reads the fish's own
     * position, never the bobber's, so nudging the bobber changes nothing about the mechanics.
     * WaterAnimal covers every fish — vanilla AbstractFish, Aquaculture, and Unusual Fish all
     * extend it — with no server-config lookup, and non-fish snags keep vanilla's centre pin.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void nicecatch$bobberInHead(CallbackInfo ci)
    {
        FishingHook self = (FishingHook) (Object) this;
        if (self.currentState != FishingHook.FishHookState.HOOKED_IN_ENTITY) return;
        if (!(self.hookedIn instanceof WaterAnimal fish)) return;

        double rad = Math.toRadians(fish.yBodyRot);
        double reach = fish.getBbWidth() * 0.8D;
        self.setPos(
                fish.getX() - Math.sin(rad) * reach,
                fish.getY(0.5D),
                fish.getZ() + Math.cos(rad) * reach);
    }
}
