package net.camacraft.nicecatch.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Patchy surface murk: SURFACE-layer water blocks (no water above them) get their tint
 * color scaled down by a per-position value noise, so a pond is a shifting patchwork of
 * clear and turbid water. Fish under a murky patch are genuinely hard to spot from above —
 * the bite gets its surprise back — while visibility UNDERWATER is completely untouched:
 * within one connected body of water there are no faces between the camera and the fish,
 * so a darkened surface never sits in an underwater sightline.
 *
 * The hook is the single packed-RGB tint local at the top of tesselate (the first int
 * local, ordinal 0): one store feeds every face of the block, and being a local capture
 * it needs no fragile targeting of Forge-patched helper methods. Two octaves of hashed
 * cell noise (4- and 16-block cells) give the patches actual shape.
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin
{
    @ModifyVariable(method = "tesselate", at = @At("STORE"), ordinal = 0)
    private int nicecatch$murkTint(int color, BlockAndTintGetter level, BlockPos pos,
                                   VertexConsumer consumer, BlockState blockState, FluidState fluidState)
    {
        if (!NiceCatchConfig.CLIENT.waterMurkEnabled.get()) return color;
        if (!fluidState.is(FluidTags.WATER)) return color;
        if (level.getFluidState(pos.above()).is(FluidTags.WATER)) return color; // not the surface layer

        // Shore water stays readable; murk deepens with the water column below. A one-deep
        // shelf shows the fish plainly, the drop-off past it goes dark.
        int depth = 0;
        BlockPos.MutableBlockPos probe = pos.mutable();
        while (depth < 7) {
            probe.move(0, -1, 0);
            if (!level.getFluidState(probe).is(FluidTags.WATER)) break;
            depth++;
        }
        float depthFactor = net.minecraft.util.Mth.clamp((depth - 0.5F) / 4.5F, 0.0F, 1.0F);
        if (depthFactor <= 0.0F) return color;

        // Smoothly interpolated value noise in world coordinates: continuous across chunk
        // borders by construction, no cell blockiness. Two octaves for patch shape.
        float noise = 0.65F * nicecatch$valueNoise(pos.getX() / 11.0D, pos.getZ() / 11.0D, 0x9E3779B9L)
                + 0.35F * nicecatch$valueNoise(pos.getX() / 27.0D, pos.getZ() / 27.0D, 0x85EBCA6BL);

        float strength = NiceCatchConfig.CLIENT.waterMurkStrength.get().floatValue();
        float factor = 1.0F - strength * depthFactor * (0.30F + 0.70F * noise);

        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Bilinear value noise with smoothstep fading — a continuous field, not cells. */
    private static float nicecatch$valueNoise(double u, double v, long salt)
    {
        int x0 = (int) Math.floor(u);
        int z0 = (int) Math.floor(v);
        float fu = (float) (u - x0);
        float fv = (float) (v - z0);
        fu = fu * fu * (3.0F - 2.0F * fu);
        fv = fv * fv * (3.0F - 2.0F * fv);
        float c00 = nicecatch$hash(x0, z0, salt);
        float c10 = nicecatch$hash(x0 + 1, z0, salt);
        float c01 = nicecatch$hash(x0, z0 + 1, salt);
        float c11 = nicecatch$hash(x0 + 1, z0 + 1, salt);
        float top = c00 + (c10 - c00) * fu;
        float bottom = c01 + (c11 - c01) * fu;
        return top + (bottom - top) * fv;
    }

    private static float nicecatch$hash(int cx, int cz, long salt)
    {
        long h = cx * 341873128712L + cz * 132897987541L + salt;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h & 0xFFFF) / 65535.0F;
    }
}
