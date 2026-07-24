package net.camacraft.nicecatch.registry;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.entity.LineArrowEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, NiceCatch.MODID);

    public static final RegistryObject<EntityType<LineArrowEntity>> LINE_ARROW =
            ENTITY_TYPES.register("line_arrow", () ->
                    EntityType.Builder.<LineArrowEntity>of(LineArrowEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("line_arrow"));

    private ModEntities() {}

    public static void register(IEventBus modBus)
    {
        ENTITY_TYPES.register(modBus);
    }
}
