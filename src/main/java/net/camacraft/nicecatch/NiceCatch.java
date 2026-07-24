package net.camacraft.nicecatch;

import net.camacraft.nicecatch.network.NiceCatchNet;
import net.camacraft.nicecatch.registry.ModEntities;
import net.camacraft.nicecatch.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NiceCatch.MODID)
public class NiceCatch
{
    public static final String MODID = "nicecatch";

    public NiceCatch(FMLJavaModLoadingContext context)
    {
        IEventBus modBus = context.getModEventBus();
        NiceCatchNet.register();
        ModItems.register(modBus);
        ModEntities.register(modBus);

        context.registerConfig(ModConfig.Type.SERVER, NiceCatchConfig.SERVER_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, NiceCatchConfig.CLIENT_SPEC);
    }
}
