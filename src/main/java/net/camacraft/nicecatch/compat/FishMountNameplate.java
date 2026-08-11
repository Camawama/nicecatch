package net.camacraft.nicecatch.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.server.FishSizing;
import net.camacraft.nicecatch.server.FishTraits;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The trophy plaque reads like the catch line: looking at an Aquaculture fish mount already
 * shows the fish's name (and, for fish caught on their rods, their own weight line) — this
 * appends ours: the stamped Nice Catch weight and the trait names in their colors, drawn as
 * an extra line under theirs. Their renderer re-derives its text internally (the name-tag
 * event's content is ignored), so we draw our line ourselves in the same name-tag frame,
 * one slot lower — two slots when their own weight line is present, which also means we
 * skip our weight number rather than show two.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID, value = Dist.CLIENT)
public final class FishMountNameplate
{
    /** Vertical world-space step between name-tag lines (matches Aquaculture's own line). */
    private static final double LINE_STEP = 0.25D;
    /** Their per-fish weight NBT (displayed by their renderer when present). */
    private static final String AQUA_WEIGHT_KEY = "fishWeight";

    private FishMountNameplate() {}

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event)
    {
        Entity mount = event.getEntity();
        if (!AquacultureCompat.isFishMount(mount)) return;
        Minecraft mc = Minecraft.getInstance();
        // Mirror their hover gate EXACTLY (decompiled): names on, and the crosshair's hit
        // LOCATION within ~half a block of the mount — crucially not an entity pick, because
        // aiming at a flat wall mount usually ray-traces to the block behind it.
        if (!Minecraft.renderNames() || mc.hitResult == null
                || mount.distanceToSqr(mc.hitResult.getLocation()) >= 0.24D) {
            return;
        }

        ItemStack shown = AquacultureCompat.mountedItem(mount);
        CompoundTag info = shown.getTagElement("NiceCatch");
        if (info == null) return;

        boolean theirWeightLine = shown.hasTag() && shown.getTag() != null
                && shown.getTag().contains(AQUA_WEIGHT_KEY);
        MutableComponent line = Component.empty();
        boolean any = false;
        if (!theirWeightLine && info.contains("Weight", Tag.TAG_FLOAT)) {
            line.append(Component.literal(FishSizing.formatWeight(info.getFloat("Weight")) + " ")
                    .append(FishSizing.unitLabel()).withStyle(ChatFormatting.GRAY));
            any = true;
        }
        if (info.contains("Traits", Tag.TAG_LIST)) {
            var traitIds = info.getList("Traits", Tag.TAG_STRING);
            for (int i = 0; i < traitIds.size(); i++) {
                FishTraits.FishTrait trait = FishTraits.byId(traitIds.getString(i));
                if (trait == null) continue;
                if (any) line.append(" ");
                line.append(Component.translatable(trait.nameKey()).withStyle(trait.color));
                any = true;
            }
        }
        if (!any) return;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        if (dispatcher.distanceToSqr(mount) > 4096.0D) return;

        // The vanilla name-tag frame, one line (or two) below where their text sits.
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        double drop = theirWeightLine ? LINE_STEP * 2.0D : LINE_STEP;
        pose.translate(0.0D, mount.getBbHeight() + 0.5D - drop, 0.0D);
        pose.mulPose(dispatcher.cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = pose.last().pose();
        Font font = mc.font;
        float x = -font.width(line) / 2.0F;
        int background = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(line, x, 0.0F, 0x20FFFFFF, false, matrix, event.getMultiBufferSource(),
                Font.DisplayMode.SEE_THROUGH, background, event.getPackedLight());
        font.drawInBatch(line, x, 0.0F, -1, false, matrix, event.getMultiBufferSource(),
                Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        pose.popPose();
    }
}
