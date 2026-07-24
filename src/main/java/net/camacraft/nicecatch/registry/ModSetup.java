package net.camacraft.nicecatch.registry;

import net.camacraft.nicecatch.NiceCatch;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus setup: puts the fishing-line arrow into a creative tab so it's obtainable. */
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
    }
}
