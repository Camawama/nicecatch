package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Fish traits: named quirks a fish can be born with, from "puts up no fight" to "trails
 * other skies behind it". A trait bundles stat modifiers (fight strength, stamina, line
 * stress, thrash amplitude, run force, charge appetite, bite eagerness, fear, hook-set
 * window, body scale, weight density, bonus XP, double-catch) plus an optional particle
 * aura, a rarity weight, and a display color; the catch item carries its fish's traits in
 * the tooltip.
 *
 * Assignment is derived deterministically from the fish's UUID (a different hash salt than
 * the size roll), so the server and client always agree with zero networking, it persists
 * for free, and the same fish keeps its traits through catch-and-release. Adding a trait is
 * one {@code register(...)} line plus two lang keys — mod-mates welcome.
 */
public final class FishTraits
{
    private FishTraits() {}

    /** One trait definition. All multipliers default to 1 (no effect). */
    public static final class FishTrait
    {
        public final String id;
        /** Rarity weight in the trait lottery; higher = more common. */
        public final int rarity;
        public final ChatFormatting color;
        public float strength = 1.0F;
        public float stamina = 1.0F;
        /** Line stress: >1 builds tension faster (snappier line). */
        public float tension = 1.0F;
        /** Thrash amplitude while sweeping. */
        public float sweep = 1.0F;
        /** Force of its runs (pull/sound/charge). */
        public float runForce = 1.0F;
        /** Weighting toward picking the Charge tactic. */
        public float chargeBias = 1.0F;
        /** Bite eagerness (nibble conversion + who-bites weighting). */
        public float bite = 1.0F;
        /** Scare-chance multiplier: >1 spooks easier. */
        public float fear = 1.0F;
        /** Hook-set window multiplier: <1 must be answered faster. */
        public float biteWindow = 1.0F;
        /** Body scale multiplier on top of the ordinary size roll. */
        public float scale = 1.0F;
        /** Weight density multiplier (same size, different heft). */
        public float weight = 1.0F;
        public int xpBonus = 0;
        public float doubleCatch = 0.0F;
        @Nullable public SimpleParticleType aura;

        FishTrait(String id, int rarity, ChatFormatting color)
        {
            this.id = id;
            this.rarity = rarity;
            this.color = color;
        }

        FishTrait strength(float v) { strength = v; return this; }
        FishTrait stamina(float v) { stamina = v; return this; }
        FishTrait tension(float v) { tension = v; return this; }
        FishTrait sweep(float v) { sweep = v; return this; }
        FishTrait runForce(float v) { runForce = v; return this; }
        FishTrait chargeBias(float v) { chargeBias = v; return this; }
        FishTrait bite(float v) { bite = v; return this; }
        FishTrait fear(float v) { fear = v; return this; }
        FishTrait biteWindow(float v) { biteWindow = v; return this; }
        FishTrait scale(float v) { scale = v; return this; }
        FishTrait weight(float v) { weight = v; return this; }
        FishTrait xpBonus(int v) { xpBonus = v; return this; }
        FishTrait doubleCatch(float v) { doubleCatch = v; return this; }
        FishTrait aura(SimpleParticleType v) { aura = v; return this; }

        public String nameKey()
        {
            return "nicecatch.trait." + id;
        }

        public String descKey()
        {
            return "nicecatch.trait." + id + ".desc";
        }
    }

    /** A fish's traits with every modifier pre-multiplied, cached per entity. */
    public record Modifiers(List<FishTrait> traits, float strength, float stamina, float tension,
                            float sweep, float runForce, float chargeBias, float bite, float fear,
                            float biteWindow, float scale, float weight, int xpBonus,
                            float doubleCatch, @Nullable SimpleParticleType aura)
    {
        public boolean isEmpty()
        {
            return traits.isEmpty();
        }
    }

    private static final Modifiers NONE = new Modifiers(List.of(),
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, null);

    private static final Map<String, FishTrait> REGISTRY = new LinkedHashMap<>();
    /** Both sides read this (render scale client, everything else server) — synchronized. */
    private static final Map<Entity, Modifiers> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final long TRAIT_SALT = 0x7A175A17F15EL;

