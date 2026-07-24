package net.camacraft.nicecatch.client;

import net.camacraft.nicecatch.entity.LineArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renders the flying fishing-line arrow. Reuses the vanilla arrow texture, so no new art is needed. */
public class LineArrowRenderer extends ArrowRenderer<LineArrowEntity>
{
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public LineArrowRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LineArrowEntity entity)
    {
        return TEXTURE;
    }
}
