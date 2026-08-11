package net.camacraft.nicecatch.registry;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.menu.FishTrapMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus
{
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, NiceCatch.MODID);

    public static final RegistryObject<MenuType<FishTrapMenu>> FISH_TRAP =
            MENUS.register("fish_trap", () -> new MenuType<>(FishTrapMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}

    public static void register(IEventBus modBus)
    {
        MENUS.register(modBus);
    }
}
