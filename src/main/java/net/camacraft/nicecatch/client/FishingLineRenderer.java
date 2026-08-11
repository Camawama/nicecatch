package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Draws the fishing line from an angler to the fish during a line-arrow fight (a rod fight
 * uses vanilla's own bobber line instead). A short, lightly sagging strand of line segments.
 *
 * The local player's fight comes from the client fishing state; everyone ELSE'S arrow
 * fights arrive via {@code ArrowLineMessage} broadcasts and are tracked here, so spectators
 * see the line too. Entries prune themselves the moment either end stops existing.
 */
public final class FishingLineRenderer
{
    /** Other players' active arrow lines: angler entity id -> fish entity id. */
    private static final Map<Integer, Integer> REMOTE_LINES = new HashMap<>();

    private FishingLineRenderer() {}

    public static void handleLine(int playerId, int fishId, boolean active)
    {
        if (active) {
            REMOTE_LINES.put(playerId, fishId);
        } else {
            REMOTE_LINES.remove(playerId);
        }
    }

    public static void clear()
    {
        REMOTE_LINES.clear();
    }

    public static void render(PoseStack pose, Vec3 camera, float partialTick)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // The local fight, driven by the client fishing state.
        if (ClientFishing.phase() == ClientFishing.Phase.FIGHT && ClientFishing.fightAnchorId() >= 0) {
            Entity fish = mc.level.getEntity(ClientFishing.fightAnchorId());
            if (fish != null) {
                drawLine(mc, pose, camera, mc.player, fish, partialTick);
            }
        }

        // Everyone else's arrow fights, from the broadcasts.
        Iterator<Map.Entry<Integer, Integer>> it = REMOTE_LINES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> line = it.next();
            if (line.getKey() == mc.player.getId()) continue; // local path above owns this one
            Entity angler = mc.level.getEntity(line.getKey());
            Entity fish = mc.level.getEntity(line.getValue());
            if (angler == null || fish == null || !angler.isAlive() || !fish.isAlive()) {
                it.remove();
                continue;
            }
            drawLine(mc, pose, camera, angler, fish, partialTick);
        }
    }

    private static void drawLine(Minecraft mc, PoseStack pose, Vec3 camera,
                                 Entity angler, Entity fish, float partialTick)
    {
        Vec3 look = angler.getViewVector(partialTick);
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-6D ? Vec3.ZERO : flat.normalize();
        // From roughly the angler's hands, out to the middle of the fish.
        Vec3 start = angler.getPosition(partialTick)
                .add(0.0D, angler.getBbHeight() * 0.65D, 0.0D)
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
