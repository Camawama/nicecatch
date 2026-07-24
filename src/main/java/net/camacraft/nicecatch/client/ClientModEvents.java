package net.camacraft.nicecatch.client;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client mod-bus setup: the fishing-line arrow's entity renderer. */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents
{
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.LINE_ARROW.get(), LineArrowRenderer::new);
    }
}
