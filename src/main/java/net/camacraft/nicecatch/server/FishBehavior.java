package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.RodUtil;
import net.camacraft.nicecatch.server.goal.FollowBobberGoal;
import net.camacraft.nicecatch.server.goal.HookedFishGoal;
import net.camacraft.nicecatch.server.goal.ScatterGoal;
import net.camacraft.nicecatch.server.goal.SchoolBoidsGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FollowFlockLeaderGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side brain for how fish react to bobbers and threats.
 *
 * State lives in a WeakHashMap keyed by the fish entity itself, so it evaporates with the
 * entity on unload; everything is re-derived from live entity references, which keeps the
 * system safe with any number of players fishing side by side.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public final class FishBehavior
{
    private static final Map<AbstractFish, FishState> STATES = new WeakHashMap<>();

    public static class FishState
    {
        public boolean goalsInjected;
        /** The bobber this fish is drawn to (interest claim). */
        @Nullable public FishingHook bobber;
        /** Non-null while the fish is actively nibbling at that bobber. */
        @Nullable public FishingHook biteBobber;
        public boolean hooked;
        public long scatterUntil;
        @Nullable public Vec3 scatterFrom;
        public long biteCooldownUntil;
        /**
         * 0..1 curiosity about bobbers: grows every time this fish notices one (faster with
         * Aquaculture bait), grows a little more with each nibble, and drops when spooked.
         * High interest means quicker approaches and much more frequent bites.
         */
        public float interest;
        public long nibbleCooldownUntil;
    }

    public static FishState state(AbstractFish fish)
    {
        return STATES.computeIfAbsent(fish, f -> new FishState());
    }

    // ---- Blacklist ----

    /** Memoized per entity type; config lookups and wildcard matching are too hot for per-tick checks. */
    private static final Map<net.minecraft.world.entity.EntityType<?>, Boolean> BLACKLIST_CACHE
            = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Not every AbstractFish is a fish we should puppet: Aquaculture's jellyfish extends it,
     * and other mods' oddballs may too. Blacklisted types keep their vanilla AI and are
     * invisible to the whole system — never lured, spooked, schooled, clamped, or hooked,
     * and they don't count as "fish nearby" for loot suppression either.
     */
    public static boolean isBlacklisted(net.minecraft.world.entity.EntityType<?> type)
    {
        return BLACKLIST_CACHE.computeIfAbsent(type, t -> {
            var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(t);
            if (id == null) return false;
            for (String raw : NiceCatchConfig.SERVER.fishAiBlacklist.get()) {
                String entry = raw.trim();
                if (entry.equals(id.toString())) return true;
                if (entry.endsWith(":*") && id.getNamespace().equals(entry.substring(0, entry.length() - 2))) {
                    return true;
                }
            }
            return false;
        });
    }

    /** The cache must not outlive the config values it was computed from. */
    @Mod.EventBusSubscriber(modid = NiceCatch.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConfigListener
    {
        @SubscribeEvent
        public static void onConfigChanged(net.minecraftforge.fml.event.config.ModConfigEvent event)
        {
            if (NiceCatch.MODID.equals(event.getConfig().getModId())) {
                BLACKLIST_CACHE.clear();
            }
        }
    }

    // ---- Goal injection ----

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof AbstractFish fish)) return;
        if (isBlacklisted(fish.getType())) return; // jellyfish and friends keep their own AI

        FishState state = state(fish);
        if (state.goalsInjected) return;
        state.goalsInjected = true;

        // Vanilla fish flee any player within 8 blocks, which would keep them from ever
        // approaching a bobber cast near its owner, and their panic is a slow pathfinding
        // crawl. Our scatter system replaces both with a meaner, faster, situational fear
        // (close players, swimmers, attacks, damage), so drop the vanilla goals. The boids
        // school likewise supersedes vanilla's pathfinding flock-follow when enabled.
        boolean boids = NiceCatchConfig.SERVER.boidSchoolingEnabled.get();
        fish.goalSelector.removeAllGoals(g -> g instanceof AvoidEntityGoal || g instanceof PanicGoal
                || (boids && g instanceof FollowFlockLeaderGoal));
        fish.goalSelector.addGoal(0, new HookedFishGoal(fish));
        fish.goalSelector.addGoal(1, new ScatterGoal(fish));
        fish.goalSelector.addGoal(2, new FollowBobberGoal(fish));
        fish.goalSelector.addGoal(3, new SchoolBoidsGoal(fish));
    }

    /** Swinging at any fish spooks the school; melee fishing should feel nearly hopeless. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event)
    {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getTarget() instanceof AbstractFish target)) return;
        if (isHooked(target) || isBlacklisted(target.getType())) return;

        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        scatter(target, event.getEntity().position(), cfg.scatterDurationTicks.get());
        scatterAround((ServerLevel) target.level(), target.position(), cfg.scatterRadius.get(),
                cfg.meleeScatterChance.get().floatValue(), target);
    }

    /**
     * Fish never break the surface: if the top of a fish would poke above the actual water
     * plane, it gets eased back under. Runs for every fish (vanilla wander included) —
     * except a hooked one, whose catch launch out of the water must not be suppressed.
     */
    @SubscribeEvent
    public static void onFishTick(LivingEvent.LivingTickEvent event)
    {
        if (!(event.getEntity() instanceof AbstractFish fish) || fish.level().isClientSide) return;
        if (!fish.isInWater() || isHooked(fish) || isBlacklisted(fish.getType())) return;

        BlockPos pos = fish.blockPosition();
        FluidState fluid = fish.level().getFluidState(pos);
        if (!fluid.is(FluidTags.WATER)) return;
        if (fish.level().getFluidState(pos.above()).is(FluidTags.WATER)) return; // deep water, fine

        double surfaceY = pos.getY() + fluid.getHeight(fish.level(), pos);
        if (fish.getY() + fish.getBbHeight() > surfaceY - 0.03D) {
            Vec3 v = fish.getDeltaMovement();
            fish.setDeltaMovement(v.x, Math.min(v.y, 0.0D) - 0.03D, v.z);
        }
    }

    /** Any damage (arrows, tridents, cacti, whatever) also sends the school running. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event)
    {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof AbstractFish fish) || isHooked(fish)) return;
        if (isBlacklisted(fish.getType())) return;

        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        Vec3 from = event.getSource().getSourcePosition() != null
                ? event.getSource().getSourcePosition() : fish.position();
        scatter(fish, from, cfg.scatterDurationTicks.get());
        scatterAround((ServerLevel) fish.level(), fish.position(), cfg.scatterRadius.get(),
                cfg.meleeScatterChance.get().floatValue(), fish);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        STATES.clear();
    }

    // ---- Queries ----

    /** A bobber attracts fish while it sits in water, its owner is online, and no fight is running on it. */
    public static boolean isAttracting(@Nullable FishingHook hook)
    {
        if (hook == null || !hook.isAlive() || !hook.isInWater()) return false;
        if (!(hook.getPlayerOwner() instanceof ServerPlayer owner) || owner.fishing != hook) return false;
        return !ServerFishingManager.isFighting(owner);
    }

    public static double attractRadius(FishingHook hook)
    {
        double radius = NiceCatchConfig.SERVER.interestRadius.get();
        Player owner = hook.getPlayerOwner();
        if (owner != null) {
            InteractionHand hand = RodUtil.findRodHand(owner);
            if (hand != null) {
                ItemStack rod = owner.getItemInHand(hand);
                radius += 1.5D * EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_SPEED, rod);
            }
        }
        return radius;
    }

    public static int claimedCount(FishingHook hook)
    {
        int count = 0;
        for (Map.Entry<AbstractFish, FishState> entry : STATES.entrySet()) {
            if (entry.getValue().bobber == hook && entry.getKey().isAlive()) count++;
        }
        return count;
    }

    /** Fish claimed by this bobber that are within the given range and allowed to bite. */
    public static List<AbstractFish> biteCandidates(FishingHook hook, double range)
    {
        long now = hook.level().getGameTime();
        List<AbstractFish> out = new ArrayList<>();
        for (Map.Entry<AbstractFish, FishState> entry : STATES.entrySet()) {
            AbstractFish fish = entry.getKey();
            FishState state = entry.getValue();
            if (state.bobber != hook || state.hooked || state.biteBobber != null) continue;
            if (!fish.isAlive() || isScattering(fish) || now < state.biteCooldownUntil) continue;
            if (fish.distanceToSqr(hook) > range * range) continue;
            out.add(fish);
        }
        return out;
    }

    public static boolean isScattering(AbstractFish fish)
    {
        FishState state = STATES.get(fish);
        return state != null && fish.level().getGameTime() < state.scatterUntil;
    }

    public static boolean isHooked(AbstractFish fish)
    {
        FishState state = STATES.get(fish);
        return state != null && state.hooked;
    }

    // ---- Mutations ----

    /** Ticks a lightly-spooked fish waits before biting again; proximity scares use this. */
    public static final int LIGHT_SCARE_COOLDOWN = 100;

    /** Full-cooldown scatter: for real trouble (damage, failed bites, thrown hooks). */
    public static void scatter(AbstractFish fish, Vec3 from, int durationTicks)
    {
        scatter(fish, from, durationTicks, NiceCatchConfig.SERVER.fishBiteCooldownTicks.get());
    }

    public static void scatter(AbstractFish fish, Vec3 from, int durationTicks, int biteCooldownTicks)
    {
        FishState state = state(fish);
        long now = fish.level().getGameTime();
        state.scatterUntil = now + durationTicks;
        state.scatterFrom = from;
        state.bobber = null;
        state.biteBobber = null;
        state.biteCooldownUntil = Math.max(state.biteCooldownUntil, now + biteCooldownTicks);
        state.interest = Math.max(0.0F, state.interest - 0.35F);
    }

    public static void scatterAround(ServerLevel level, Vec3 center, double radius, float chance, @Nullable AbstractFish except)
    {
        scatterAround(level, center, radius, chance, except, NiceCatchConfig.SERVER.fishBiteCooldownTicks.get());
    }

    public static void scatterAround(ServerLevel level, Vec3 center, double radius, float chance,
                                     @Nullable AbstractFish except, int biteCooldownTicks)
    {
        AABB box = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (AbstractFish fish : level.getEntitiesOfClass(AbstractFish.class, box,
                f -> f != except && !isHooked(f) && !isBlacklisted(f.getType()))) {
            if (level.random.nextFloat() < chance) {
                scatter(fish, center, NiceCatchConfig.SERVER.scatterDurationTicks.get(), biteCooldownTicks);
            }
        }
    }

    public static void setHooked(AbstractFish fish, boolean hooked)
    {
        FishState state = state(fish);
        state.hooked = hooked;
        state.biteBobber = null;
        if (!hooked) state.bobber = null;
    }

    /** Any live fish near the bobber at all? Controls whether loot-table fishing is available. */
    public static boolean anyFishNear(FishingHook hook)
    {
        double radius = attractRadius(hook);
        AABB box = hook.getBoundingBox().inflate(radius);
        // Blacklisted types must not suppress loot fishing — a jellyfish will never bite.
        return !hook.level().getEntitiesOfClass(AbstractFish.class, box,
                f -> f.isAlive() && f.isInWater() && !isBlacklisted(f.getType())).isEmpty();
    }

    /** How fast this bobber grows fish interest (Aquaculture bait speeds it up). */
    public static float interestGainMultiplier(FishingHook hook)
    {
        Player owner = hook.getPlayerOwner();
        if (owner == null) return 1.0F;
        InteractionHand hand = RodUtil.findRodHand(owner);
        if (hand == null) return 1.0F;
        return net.camacraft.nicecatch.compat.AquacultureCompat.interestGainMultiplier(owner.getItemInHand(hand));
    }

    /** Fish swim way faster now, and vanilla's swim sound at that speed is a racket — hush it. */
    @SubscribeEvent
    public static void onFishSwimSound(net.minecraftforge.event.PlayLevelSoundEvent.AtEntity event)
    {
        if (!(event.getEntity() instanceof AbstractFish fish) || isBlacklisted(fish.getType())) return;
        var sound = event.getSound();
        if (sound != null && sound.value().getLocation().equals(
                net.minecraft.sounds.SoundEvents.FISH_SWIM.getLocation())) {
            event.setNewVolume(event.getNewVolume() * 0.25F);
        }
    }

    /** A scare source: where it is, and whether it's the always-flee kind (a swimmer). */
    public record Threat(Vec3 pos, boolean certain) {}

    /**
     * Something worth fleeing from: any non-fish entity splashing about in the water nearby
     * (a certain scare — every fish bolts from a swimmer), or a player closing to melee reach
     * (in the water or not) who is moving or mid-swing (chance-based scare). A fisher standing
     * still on the shore never trips this.
     *
     * Movement is measured by last-tick position delta, not getDeltaMovement(): server-side
     * velocity is stale for players, whose motion arrives via packets.
     */
    @Nullable
    public static Threat findThreat(AbstractFish fish)
    {
        double swimRadius = NiceCatchConfig.SERVER.swimScareRadius.get();
        double meleeRadius = NiceCatchConfig.SERVER.meleeThreatRadius.get();
        double radius = Math.max(swimRadius, meleeRadius);
        AABB box = fish.getBoundingBox().inflate(radius);
        for (LivingEntity e : fish.level().getEntitiesOfClass(LivingEntity.class, box,
                other -> other != fish && !(other instanceof AbstractFish) && !other.isSpectator())) {
            double dx = e.getX() - e.xOld;
            double dy = e.getY() - e.yOld;
            double dz = e.getZ() - e.zOld;
            // Vertical motion counts too — a player diving straight down is very much moving.
            boolean moving = dx * dx + dy * dy + dz * dz > 4.0E-4D;
            if (e.isInWater() && moving && fish.distanceToSqr(e) <= swimRadius * swimRadius) {
                return new Threat(e.position(), true);
            }
            if (e instanceof Player player && fish.distanceToSqr(player) <= meleeRadius * meleeRadius
                    && (moving || player.swinging)) {
                return new Threat(player.position(), false);
            }
        }
        return null;
    }

    /** Nearby fish already interested in a valid bobber; used for follow-the-follower spreading. */
    @Nullable
    public static FishingHook findFollowableBobber(AbstractFish fish)
    {
        double radius = NiceCatchConfig.SERVER.followFollowerRadius.get();
        AABB box = fish.getBoundingBox().inflate(radius);
        for (AbstractFish other : fish.level().getEntitiesOfClass(AbstractFish.class, box, f -> f != fish)) {
            FishState state = STATES.get(other);
            if (state == null || state.bobber == null || state.hooked) continue;
            if (isAttracting(state.bobber)
                    && claimedCount(state.bobber) < NiceCatchConfig.SERVER.maxFishPerBobber.get()) {
                return state.bobber;
            }
        }
        return null;
    }

    /** Nearest bobber this fish could take an interest in on its own. */
    @Nullable
    public static FishingHook findNearbyBobber(AbstractFish fish)
    {
        double maxRadius = NiceCatchConfig.SERVER.interestRadius.get() + 8.0D; // upper bound incl. Lure bonus
        AABB box = fish.getBoundingBox().inflate(maxRadius);
        FishingHook best = null;
        double bestDist = Double.MAX_VALUE;
        for (FishingHook hook : fish.level().getEntitiesOfClass(FishingHook.class, box)) {
            if (!isAttracting(hook)) continue;
            double dist = fish.distanceToSqr(hook);
            double radius = attractRadius(hook);
            if (dist > radius * radius) continue;
            if (claimedCount(hook) >= NiceCatchConfig.SERVER.maxFishPerBobber.get()) continue;
            if (dist < bestDist) {
                bestDist = dist;
                best = hook;
            }
        }
        return best;
    }
}
