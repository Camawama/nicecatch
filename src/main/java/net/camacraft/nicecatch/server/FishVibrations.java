package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.goal.FoodInterestGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The fish's ears: sculk hearing without the sculk. The old scare system watched entity
 * positions from each fish's side, polled on a stagger against single-tick movement
 * snapshots and chance rolls — which is exactly why walking or swimming right past a fish
 * sometimes did nothing. Vanilla already broadcasts every noteworthy sound and movement as
 * a {@link GameEvent} (that is what sculk sensors and wardens listen to), and Forge mirrors
 * each one through {@link VanillaGameEvent}; listening there is event-driven, so a footstep
 * CANNOT be missed — the world tells us it happened.
 *
 * The vanilla vibration rulebook is honored so it feels like sculk: only events in the
 * VIBRATIONS tag count, sneaking suppresses the sneak-exempt ones (crouched anglers stay
 * invisible, matching the mod's stealth rules), vibration-dampening entities are silent,
 * and wool occludes the signal. Because we merely observe the broadcast and no sculk
 * sensor is ever involved, no sculk click, shriek, or vibration particle can ever play.
 *
 * Deliberate departures from raw sculk, so fishing itself survives having sharp-eared fish:
 * the angler's own rod work is silent (the bobber emits nothing and ITEM_INTERACT_FINISH —
 * the rod-use event — is ignored, along with quiet mouth sounds like eating and drinking);
 * fish never hear each other; thrown FOOD keeps FoodInterestGoal's gentler splash-startle-
 * then-attract treatment (a thrown rock, though, is just a scary splash); and land-walking
 * animals are ambient noise fish are used to — only players, things in the water with them,
 * and genuinely loud events carry.
 *
 * A heard vibration is latched onto each fish in range (FishBehavior.hearVibration) and
 * ScatterGoal reads the latch on every poll as a certain threat — reliable end to end.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public final class FishVibrations
{
    /** Last game time each source's noise was processed; movement events spam every tick. */
    private static final Map<Entity, Long> LAST_HEARD = new WeakHashMap<>();
    private static final int SOURCE_THROTTLE_TICKS = 15;
    /** Worst-case cap: accepted vibrations per level tick, so a mob-dense area can't stack
     * entity queries. Loud events always land; quiet ones past the cap just go unheard. */
    private static final int MAX_EVENTS_PER_TICK = 16;
    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetUsed;

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!NiceCatchConfig.SERVER_SPEC.isLoaded()
                || !NiceCatchConfig.SERVER.vibrationScareEnabled.get()) return;

        GameEvent gameEvent = event.getVanillaEvent();
        if (!gameEvent.is(GameEventTags.VIBRATIONS)) return;
        // The angler's own rod use, and quiet mouth sounds, never spook the pond.
        if (gameEvent == GameEvent.ITEM_INTERACT_FINISH
                || gameEvent == GameEvent.EAT || gameEvent == GameEvent.DRINK) return;

        GameEvent.Context context = event.getContext();
        Entity source = context.sourceEntity();
        if (source != null) {
            // Vanilla's own vibration validity rules: spectators are silent, sneaking mutes
            // the sneak-exempt events (steps, swimming, bow shots...), and entities that
            // dampen vibrations make none.
            if (source.isSpectator()) return;
            if (source.isSteppingCarefully() && gameEvent.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) return;
            if (source.dampensVibrations()) return;
            // Ours: fish don't hear each other (a pond would deafen itself), the bobber is
            // part of the fishing the fish are supposed to approach, chum keeps its gentler
            // startle-vs-attract roll, and land wildlife is ambient background noise.
            if (FishBehavior.isFishKind(source) || source instanceof FishingHook) return;
            if (source instanceof ItemEntity item) {
                if (FoodInterestGoal.isFishFood(item.getItem())) return;
            } else if (source instanceof LivingEntity && !(source instanceof Player)
                    && !source.isInWater()) {
                return;
            }
        }
        // Sculk parity: acting on a vibration-dampening block (wool) makes no vibration.
        if (context.affectedState() != null
                && context.affectedState().is(BlockTags.DAMPENS_VIBRATIONS)) return;

        // Movement events fire every step/stroke; once per throttle window per source keeps
        // the fear fresh without stacking panic fatigue to the cap in seconds. Loud events
        // are rare and always land.
        long now = level.getGameTime();
        boolean loud = isLoud(gameEvent);
        if (source != null && !loud) {
            Long last = LAST_HEARD.get(source);
            if (last != null && now - last < SOURCE_THROTTLE_TICKS) return;
            LAST_HEARD.put(source, now);
        }
        if (budgetTick != now) {
            budgetTick = now;
            budgetUsed = 0;
        }
        if (!loud && ++budgetUsed > MAX_EVENTS_PER_TICK) return;

        double radius = NiceCatchConfig.SERVER.vibrationScareRadius.get() * (loud ? 1.5D : 1.0D);
        Vec3 pos = event.getEventPosition();
        double radiusSqr = radius * radius;
        AABB box = AABB.ofSize(pos, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (PathfinderMob fish : level.getEntitiesOfClass(PathfinderMob.class, box,
                f -> f.isAlive() && !FishBehavior.isHooked(f) && FishBehavior.isFishLike(f))) {
            double distSqr = fish.distanceToSqr(pos.x, pos.y, pos.z);
            if (distSqr > radiusSqr) continue;
            if (isOccluded(level, pos, fish.getEyePosition())) continue;
            // Close-by or loud cuts through even a habituated fish's calm, like a swimmer
            // bearing straight down on it does.
            FishBehavior.hearVibration(fish, pos, loud || distSqr <= radiusSqr * 0.25D);
        }
    }

    /** Events that carry further and always startle: nothing sleeps through an explosion. */
    private static boolean isLoud(GameEvent gameEvent)
    {
        return gameEvent == GameEvent.EXPLODE || gameEvent == GameEvent.LIGHTNING_STRIKE
                || gameEvent == GameEvent.ENTITY_DIE || gameEvent == GameEvent.ENTITY_ROAR
                || gameEvent == GameEvent.INSTRUMENT_PLAY;
    }

    /**
     * Vanilla's wool occlusion, one center-to-center clip against blocks that occlude
     * vibration signals. (Vanilla retries six epsilon-offset lines and only calls it
     * occluded if all block; the single line is marginally stricter at wool edges, which
     * for fish is fine.)
     */
    private static boolean isOccluded(Level level, Vec3 eventPos, Vec3 listenerPos)
    {
        Vec3 from = new Vec3(Math.floor(eventPos.x) + 0.5D, Math.floor(eventPos.y) + 0.5D,
                Math.floor(eventPos.z) + 0.5D);
        Vec3 to = new Vec3(Math.floor(listenerPos.x) + 0.5D, Math.floor(listenerPos.y) + 0.5D,
                Math.floor(listenerPos.z) + 0.5D);
        return level.isBlockInLine(new ClipBlockStateContext(from, to,
                state -> state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))).getType() == HitResult.Type.BLOCK;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        LAST_HEARD.clear();
    }
}