    /** Extension point: register a trait before any fish of interest spawns (mod init is fine). */
    public static FishTrait register(FishTrait trait)
    {
        REGISTRY.put(trait.id, trait);
        return trait;
    }

    @Nullable
    public static FishTrait byId(String id)
    {
        return REGISTRY.get(id);
    }

    static {
        // Fight character
        register(new FishTrait("feisty", 10, ChatFormatting.GOLD).sweep(1.6F));
        register(new FishTrait("tough", 10, ChatFormatting.RED).tension(1.4F));
        register(new FishTrait("mighty", 8, ChatFormatting.DARK_RED).strength(1.25F));
        register(new FishTrait("frail", 8, ChatFormatting.GRAY).strength(0.72F));
        register(new FishTrait("tireless", 8, ChatFormatting.AQUA).stamina(1.5F));
        register(new FishTrait("lazy", 8, ChatFormatting.GREEN).stamina(0.6F));
        register(new FishTrait("swift", 8, ChatFormatting.YELLOW).runForce(1.3F));
        register(new FishTrait("sluggish", 8, ChatFormatting.GRAY).runForce(0.75F));
        register(new FishTrait("cunning", 6, ChatFormatting.LIGHT_PURPLE).chargeBias(2.2F));
        // Temperament
        register(new FishTrait("slippery", 6, ChatFormatting.BLUE).biteWindow(0.6F));
        register(new FishTrait("greedy", 8, ChatFormatting.GOLD).bite(1.8F));
        register(new FishTrait("timid", 8, ChatFormatting.WHITE).fear(1.5F));
        register(new FishTrait("bold", 8, ChatFormatting.YELLOW).fear(0.55F));
        // Body
        register(new FishTrait("giant", 5, ChatFormatting.DARK_GREEN).scale(1.3F));
        register(new FishTrait("dwarf", 5, ChatFormatting.GREEN).scale(0.72F));
        register(new FishTrait("dense", 6, ChatFormatting.DARK_GRAY).weight(1.5F));
        register(new FishTrait("featherweight", 6, ChatFormatting.WHITE).weight(0.6F));
        // Fortune
        register(new FishTrait("lucky", 4, ChatFormatting.GREEN).xpBonus(5).doubleCatch(0.15F));
        // Aura rarities — the special ones
        register(new FishTrait("verdant", 3, ChatFormatting.GREEN)
                .aura(ParticleTypes.HAPPY_VILLAGER).stamina(1.15F));
        register(new FishTrait("glimmering", 3, ChatFormatting.YELLOW)
                .aura(ParticleTypes.GLOW));
        register(new FishTrait("molten", 3, ChatFormatting.RED)
                .aura(ParticleTypes.LAVA).tension(1.25F));
        register(new FishTrait("ghostly", 2, ChatFormatting.DARK_AQUA)
                .aura(ParticleTypes.SOUL).fear(0.4F));
        register(new FishTrait("abyssal", 2, ChatFormatting.DARK_BLUE)
                .aura(ParticleTypes.SCULK_SOUL).strength(1.2F).stamina(1.25F));
        register(new FishTrait("cosmic", 2, ChatFormatting.LIGHT_PURPLE)
                .aura(ParticleTypes.PORTAL).strength(1.15F).stamina(1.2F).weight(1.2F));
        register(new FishTrait("ancient", 1, ChatFormatting.GOLD)
                .aura(ParticleTypes.ENCHANT).strength(1.3F).stamina(1.4F).weight(1.3F).xpBonus(10));
    }

    // ---- Assignment & lookup ----

    public static Modifiers of(Entity fish)
    {
        Modifiers cached = CACHE.get(fish);
        if (cached != null) return cached;
        // Config not in yet (very early entity construction): answer neutrally, don't cache.
        if (!NiceCatchConfig.SERVER_SPEC.isLoaded()) return NONE;
        Modifiers built = build(fish.getUUID());
        CACHE.put(fish, built);
        return built;
    }

