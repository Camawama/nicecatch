package net.camacraft.nicecatch.compat;

import com.mojang.logging.LogUtils;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Soft, reflection-based Aquaculture 2 integration; the mod stays an optional dependency.
 *
 * Aquaculture rods carry a hook and bait in their internal slots. We read them via
 * AquaFishingRodItem's static helpers: bait boosts bite chance, hooks slow tension build-up
 * (per-hook scales configurable), and the double hook can double the caught item.
 * Any reflection hiccup (API change, missing method) permanently disables the integration
 * for the session with a single log line — never a crash.
 */
public final class AquacultureCompat
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized;
    private static boolean active;
    private static Method getHookType;   // static AquaFishingRodItem.getHookType(ItemStack) -> Hook
    private static Method getBait;       // static AquaFishingRodItem.getBait(ItemStack) -> ItemStack
    private static Method hookGetName;   // Hook.getName() -> String

    private AquacultureCompat() {}

    private static void init()
    {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("aquaculture")) return;
        try {
            Class<?> rodClass = Class.forName("com.teammetallurgy.aquaculture.item.AquaFishingRodItem");
            getHookType = findMethod(rodClass, "getHookType", ItemStack.class);
            getBait = findMethod(rodClass, "getBait", ItemStack.class);
            Class<?> hookClass = Class.forName("com.teammetallurgy.aquaculture.api.fishing.Hook");
            hookGetName = findMethod(hookClass, "getName");
            active = getHookType != null && hookGetName != null;
            if (active) {
                LOGGER.info("Nice Catch: Aquaculture integration active (hooks and bait affect the fight).");
            } else {
                LOGGER.warn("Nice Catch: Aquaculture found but its rod API looks different; integration disabled.");
            }
        } catch (Throwable t) {
            LOGGER.warn("Nice Catch: failed to wire Aquaculture integration, disabling it.", t);
            active = false;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... args)
    {
        try {
            Method m = owner.getMethod(name, args);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Registry-style name of the hook on this rod ("heavy", "double", ...) or null. */
    @Nullable
    public static String hookName(ItemStack rod)
    {
        init();
        if (!active || rod.isEmpty()) return null;
        try {
            Object hook = getHookType.invoke(null, rod);
            if (hook == null) return null;
            Object name = hookGetName.invoke(hook);
            return name == null || name.toString().isEmpty() ? null : name.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    public static boolean hasBait(ItemStack rod)
    {
        init();
        if (!active || getBait == null || rod.isEmpty()) return false;
        try {
            Object bait = getBait.invoke(null, rod);
            return bait instanceof ItemStack stack && !stack.isEmpty();
        } catch (Throwable t) {
            disable(t);
            return false;
        }
    }

    public static float biteChanceMultiplier(ItemStack rod)
    {
        return hasBait(rod) ? NiceCatchConfig.SERVER.baitBiteMultiplier.get().floatValue() : 1.0F;
    }

    /** How much slower tension builds thanks to the equipped hook (1.0 = no hook). */
    public static float tensionScale(ItemStack rod)
    {
        String name = hookName(rod);
        if (name == null) return 1.0F;
        Float scale = hookScales().get(name);
        return scale != null ? scale : NiceCatchConfig.SERVER.defaultHookTensionScale.get().floatValue();
    }

    public static float doubleCatchChance(ItemStack rod)
    {
        return "double".equals(hookName(rod))
                ? NiceCatchConfig.SERVER.doubleHookDoubleChance.get().floatValue() : 0.0F;
    }

    private static Map<String, Float> hookScales()
    {
        Map<String, Float> map = new HashMap<>();
        for (String entry : NiceCatchConfig.SERVER.hookTensionScales.get()) {
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

    private static void disable(Throwable t)
    {
        if (active) {
            active = false;
            LOGGER.warn("Nice Catch: Aquaculture integration hit an error and has been disabled.", t);
        }
    }
}
