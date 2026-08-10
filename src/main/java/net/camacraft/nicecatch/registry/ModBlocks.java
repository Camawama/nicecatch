package net.camacraft.nicecatch.registry;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.block.FishTrapBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks
{
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, NiceCatch.MODID);

    public static final RegistryObject<Block> FISH_TRAP =
            BLOCKS.register("fish_trap", () -> new FishTrapBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(1.2F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()));

    private ModBlocks() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
    }
}