    /** Config reloads change the trait lottery; recompute lazily. */
    public static void clearCache()
    {
        CACHE.clear();
    }

    /**
     * The aura carried by a caught-fish item's NBT (first aura trait wins), or null. Lets
     * displayed fish — Aquaculture wall mounts, and whatever else shows a fish item — keep
     * shimmering with the trait they were caught with.
     */
    @Nullable
    public static SimpleParticleType auraFromStack(net.minecraft.world.item.ItemStack stack)
    {
        net.minecraft.nbt.CompoundTag info = stack.getTagElement("NiceCatch");
        if (info == null || !info.contains("Traits", net.minecraft.nbt.Tag.TAG_LIST)) return null;
        net.minecraft.nbt.ListTag list = info.getList("Traits", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            FishTrait trait = REGISTRY.get(list.getString(i));
            if (trait != null && trait.aura != null) return trait.aura;
        }
        return null;
    }

    /** Rebuild traits from a list of ids (for tooltips off item NBT). Unknown ids are skipped. */
    public static List<FishTrait> fromIds(List<String> ids)
    {
        List<FishTrait> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            FishTrait t = REGISTRY.get(id);
            if (t != null) out.add(t);
        }
        return out;
    }

    private static Modifiers build(UUID id)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        long seed = mix(id, TRAIT_SALT);
        Random random = new Random(seed);
        if (random.nextFloat() >= cfg.traitChance.get().floatValue()) return NONE;

        List<FishTrait> picked = new ArrayList<>(2);
        picked.add(pickWeighted(random));
        if (random.nextFloat() < cfg.doubleTraitChance.get().floatValue()) {
            FishTrait second = pickWeighted(random);
            if (second != picked.get(0)) picked.add(second);
        }

        float strength = 1, stamina = 1, tension = 1, sweep = 1, runForce = 1, chargeBias = 1;
        float bite = 1, fear = 1, biteWindow = 1, scale = 1, weight = 1, doubleCatch = 0;
        int xpBonus = 0;
        SimpleParticleType aura = null;
        for (FishTrait t : picked) {
            strength *= t.strength;
            stamina *= t.stamina;
            tension *= t.tension;
            sweep *= t.sweep;
            runForce *= t.runForce;
            chargeBias *= t.chargeBias;
            bite *= t.bite;
            fear *= t.fear;
            biteWindow *= t.biteWindow;
            scale *= t.scale;
            weight *= t.weight;
            xpBonus += t.xpBonus;
            doubleCatch += t.doubleCatch;
            if (t.aura != null) aura = t.aura;
        }
        return new Modifiers(List.copyOf(picked), strength, stamina, tension, sweep, runForce,
                chargeBias, bite, fear, biteWindow, scale, weight, xpBonus, doubleCatch, aura);
    }

    private static FishTrait pickWeighted(Random random)
    {
        int total = 0;
        for (FishTrait t : REGISTRY.values()) total += t.rarity;
        int roll = random.nextInt(Math.max(1, total));
        for (FishTrait t : REGISTRY.values()) {
            roll -= t.rarity;
            if (roll < 0) return t;
        }
        return REGISTRY.values().iterator().next();
    }

    /** Stable hash of a UUID with a salt; size and traits use different salts. */
    public static long mix(UUID id, long salt)
    {
        long h = (id.getMostSignificantBits() * 0x9E3779B97F4A7C15L) ^ (id.getLeastSignificantBits() + salt);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /** Ambient sparkle for aura traits; called from the fish tick, staggered and cheap. */
    public static void tickAura(ServerLevel level, PathfinderMob fish)
    {
        if (!NiceCatchConfig.SERVER.traitAurasEnabled.get()) return;
        if ((fish.tickCount + fish.getId()) % 8 != 0) return;
        SimpleParticleType aura = of(fish).aura();
        if (aura == null) return;
        level.sendParticles(aura,
                fish.getX(), fish.getY() + fish.getBbHeight() * 0.6D, fish.getZ(),
                2, fish.getBbWidth() * 0.4D, fish.getBbHeight() * 0.3D, fish.getBbWidth() * 0.4D, 0.005D);
    }
}
