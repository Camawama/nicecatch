package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the fishing line from the player to the fish during a line-arrow fight (a rod fight uses
 * vanilla's own bobber line instead). A short, lightly sagging strand of line segments — enough
 * for the player to see they're tethered to the fish they shot.
 */
public final class FishingLineRenderer
{
    private FishingLineRenderer() {}

    public static void render(PoseStack pose, Vec3 camera, float partialTick)
    {
        if (ClientFishing.phase() != ClientFishing.Phase.FIGHT) return;
        int anchorId = ClientFishing.fightAnchorId();
        if (anchorId < 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity fish = mc.level.getEntity(anchorId);
        if (fish == null) return;

        LocalPlayer player = mc.player;
        Vec3 look = player.getViewVector(partialTick);
        Vec3 flat = new Vec3(look.x, 0.0D, look.z).normalize();
        // From roughly the player's hands, out to the middle of the fish.
        Vec3 start = player.getPosition(partialTick)
                .add(0.0D, player.getBbHeight() * 0.65D, 0.0D)
                .add(flat.scale(0.35D));
        Vec3 end = fish.getPosition(partialTick).add(0.0D, fish.getBbHeight() * 0.5D, 0.0D);

        pose.pushPose();
        pose.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        Matrix4f mat = pose.last().pose();
        Matrix3f nrm = pose.last().normal();

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double sag = Math.min(0.6D, dist * 0.04D);

        int segments = 16;
        float prevX = 0.0F, prevY = 0.0F, prevZ = 0.0F;
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float px = (float) (dx * t);
            float pz = (float) (dz * t);
            float py = (float) (dy * t - sag * Math.sin(Math.PI * t));
            if (i > 0) {
                float nx = px - prevX, ny = py - prevY, nz = pz - prevZ;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1.0E-5F) len = 1.0F;
                nx /= len; ny /= len; nz /= len;
                vc.vertex(mat, prevX, prevY, prevZ).color(35, 30, 24, 255).normal(nrm, nx, ny, nz).endVertex();
                vc.vertex(mat, px, py, pz).color(35, 30, 24, 255).normal(nrm, nx, ny, nz).endVertex();
            }
            prevX = px; prevY = py; prevZ = pz;
        }
        buffer.endBatch(RenderType.lines());
        pose.popPose();
    }
}
