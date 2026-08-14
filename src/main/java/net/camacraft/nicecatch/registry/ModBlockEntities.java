package net.camacraft.nicecatch.registry;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.block.FishTrapBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, NiceCatch.MODID);

    public static final RegistryObject<BlockEntityType<FishTrapBlockEntity>> FISH_TRAP =
            BLOCK_ENTITIES.register("fish_trap", () ->
                    BlockEntityType.Builder.of(FishTrapBlockEntity::new, ModBlocks.FISH_TRAP.get()).build(null));

    public static final RegistryObject<BlockEntityType<net.camacraft.nicecatch.block.BubbleJetBlockEntity>> BUBBLE_JET =
            BLOCK_ENTITIES.register("bubble_jet", () ->
                    BlockEntityType.Builder.of(net.camacraft.nicecatch.block.BubbleJetBlockEntity::new,
                            ModBlocks.BUBBLE_JET.get()).build(null));

    public static final RegistryObject<BlockEntityType<net.camacraft.nicecatch.block.FishTankBlockEntity>> FISH_TANK =
            BLOCK_ENTITIES.register("fish_tank", () ->
                    BlockEntityType.Builder.of(net.camacraft.nicecatch.block.FishTankBlockEntity::new,
                            ModBlocks.FISH_TANK.get()).build(null));

    public static final RegistryObject<BlockEntityType<net.camacraft.nicecatch.block.RodStandBlockEntity>> ROD_STAND =
            BLOCK_ENTITIES.register("rod_stand", () ->
                    BlockEntityType.Builder.of(net.camacraft.nicecatch.block.RodStandBlockEntity::new,
                            ModBlocks.ROD_STAND.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus)
    {
        BLOCK_ENTITIES.register(modBus);
    }
}
