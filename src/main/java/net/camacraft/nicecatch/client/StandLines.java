package net.camacraft.nicecatch.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client registry of stand-parked fishing lines, fed by the rod stand's block-entity sync.
 * Consumers: the fishing-hook renderer mixin swaps the line's rod-tip anchor for the
 * stand's planted rod tip, the stand's renderer asks which way to aim the rod, and the
 * local player's {@code fishing} reference is kept honest through park/take transitions
 * (parked = slot free so the next rod can cast; taken back = slot restored, or the client
 * soft-locks into casts the server ignores).
 */
public final class StandLines
{
    /** hook entity id -> the stand holding its rod. */
    private static final Map<Integer, BlockPos> BY_HOOK = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> BY_STAND = new ConcurrentHashMap<>();

    private StandLines() {}

    public static void update(BlockPos stand, int hookId, @Nullable UUID owner, boolean held)
    {
        Integer previous = BY_STAND.remove(stand);
        if (previous != null) BY_HOOK.remove(previous);
        if (held && hookId >= 0) {
            BY_STAND.put(stand, hookId);
            BY_HOOK.put(hookId, stand);
        }

        // The local player's fishing slot: vanilla only clears it when the hook dies, so
        // park/take must mirror the server by hand. NOTE the take-back path restores from
        // PREVIOUS — by the time the release syncs, the packet's own hookId is already -1.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || owner == null || !owner.equals(mc.player.getUUID())) return;
        if (held) {
            if (mc.player.fishing != null && mc.player.fishing.getId() == hookId) {
                mc.player.fishing = null;
            }
        } else if (previous != null && mc.player.fishing == null && mc.level != null
                && mc.level.getEntity(previous) instanceof FishingHook hook
                && hook.isAlive()) {
            mc.player.fishing = hook; // the rod (and its line) came back to our hands
        }
    }

    public static void clear(BlockPos stand)
    {
        Integer id = BY_STAND.remove(stand);
        if (id != null) BY_HOOK.remove(id);
    }

    /** The stand this hook's rod is parked on, or null. */
    @Nullable
    public static BlockPos standFor(int hookId)
    {
        return BY_HOOK.get(hookId);
    }

    /** Horizontal direction the planted rod leans (toward its bobber); null when unknown. */
    @Nullable
    public static Vec3 rodLean(BlockPos stand, int hookId)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (!(mc.level.getEntity(hookId) instanceof FishingHook hook)) return null;
        Vec3 toHook = new Vec3(hook.getX() - (stand.getX() + 0.5D), 0.0D,
                hook.getZ() - (stand.getZ() + 0.5D));
        return toHook.lengthSqr() < 1.0E-4D ? null : toHook.normalize();
    }

    /**
     * The line anchor for a parked hook: the planted rod's TIP, leaned toward the bobber
     * (matching RodStandRenderer's pose). Vanilla adds its own hand-lift to whatever
     * anchor it computes — the local first-person eye height, or a small crouch dip — so
     * that is SUBTRACTED here in advance; without this the line floated a full head above
     * the stand whenever its owner looked at it in first person.
     */
    @Nullable
    public static Vec3 lineAnchor(int hookId)
    {
        BlockPos stand = BY_HOOK.get(hookId);
        if (stand == null) return null;
        Vec3 lean = rodLean(stand, hookId);
        double dx = lean != null ? lean.x : 0.0D;
        double dz = lean != null ? lean.z : 0.0D;
        double tipX = stand.getX() + 0.5D + dx * 0.38D;
        double tipY = stand.getY() + 1.28D;
        double tipZ = stand.getZ() + 0.5D + dz * 0.38D;

        Minecraft mc = Minecraft.getInstance();
        float lift = 0.0F;
        if (mc.level != null && mc.level.getEntity(hookId) instanceof FishingHook hook) {
            Player owner = hook.getPlayerOwner();
            if (owner != null) {
                boolean firstPerson = owner == mc.player
                        && mc.options.getCameraType() == CameraType.FIRST_PERSON;
                lift = firstPerson ? owner.getEyeHeight() : (owner.isCrouching() ? -0.1875F : 0.0F);
            }
        }
        return new Vec3(tipX, tipY - lift, tipZ);
    }
}
