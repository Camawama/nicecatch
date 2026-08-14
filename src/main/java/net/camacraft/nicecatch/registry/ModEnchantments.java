package net.camacraft.nicecatch.registry;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.enchant.RodEnchantment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The fishing-rod enchantment set:
 *  - Quick Reel I-III: every fish reels in faster (fight winch and plain retrieves both).
 *  - Long Cast I-III: the cast flies further.
 *  - Iron Line I-II: the line tolerates far more tension before snapping.
 *  - Sure Hook I-II: a biting fish stays on the hook longer before spitting it.
 *  - Alluring I-II: the bobber draws fish from further away.
 *  - Auto Attract I: the bobber twitches itself like a worked lure, exciting the
 *    movement-hunting species (lure_movement) with no reeling needed.
 */
public final class ModEnchantments
{
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, NiceCatch.MODID);

    public static final RegistryObject<Enchantment> QUICK_REEL =
            ENCHANTMENTS.register("quick_reel", () -> new RodEnchantment(Enchantment.Rarity.UNCOMMON, 3));
    public static final RegistryObject<Enchantment> LONG_CAST =
            ENCHANTMENTS.register("long_cast", () -> new RodEnchantment(Enchantment.Rarity.UNCOMMON, 3));
    public static final RegistryObject<Enchantment> IRON_LINE =
            ENCHANTMENTS.register("iron_line", () -> new RodEnchantment(Enchantment.Rarity.RARE, 2));
    public static final RegistryObject<Enchantment> SURE_HOOK =
            ENCHANTMENTS.register("sure_hook", () -> new RodEnchantment(Enchantment.Rarity.RARE, 2));
    public static final RegistryObject<Enchantment> ALLURING =
            ENCHANTMENTS.register("alluring", () -> new RodEnchantment(Enchantment.Rarity.UNCOMMON, 2));
    public static final RegistryObject<Enchantment> AUTO_ATTRACT =
            ENCHANTMENTS.register("auto_attract", () -> new RodEnchantment(Enchantment.Rarity.VERY_RARE, 1));

    private ModEnchantments() {}

    /** Level of one of our enchantments on this rod (0 = absent). */
    public static int level(ItemStack rod, RegistryObject<Enchantment> enchantment)
    {
        return rod.isEmpty() ? 0 : EnchantmentHelper.getItemEnchantmentLevel(enchantment.get(), rod);
    }

    public static void register(IEventBus modBus)
    {
        ENCHANTMENTS.register(modBus);
    }
}
