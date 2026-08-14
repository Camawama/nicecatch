package net.camacraft.nicecatch.registry;

import net.camacraft.nicecatch.NiceCatch;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus setup: puts the mod's items into creative tabs so they're obtainable. */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModSetup
{
    private ModSetup() {}

    @SubscribeEvent
    public static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.LINE_ARROW.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.FISHING_NET.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.FISH_TRAP.get());
            event.accept(ModItems.ROD_STAND.get());
            event.accept(ModItems.BUBBLE_JET.get());
            event.accept(ModItems.FISH_TANK.get());
        }
    }
}
