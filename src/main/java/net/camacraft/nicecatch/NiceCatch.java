package net.camacraft.nicecatch;

import net.camacraft.nicecatch.network.NiceCatchNet;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NiceCatch.MODID)
public class NiceCatch
{
    public static final String MODID = "nicecatch";

    public NiceCatch(FMLJavaModLoadingContext context)
    {
        NiceCatchNet.register();

        context.registerConfig(ModConfig.Type.SERVER, NiceCatchConfig.SERVER_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, NiceCatchConfig.CLIENT_SPEC);
    }
}
