package net.camacraft.nicecatch.compat;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full optional Aquaculture 2 integration, compiled against its API but only class-loaded
 * when the mod is present (everything Aquaculture-typed lives in the inner Impl class,
 * which the JVM never touches unless {@code LOADED} is true).
 *
 * What each piece of tackle does in the Nice Catch systems:
 *  - Bait (graded by its lure speed): fish interest grows faster and bites come sooner.
 *  - Hook: better hooks convert nibbles into real bites more often (per-hook config),
 *    carry Aquaculture's own luck modifier into bite selection and treasure rolls, use
 *    its real double-catch chance, and its durability chance can spare the rod on a catch.
 *  - Fishing line: equipped line slows tension build-up substantially (the line snaps later).
 *  - Rod tier: higher-tier rods reel more effectively and add further snap protection.
 */
public final class AquacultureCompat
{
    private static final boolean LOADED = ModList.get().isLoaded("aquaculture");

    private AquacultureCompat() {}

    public static boolean loaded()
    {
        return LOADED;
    }

    /** Multiplier on how fast fish interest grows while this rod's bobber is watched. */
    public static float interestGainMultiplier(ItemStack rod)
    {
        return LOADED ? Impl.baitMultiplier(rod, NiceCatchConfig.SERVER.baitInterestPerLureLevel.get().floatValue()) : 1.0F;
    }

    /** Multiplier on the per-tick bite roll. */
    public static float biteChanceMultiplier(ItemStack rod)
    {
        return LOADED ? Impl.baitMultiplier(rod, NiceCatchConfig.SERVER.baitBitePerLureLevel.get().floatValue()) : 1.0F;
    }

    /** Tension is divided by this: fishing line, sturdy hooks, and rod tier all slow the snap. */
    public static float tensionScale(ItemStack rod)
    {
        return LOADED ? Impl.tensionScale(rod) : 1.0F;
    }

    /** Multiplier on nibble-to-real-bite conversion; better hooks set themselves on a taste. */
    public static float nibbleBiteMultiplier(ItemStack rod)
    {
        return LOADED ? Impl.hookScale(rod, hookNibbleScales(), 1.0F) : 1.0F;
    }

    /** Aquaculture's own per-hook double catch chance (double hook etc.). */
    public static float doubleCatchChance(ItemStack rod)
    {
        return LOADED ? Impl.doubleCatchChance(rod) : 0.0F;
    }

    /** Extra luck levels granted by the equipped hook (gold, nether star...). */
    public static int bonusLuck(ItemStack rod)
    {
        return LOADED ? Impl.bonusLuck(rod) : 0;
    }

    /** Multiplier on crank progress from the rod's material tier. */
    public static float reelEffectiveness(ItemStack rod)
    {
        return LOADED ? Impl.reelEffectiveness(rod) : 1.0F;
    }

    /** True when the hook's durability bonus spares the rod this catch. */
    public static boolean skipRodDamage(ItemStack rod, RandomSource random)
    {
        return LOADED && Impl.skipRodDamage(rod, random);
    }

    private static Map<String, Float> hookNibbleScales()
    {
        return parseScales(NiceCatchConfig.SERVER.hookNibbleScales.get());
    }

    static Map<String, Float> parseScales(List<? extends String> entries)
    {
        Map<String, Float> map = new HashMap<>();
        for (String entry : entries) {
            int idx = entry.indexOf('=');
            if (idx <= 0) continue;
            try {
                map.put(entry.substring(0, idx).trim().toLowerCase(Locale.ROOT),
                        Float.parseFloat(entry.substring(idx + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return map;
    }

    /** Everything that touches Aquaculture classes; never class-loaded without the mod. */
    private static final class Impl
    {
        static float baitMultiplier(ItemStack rod, float perLureLevel)
        {
            if (!(rod.getItem() instanceof com.teammetallurgy.aquaculture.item.AquaFishingRodItem)) return 1.0F;
            ItemStack bait = com.teammetallurgy.aquaculture.item.AquaFishingRodItem.getBait(rod);
            if (bait.isEmpty()) return 1.0F;
            int lure = bait.getItem() instanceof com.teammetallurgy.aquaculture.api.bait.IBaitItem baitItem
                    ? Math.max(1, baitItem.getLureSpeedModifier()) : 1;
            return 1.0F + perLureLevel * lure;
        }

        static float tensionScale(ItemStack rod)
        {
            if (!(rod.getItem() instanceof com.teammetallurgy.aquaculture.item.AquaFishingRodItem rodItem)) return 1.0F;
            NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
            float scale = 1.0F;
            if (!com.teammetallurgy.aquaculture.item.AquaFishingRodItem.getFishingLine(rod).isEmpty()) {
                scale *= cfg.lineTensionScale.get().floatValue();
            }
            scale *= hookScale(rod, parseScales(cfg.hookTensionScales.get()),
                    cfg.defaultHookTensionScale.get().floatValue());
            scale *= 1.0F + cfg.rodTierTensionBonus.get().floatValue() * rodItem.getTier().getLevel();
            return scale;
        }

        static float hookScale(ItemStack rod, Map<String, Float> scales, float fallback)
        {
            String name = hookName(rod);
            if (name == null) return 1.0F;
            Float scale = scales.get(name);
            return scale != null ? scale : fallback;
        }

        static float doubleCatchChance(ItemStack rod)
        {
            com.teammetallurgy.aquaculture.api.fishing.Hook hook = hook(rod);
            return hook != null ? (float) hook.getDoubleCatchChance() : 0.0F;
        }

        static int bonusLuck(ItemStack rod)
        {
            com.teammetallurgy.aquaculture.api.fishing.Hook hook = hook(rod);
            return hook != null ? hook.getLuckModifier() : 0;
        }

        static float reelEffectiveness(ItemStack rod)
        {
            if (!(rod.getItem() instanceof com.teammetallurgy.aquaculture.item.AquaFishingRodItem rodItem)) return 1.0F;
            return 1.0F + NiceCatchConfig.SERVER.rodTierReelBonus.get().floatValue() * rodItem.getTier().getLevel();
        }

        static boolean skipRodDamage(ItemStack rod, RandomSource random)
        {
            com.teammetallurgy.aquaculture.api.fishing.Hook hook = hook(rod);
            return hook != null && hook.getDurabilityChance() > 0.0D
                    && random.nextDouble() < hook.getDurabilityChance();
        }

        @javax.annotation.Nullable
        private static com.teammetallurgy.aquaculture.api.fishing.Hook hook(ItemStack rod)
        {
            if (!(rod.getItem() instanceof com.teammetallurgy.aquaculture.item.AquaFishingRodItem)) return null;
            com.teammetallurgy.aquaculture.api.fishing.Hook hook =
                    com.teammetallurgy.aquaculture.item.AquaFishingRodItem.getHookType(rod);
            if (hook == null || hook == com.teammetallurgy.aquaculture.api.fishing.Hooks.EMPTY
                    || hook.getName() == null || hook.getName().isEmpty()) {
                return null;
            }
            return hook;
        }

        @javax.annotation.Nullable
        private static String hookName(ItemStack rod)
        {
            com.teammetallurgy.aquaculture.api.fishing.Hook hook = hook(rod);
            return hook != null ? hook.getName().toLowerCase(Locale.ROOT) : null;
        }
    }
}
