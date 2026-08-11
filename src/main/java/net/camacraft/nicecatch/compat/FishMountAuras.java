package net.camacraft.nicecatch.compat;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishTraits;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A trophy keeps its shine: an Aquaculture fish mount displaying a fish caught with an aura
 * trait (glimmering, cosmic, molten...) sheds that trait's particles on the wall, same as
 * the living fish did. Mounts are tracked as they join their level (no per-tick entity
 * scans), everything is weakly referenced so unloads clean themselves up, and the displayed
 * item is re-read each pulse so swapping the fish on the mount just works.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public final class FishMountAuras
{
    /** Ticks between particle pulses. */
    private static final int PULSE_INTERVAL = 8;

    private static final Map<Level, Set<Entity>> MOUNTS = new WeakHashMap<>();
    /** Client-side mount tracking, so renderers can match a mount's display fish to its item. */
    private static final Map<Level, Set<Entity>> CLIENT_MOUNTS = new WeakHashMap<>();

    private FishMountAuras() {}

    /** The known fish mounts in this client level (empty without Aquaculture). */
    public static Iterable<Entity> clientMounts(Level level)
    {
        Set<Entity> mounts = CLIENT_MOUNTS.get(level);
        return mounts != null ? mounts : Collections.emptySet();
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if (!AquacultureCompat.loaded()) return;
        Entity entity = event.getEntity();
        if (!AquacultureCompat.isFishMount(entity)) return;
        Map<Level, Set<Entity>> registry = event.getLevel().isClientSide ? CLIENT_MOUNTS : MOUNTS;
        registry.computeIfAbsent(event.getLevel(),
                level -> Collections.newSetFromMap(new WeakHashMap<>())).add(entity);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % PULSE_INTERVAL != 0) return;
        if (!NiceCatchConfig.SERVER.traitAurasEnabled.get()) return;

        Set<Entity> mounts = MOUNTS.get(level);
        if (mounts == null || mounts.isEmpty()) return;
        Iterator<Entity> it = mounts.iterator();
        while (it.hasNext()) {
            Entity mount = it.next();
            if (!mount.isAlive()) {
                it.remove();
                continue;
            }
            SimpleParticleType aura = FishTraits.auraFromStack(AquacultureCompat.mountedItem(mount));
            if (aura == null) continue;
            level.sendParticles(aura, mount.getX(), mount.getY(), mount.getZ(),
                    2, 0.25D, 0.25D, 0.25D, 0.004D);
        }
    }
}
