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

    public static final RegistryObject<Block> BUBBLE_JET =
            BLOCKS.register("bubble_jet", () -> new net.camacraft.nicecatch.block.BubbleJetBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NETHER)
                            .strength(1.5F)
                            .sound(SoundType.SOUL_SAND)));

    public static final RegistryObject<Block> FISH_TANK =
            BLOCKS.register("fish_tank", () -> new net.camacraft.nicecatch.block.FishTankBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .strength(0.5F)
                            .sound(SoundType.GLASS)
                            .noOcclusion()));

    public static final RegistryObject<Block> ROD_STAND =
            BLOCKS.register("rod_stand", () -> new net.camacraft.nicecatch.block.RodStandBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(0.8F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()));

    private ModBlocks() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
    }
}
