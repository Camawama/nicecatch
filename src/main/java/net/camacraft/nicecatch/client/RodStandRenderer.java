package net.camacraft.nicecatch.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.camacraft.nicecatch.block.RodStandBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Renders the ACTUAL parked rod on the stand — whatever rod item was placed (vanilla,
 * Aquaculture, enchanted glint and all) — planted upright and leaning toward wherever its
 * line was cast, in its CAST pose. The cast pose is the tricky part: rod items pick their
 * cast model through an item property that demands "a player holding this stack whose
 * fishing hook is live", so a hidden client-side poser player briefly holds the stack and
 * the parked hook while the model resolves. The line anchor in StandLines uses the same
 * tip, so line meets rod.
 */
public class RodStandRenderer implements BlockEntityRenderer<RodStandBlockEntity>
{
    /** The invisible model-poser: holds the rod + hook just long enough to resolve "cast". */
    @Nullable private static RemotePlayer castPoser;
    @Nullable private static Level poserLevel;

    public RodStandRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(RodStandBlockEntity stand, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay)
    {
        ItemStack rod = stand.getRod();
        if (rod.isEmpty()) return;

        Vec3 lean = StandLines.rodLean(stand.getBlockPos(), stand.clientHookId());
        float yaw = lean != null ? (float) Math.toDegrees(Math.atan2(-lean.x, lean.z)) : 0.0F;

        Minecraft mc = Minecraft.getInstance();
        BakedModel model = resolveCastModel(mc, rod, stand.clientHookId());

        pose.pushPose();
        pose.translate(0.5D, 0.95D, 0.5D);
        // The sprite plane CONTAINS the cast direction (profile view from the side), the
        // handle sits in the stand's clamp, and the whole rod leans out over the water.
        pose.mulPose(Axis.YP.rotationDegrees(-yaw + 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(-35.0F));
        pose.scale(0.85F, 0.85F, 0.85F);
        mc.getItemRenderer().render(rod, ItemDisplayContext.FIXED, false, pose, buffer,
                light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }

    /**
     * The rod model WITH its line-out override applied: the "cast" item property only
     * answers 1 for a player actively fishing with the stack, so the poser fakes exactly
     * that for the length of one model lookup. Falls back to the plain model when the
     * parked hook isn't client-loaded.
     */
    private static BakedModel resolveCastModel(Minecraft mc, ItemStack rod, int hookId)
    {
        FishingHook hook = mc.level != null
                && mc.level.getEntity(hookId) instanceof FishingHook found ? found : null;
        if (hook == null) {
            return mc.getItemRenderer().getModel(rod, mc.level, null, 0);
        }
        if (castPoser == null || poserLevel != mc.level) {
            castPoser = new RemotePlayer(mc.level, new GameProfile(
                    UUID.fromString("d2a9f0c1-5b7e-4c3d-8e1f-6a0b9c8d7e6f"), "nicecatch_poser"));
            poserLevel = mc.level;
        }
        castPoser.setItemInHand(InteractionHand.MAIN_HAND, rod);
        castPoser.fishing = hook;
        BakedModel model = mc.getItemRenderer().getModel(rod, mc.level, castPoser, 0);
        castPoser.fishing = null;
        castPoser.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return model;
    }
}
