package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Individual fish sizes and the weight system built on them. No two salmon are the same
 * anymore: every fish rolls a body scale from its UUID (plus any giant/dwarf trait), the
 * scale is applied to its real hitbox, and weight is simply the scaled hitbox volume times
 * a density factor — so a small salmon genuinely is a lighter, weaker fight and a big one
 * is a heavier, harder one, because everything downstream (fight strength, the net's odds,
 * predator/prey size checks) already reads the live hitbox.
 *
 * The scale is a pure function of the UUID: server and client compute the same number with
 * no sync packet, it persists across save/load for free, and the same fish keeps its size
 * through catch-and-release. The hitbox is applied through Forge's entity-size event (both
 * sides); the model is scaled to match in {@code FishPitchRenderer}.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public final class FishSizing
{
    private static final long SIZE_SALT = 0x51F1_5CA1E5L;

    private FishSizing() {}

    /**
     * This fish's body scale: UUID roll inside its size band, times traits. The band is the
     * global weight.sizeVariance range unless the species' profile overrides it (size_min /
     * size_max in fishProfiles) — which also works with global variance off, so specific
     * species can be resized or opted out either way.
     */
    public static float scaleOf(Entity fish)
    {
        if (!NiceCatchConfig.SERVER_SPEC.isLoaded()) return 1.0F;
        // Fixed-dimension types refuse EntityDimensions.scale(), so their hitbox could never
        // follow — report 1 so the rendered model stays honest about the real hitbox.
        if (fish.getType().getDimensions().fixed) return 1.0F;
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        FishProfiles.FishProfile profile = FishProfiles.of(fish.getType());
        boolean hasOverride = !Float.isNaN(profile.sizeMin) || !Float.isNaN(profile.sizeMax);
        float base = 1.0F;
        if (cfg.sizeVarianceEnabled.get() || hasOverride) {
            float min = !Float.isNaN(profile.sizeMin) ? profile.sizeMin
                    : cfg.sizeVarianceMin.get().floatValue();
            float max = Math.max(min, !Float.isNaN(profile.sizeMax) ? profile.sizeMax
                    : cfg.sizeVarianceMax.get().floatValue());
            // Two independent rolls averaged: sizes cluster around the middle of the band,
            // with the extremes rare enough to be worth mentioning to a friend.
            long h = FishTraits.mix(fish.getUUID(), SIZE_SALT);
            float a = ((h >>> 16) & 0xFFFF) / 65535.0F;
            float b = ((h >>> 40) & 0xFFFF) / 65535.0F;
            base = Mth.lerp((a + b) * 0.5F, min, max);
        }
        return base * FishTraits.of(fish).scale();
    }

    /** Weight in kilograms: scaled hitbox volume x density config x trait density. */
    public static float weightKg(PathfinderMob fish)
    {
        double volume = fish.getBbWidth() * fish.getBbWidth() * fish.getBbHeight();
        return (float) (volume * NiceCatchConfig.SERVER.weightPerVolume.get())
                * FishTraits.of(fish).weight();
    }

    private static final float KG_TO_LBS = 2.20462F;

    /** The stored (metric) weight converted to the configured display unit. */
    public static float displayWeight(float kg)
    {
        if (!NiceCatchConfig.SERVER_SPEC.isLoaded()) return kg;
        return NiceCatchConfig.SERVER.weightUnit.get() == NiceCatchConfig.WeightUnit.LBS
                ? kg * KG_TO_LBS : kg;
    }

    /** The display unit's label ("lbs"/"kg"), translatable. */
    public static net.minecraft.network.chat.Component unitLabel()
    {
        boolean lbs = NiceCatchConfig.SERVER_SPEC.isLoaded()
                && NiceCatchConfig.SERVER.weightUnit.get() == NiceCatchConfig.WeightUnit.LBS;
        return net.minecraft.network.chat.Component.translatable(lbs ? "nicecatch.unit.lbs" : "nicecatch.unit.kg");
    }

    /**
     * "4.2" / "0.35" / "12" in the configured display unit — enough precision to brag
     * with, no more. Pass the stored (kg) value; conversion happens here.
     */
    public static String formatWeight(float kg)
    {
        float v = displayWeight(kg);
        if (v >= 10.0F) return String.format(java.util.Locale.ROOT, "%.0f", v);
        if (v >= 1.0F) return String.format(java.util.Locale.ROOT, "%.1f", v);
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /**
     * Applies the scale to the hitbox. Fires from the entity ctor and every refreshDimensions.
     * EntityEvent.Size is deprecated-for-removal as a forward-port marker, but on Forge 1.20.1
     * it is live (fired from Entity's ctor and refreshDimensions) and is the only hook that can
     * resize entities we don't own.
     */
    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event)
    {
        Entity entity = event.getEntity();
        if (!FishBehavior.isFishKind(entity)) return;
        float scale = scaleOf(entity);
        if (Math.abs(scale - 1.0F) < 0.001F) return;
        event.setNewSize(event.getNewSize().scale(scale));
        event.setNewEyeHeight(event.getNewEyeHeight() * scale);
    }

    /**
     * The size event first fires during construction, when the UUID is still the ctor's
     * placeholder (loading from disk and client spawn packets both overwrite it afterward).
     * Re-derive dimensions once the entity actually joins a level — both sides — so the
     * hitbox always reflects the final UUID.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if (FishBehavior.isFishKind(event.getEntity())) {
            event.getEntity().refreshDimensions();
        }
    }
}
