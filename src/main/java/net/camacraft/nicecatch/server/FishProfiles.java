package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-species behavioral profiles: one place that answers "what kind of fish is this?" for
 * every system in the mod — approach and bite behavior, fight strength and stamina, food
 * attraction, predation, and habitat preference.
 *
 * A profile is derived from the species' hitbox size (small fish are bold, weak and quick to
 * tire; big fish are wary, strong and fond of cover), then overridden by any matching entry in
 * the fishProfiles config list, so both vanilla tweaks and modded species can be tuned without
 * touching code. Profiles are immutable once built and memoized per entity type; the cache is
 * dropped whenever the config reloads.
 */
public final class FishProfiles
{
    private FishProfiles() {}

    /** Where a species prefers to spend its idle time. */
    public enum Depth
    {
        /** Near the surface. */
        SURFACE,
        /** Mid-water, away from structure. */
        OPEN,
        /** Hugging the floor. */
        BOTTOM;

        static Depth parse(String s, Depth fallback)
        {
            for (Depth d : values()) {
                if (d.name().equalsIgnoreCase(s)) return d;
            }
            return fallback;
        }
    }

    /** One species' disposition. All multipliers are 1.0 = the ordinary fish. */
    public static final class FishProfile
    {
        /** 0..1: willingness to approach bobbers/food and to shrug off a looming player. */
        public float boldness = 0.5F;
        /** Multiplier on how fast bobber interest accumulates. */
        public float curiosity = 1.0F;
        /** Multiplier on nibble-to-bite conversion and bite pick weight. */
        public float biteBias = 1.0F;
        /** Multiplier on the size-derived fight strength. */
        public float strength = 1.0F;
        /** Multiplier on stamina: >1 tires slower in the fight, recovers faster. */
        public float stamina = 1.0F;
        /** Multiplier on attraction to food thrown into the water. */
        public float foodAffinity = 1.0F;
        /** Hunts much smaller fish. */
        public boolean predator = false;
        /** Minimum (own hitbox area / prey hitbox area) for a fish to count as prey. */
        public float preyRatio = 2.5F;
        /** Preferred idle depth band. */
        public Depth depth = Depth.OPEN;
        /** 0..1: fondness for lurking in kelp/seagrass cover. */
        public float cover = 0.0F;
        /**
         * Species size band overriding the global weight.sizeVariance range (NaN = use the
         * global band). Lets undersized species like Aquaculture's tuna grow to trophy size,
         * and setting both to 1 opts a species out of size variance entirely.
         */
        public float sizeMin = Float.NaN;
        public float sizeMax = Float.NaN;
    }

    private static final Map<EntityType<?>, FishProfile> CACHE = new ConcurrentHashMap<>();

    public static FishProfile of(PathfinderMob fish)
    {
        return of(fish.getType());
    }

    public static FishProfile of(EntityType<?> type)
    {
        return CACHE.computeIfAbsent(type, FishProfiles::build);
    }

    /** Called when the config reloads; profiles are rebuilt lazily from the new values. */
    public static void clearCache()
    {
        CACHE.clear();
    }

    private static FishProfile build(EntityType<?> type)
    {
        FishProfile p = baseBySize(type.getWidth() * type.getHeight());
        var id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return p;

        String idString = id.toString();
        List<? extends String> entries = NiceCatchConfig.SERVER.fishProfiles.get();
        for (String raw : entries) {
            int eq = raw.indexOf('=');
            if (eq <= 0) continue;
            if (!raw.substring(0, eq).trim().equals(idString)) continue;
            applyOverrides(p, raw.substring(eq + 1));
        }
        return p;
    }

    /**
     * Size sets the archetype: darting little schoolers, ordinary mid-size fish, and big wary
     * lurkers. Any species the config doesn't mention still lands on something sensible.
     */
    private static FishProfile baseBySize(float area)
    {
        FishProfile p = new FishProfile();
        if (area < 0.18F) {
            p.boldness = 0.68F;
            p.curiosity = 1.2F;
            p.strength = 0.85F;
            p.stamina = 0.8F;
            p.foodAffinity = 1.2F;
        } else if (area > 0.35F) {
            p.boldness = 0.32F;
            p.curiosity = 0.85F;
            p.strength = 1.15F;
            p.stamina = 1.3F;
            p.foodAffinity = 0.9F;
            p.depth = Depth.BOTTOM;
            p.cover = 0.6F;
        }
        return p;
    }

    /** Trait overrides: "boldness:0.4,strength:1.2,predator:true,depth:bottom,cover:0.5". */
    private static void applyOverrides(FishProfile p, String traits)
    {
        for (String pair : traits.split(",")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) continue;
            String key = pair.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = pair.substring(colon + 1).trim();
            try {
                switch (key) {
                    case "boldness" -> p.boldness = Mth.clamp(Float.parseFloat(value), 0.0F, 1.0F);
                    case "curiosity" -> p.curiosity = clampMult(value);
                    case "bite" -> p.biteBias = clampMult(value);
                    case "strength" -> p.strength = clampMult(value);
                    case "stamina" -> p.stamina = clampMult(value);
                    case "food" -> p.foodAffinity = clampMult(value);
                    case "predator" -> p.predator = Boolean.parseBoolean(value);
                    case "prey_ratio" -> p.preyRatio = Mth.clamp(Float.parseFloat(value), 1.2F, 100.0F);
                    case "depth" -> p.depth = Depth.parse(value, p.depth);
                    case "cover" -> p.cover = Mth.clamp(Float.parseFloat(value), 0.0F, 1.0F);
                    case "size_min" -> p.sizeMin = Mth.clamp(Float.parseFloat(value), 0.2F, 4.0F);
                    case "size_max" -> p.sizeMax = Mth.clamp(Float.parseFloat(value), 0.2F, 4.0F);
                }
            } catch (NumberFormatException ignored) {
                // A malformed number leaves that trait at its default; better than dying on config.
            }
        }
    }

    private static float clampMult(String value)
    {
        return Mth.clamp(Float.parseFloat(value), 0.1F, 5.0F);
    }
}
