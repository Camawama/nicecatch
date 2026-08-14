package net.camacraft.nicecatch.registry;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.item.FishingNetItem;
import net.camacraft.nicecatch.item.LineArrowItem;
import net.minecraft.world.item.BlockItem;
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

    public static final RegistryObject<Item> FISHING_NET =
            ITEMS.register("fishing_net", () -> new FishingNetItem(new Item.Properties().durability(64)));

    public static final RegistryObject<Item> FISH_TRAP =
            ITEMS.register("fish_trap", () -> new BlockItem(ModBlocks.FISH_TRAP.get(), new Item.Properties()));

    public static final RegistryObject<Item> BUBBLE_JET =
            ITEMS.register("bubble_jet", () -> new BlockItem(ModBlocks.BUBBLE_JET.get(), new Item.Properties()));

    public static final RegistryObject<Item> FISH_TANK =
            ITEMS.register("fish_tank", () -> new BlockItem(ModBlocks.FISH_TANK.get(), new Item.Properties()));

    public static final RegistryObject<Item> ROD_STAND =
            ITEMS.register("rod_stand", () -> new BlockItem(ModBlocks.ROD_STAND.get(), new Item.Properties()));

    private ModItems() {}

    public static void register(IEventBus modBus)
    {
        ITEMS.register(modBus);
    }
}
