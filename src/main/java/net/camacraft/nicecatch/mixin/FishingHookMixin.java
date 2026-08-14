package net.camacraft.nicecatch.mixin;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.RodUtil;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
    /** Player-to-player line distance last tick, to hear line being paid out; -1 = not tracking. */
    @Unique private double nicecatch$lastLineOut = -1.0D;
    /** Ticks a hooked player has held sneak toward breaking free. */
    @Unique private int nicecatch$escapeTicks;
    /** Re-show cadence for the "you've been hooked" actionbar warning. */
    @Unique private int nicecatch$warnCooldown;
    /** The rod item last seen in the owner's hands, for the abandoned-line cooldown. */
    @Unique private net.minecraft.world.item.Item nicecatch$lastRod;

    /**
     * Server-side line feel: (1) A hooked PLAYER is told on screen how to escape, and holding
     * sneak works the hook loose — breaking the line entirely — instead of being helplessly
     * dragged forever. (2) As line pays out from a settled bobber (walking away, trolling
     * behind a boat), the reel audibly clicks it out to everyone nearby.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void nicecatch$lineFeel(CallbackInfo ci)
    {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide) return;

        // Remember which rod owns this line while it is still in hand — by the time the
        // owner stows it (and vanilla gives up on the line), the hand only holds whatever
        // replaced it, and the abandoned-line cooldown below needs the rod itself.
        Player tracker = self.getPlayerOwner();
        if (tracker != null) {
            var rodHand = RodUtil.findRodHand(tracker);
            if (rodHand != null) nicecatch$lastRod = tracker.getItemInHand(rodHand).getItem();
        }

        // Hooked-player escape.
        if (self.hookedIn instanceof ServerPlayer hooked && self.getPlayerOwner() != hooked) {
            int need = NiceCatchConfig.SERVER.playerEscapeCrouchTicks.get();
            if (need > 0) {
                if (--nicecatch$warnCooldown <= 0) {
                    nicecatch$warnCooldown = 40;
                    hooked.displayClientMessage(Component.translatable("nicecatch.hooked.warning"), true);
                }
                if (hooked.isShiftKeyDown()) {
                    if (++nicecatch$escapeTicks >= need) {
                        hooked.displayClientMessage(Component.translatable("nicecatch.hooked.freed"), true);
                        self.level().playSound(null, hooked.getX(), hooked.getY(), hooked.getZ(),
                                SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
                        self.discard();
                        ci.cancel();
                        return;
                    }
                } else {
                    nicecatch$escapeTicks = Math.max(0, nicecatch$escapeTicks - 2);
                }
            }
        } else {
            nicecatch$escapeTicks = 0;
        }

        // Line pay-out clicks from a settled, fishless bobber.
        if (self.currentState == FishingHook.FishHookState.BOBBING && self.hookedIn == null) {
            Player owner = self.getPlayerOwner();
            if (owner != null) {
                double dist = self.distanceTo(owner);
                if (nicecatch$lastLineOut >= 0.0D && dist > nicecatch$lastLineOut + 0.04D
                        && dist > 6.0D && self.tickCount % 6 == 0) {
                    self.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                            SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS,
                            0.3F, 1.5F + (float) Math.min(0.4D, dist / 60.0D));
                }
                nicecatch$lastLineOut = dist;
                return;
            }
        }
        nicecatch$lastLineOut = -1.0D;
    }

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

    /**
     * Vanilla silently gives up on the line 32 blocks out (1024 squared) and discards the
     * hook. Every reel system caps itself against the configured rod range instead, so the
     * hard vanilla wall moves out to line.rodRangeLimit. Server-only call site: the client
     * short-circuits before shouldStopFishing, so reading server config here is safe.
     */
    @ModifyConstant(method = "shouldStopFishing", constant = @Constant(doubleValue = 1024.0D))
    private double nicecatch$rodRangeLimit(double vanilla)
    {
        double limit = NiceCatchConfig.SERVER.rodRangeLimit.get();
        return limit * limit;
    }

    /**
     * Abandoning a line out on the water — switching hotbar slots, stowing the rod, or
     * walking out past the line's absolute range — IS cutting it: the tackle is gone
     * either way, so it carries the same re-rig cooldown as the cut-line key. (Deliberate
     * retrieves go through the rod's own use path, never through this vanilla give-up
     * check, so reeling in normally costs nothing.)
     */
    @Inject(method = "shouldStopFishing", at = @At("RETURN"))
    private void nicecatch$abandonedLineCooldown(Player player, CallbackInfoReturnable<Boolean> cir)
    {
        if (!cir.getReturnValueZ() || nicecatch$lastRod == null) return;
        int cooldown = NiceCatchConfig.SERVER.cutLineCooldownTicks.get();
        if (cooldown > 0 && !player.getCooldowns().isOnCooldown(nicecatch$lastRod)) {
            player.getCooldowns().addCooldown(nicecatch$lastRod, cooldown);
        }
    }

    /**
     * A swimming fish cannot be snagged by a thrown bobber — vanilla lets a cast stick to
     * ANY entity it strikes, which trivializes the entire bite-and-hook game. The one honest
     * way onto a swimming fish's mouth is a bite it chose. A fish flopping on LAND is fair
     * game for a rescue-snag, and non-fish entities keep vanilla behavior.
     */
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void nicecatch$noSnaggingSwimmers(net.minecraft.world.entity.Entity target,
                                              CallbackInfoReturnable<Boolean> cir)
    {
        if (target instanceof net.minecraft.world.entity.PathfinderMob mob
                && mob.isInWater()
                && net.camacraft.nicecatch.server.FishBehavior.isFishLike(mob)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * A fish snagged out of water (the flopping-on-land rescue-snag canHitEntity allows) was
     * never hooked in the mouth — the barb is just caught on its body. The moment it touches
     * water it slips free and bolts: a real hold on a swimming fish only ever comes from a
     * bite it chose (which sets the FishBehavior hooked flag, exempting genuine fights —
     * those drag fish through water constantly). Server-side; hookedIn syncs to the client.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void nicecatch$snagSlipsInWater(CallbackInfo ci)
    {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide) return;
        if (self.hookedIn instanceof net.minecraft.world.entity.PathfinderMob fish
                && fish.isInWater()
                && FishBehavior.isFishLike(fish)
                && !FishBehavior.isHooked(fish)) {
            self.setHookedEntity(null);
            Player owner = self.getPlayerOwner();
            Vec3 from = owner != null ? owner.position() : self.position();
            FishBehavior.scatter(fish, from, NiceCatchConfig.SERVER.scatterDurationTicks.get());
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
        // Sublinear reach: on big fish the hitbox grows faster than the model's actual
        // snout distance, so a straight body-width fraction floats the bobber in the air
        // ahead of the head. Growth past the small-fish baseline is reined in — better a
        // hair inside a trophy's lip than hovering in front of it.
        double reach = Math.min(fish.getBbWidth() * REACH, 0.45D + fish.getBbWidth() * 0.35D);
        self.setPos(
                fish.getX() - Math.sin(rad) * reach,
                fish.getY(HEIGHT),
                fish.getZ() + Math.cos(rad) * reach);
    }
}
