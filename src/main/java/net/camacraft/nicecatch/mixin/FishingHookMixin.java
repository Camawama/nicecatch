package net.camacraft.nicecatch.mixin;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.RodUtil;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
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
    /**
     * Feature: the reel pays out line as you walk away from a cast bobber, and once you reach
     * the end of the spool it drags the bobber along behind you instead of the line snapping
     * back. Vanilla discards a hook past 32 blocks (the line just retracts); by pulling the
     * bobber in whenever it drifts past the (sub-32) spool length, we keep it within range so
     * that discard never fires, and it trails you like a real reel that has run out of line.
     *
     * Runs at tick HEAD on both sides using each side's own player position, so the client
     * drags its own bobber smoothly rather than waiting on infrequent server position syncs.
     * Only a settled, fishless, unsnagged bobber drags (BOBBING with no hooked entity) — a
     * fish fight glues the bobber to the fish instead, and a flying cast is left alone.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void nicecatch$spoolDrag(CallbackInfo ci)
    {
        FishingHook self = (FishingHook) (Object) this;
        if (!NiceCatchConfig.SERVER.spoolDragEnabled.get()) return;
        if (self.currentState != FishingHook.FishHookState.BOBBING || self.hookedIn != null) return;

        Player owner = self.getPlayerOwner();
        if (owner == null || RodUtil.findRodHand(owner) == null) return;

        double spool = NiceCatchConfig.SERVER.spoolLength.get();
        double dx = owner.getX() - self.getX();
        double dy = (owner.getY() + 0.3D) - self.getY();
        double dz = owner.getZ() - self.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0E-4D || dist <= spool) return;
        double inv = 1.0D / dist;

        // Velocity pull toward the player for a smooth trailing feel, ramping up quickly so it
        // keeps pace with a moving boat well before the danger zone.
        double pull = Math.min(0.9D, 0.15D + (dist - spool) * 0.6D);
        Vec3 v = self.getDeltaMovement();
        self.setDeltaMovement(
                v.x * 0.6D + dx * inv * pull,
                v.y * 0.6D + dy * inv * pull * 0.5D,
                v.z * 0.6D + dz * inv * pull);

        // Hard backstop: however fast the player moves, never let the bobber sit far enough out
        // for vanilla to retract the line (>32 blocks). Snap it to the spool distance if needed.
        double maxOut = spool + 1.0D;
        if (dist > maxOut) {
            double f = maxOut * inv;
            self.setPos(owner.getX() - dx * f, (owner.getY() + 0.3D) - dy * f, owner.getZ() - dz * f);
        }
    }

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
     *
     * The reach is pushed right out to the snout and the pin sits a little low, so the hook
     * texture at the bottom of the bobber sprite sinks into the front of the head and reads as
     * a hook set in the fish's mouth rather than a bobber hovering above it. Tunable: REACH
     * fraction of body width forward, HEIGHT fraction of body height up.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void nicecatch$bobberInHead(CallbackInfo ci)
    {
        FishingHook self = (FishingHook) (Object) this;
        if (self.currentState != FishingHook.FishHookState.HOOKED_IN_ENTITY) return;
        if (!(self.hookedIn instanceof WaterAnimal fish)) return;

        final double REACH = 0.9D;   // forward along the body, in body-widths — out to the snout
        final double HEIGHT = 0.42D;  // up the body, in body-heights — low so the hook meets the mouth
        double rad = Math.toRadians(fish.yBodyRot);
        double reach = fish.getBbWidth() * REACH;
        self.setPos(
                fish.getX() - Math.sin(rad) * reach,
                fish.getY(HEIGHT),
                fish.getZ() + Math.cos(rad) * reach);
    }
}
