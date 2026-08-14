package net.camacraft.nicecatch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client registry of stand-parked fishing lines, fed by the rod stand's block-entity sync.
 * Two consumers: the fishing-hook renderer mixin swaps the line's rod-tip anchor for the
 * stand's tip (so a parked line visibly runs bobber-to-stand, not bobber-to-empty-hands),
 * and the local player's stale {@code fishing} reference is cleared/kept honest so casting
 * the NEXT rod works the moment the first one is parked.
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

        // The local player's fishing slot: vanilla only clears it when the hook dies, so a
        // freshly-parked rod would leave the client convinced its line is still in hand
        // (blocking the next cast). Mirror the server: parked = slot free.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && owner != null && owner.equals(mc.player.getUUID())) {
            if (held && mc.player.fishing != null && mc.player.fishing.getId() == hookId) {
                mc.player.fishing = null;
            } else if (!held && mc.player.fishing == null && mc.level != null
                    && mc.level.getEntity(hookId) instanceof net.minecraft.world.entity.projectile.FishingHook hook
                    && hook.isAlive()) {
                mc.player.fishing = hook; // the rod (and its line) came back to our hands
            }
        }
    }

    public static void clear(BlockPos stand)
    {
        Integer id = BY_STAND.remove(stand);
        if (id != null) BY_HOOK.remove(id);
    }

    /** The stand tip a parked hook's line should anchor to, or null for ordinary hooks. */
    @Nullable
    public static Vec3 lineAnchor(int hookId)
    {
        BlockPos stand = BY_HOOK.get(hookId);
        return stand == null ? null
                : new Vec3(stand.getX() + 0.5D, stand.getY() + 1.05D, stand.getZ() + 0.5D);
    }
}
