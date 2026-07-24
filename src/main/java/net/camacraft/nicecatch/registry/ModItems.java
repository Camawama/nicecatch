package net.camacraft.nicecatch.registry;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.item.LineArrowItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems
{
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NiceCatch.MODID);

    public static final RegistryObject<Item> LINE_ARROW =
            ITEMS.register("line_arrow", () -> new LineArrowItem(new Item.Properties()));

    private ModItems() {}

    public static void register(IEventBus modBus)
    {
        ITEMS.register(modBus);
    }
}
