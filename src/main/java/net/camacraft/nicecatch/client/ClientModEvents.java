package net.camacraft.nicecatch.client;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.registry.ModEntities;
import net.camacraft.nicecatch.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client mod-bus setup: the fishing-line arrow's entity renderer, trap screen, keybinds. */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents
{
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event)
    {
        event.register(RodControls.ROD_CONTROL);
        event.register(RodControls.CUT_LINE);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.LINE_ARROW.get(), LineArrowRenderer::new);
        event.registerBlockEntityRenderer(
                net.camacraft.nicecatch.registry.ModBlockEntities.FISH_TANK.get(), FishTankRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.FISH_TRAP.get(), FishTrapScreen::new));
    }
}
