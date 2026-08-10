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

    private ModBlockEntities() {}

    public static void register(IEventBus modBus)
    {
        BLOCK_ENTITIES.register(modBus);
    }
}
