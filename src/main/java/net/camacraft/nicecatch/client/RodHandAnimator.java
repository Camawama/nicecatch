package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.RodUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First-person rod animation: extra pose applied to the rod hand before vanilla renders it,
 * driven entirely by what the fishing state machine is doing — the rod cocks back and
 * trembles with the charge bar, whips forward on release, twitches with a bite, and during
 * the fight it bends with line tension, judders through runs, rises as you lift, and wobbles
 * in a little circle with your actual crank input. Simple rotations and slides, but the rod
 * finally feels held rather than glued to the screen.
 *
 * All values are visual-only and read from {@link ClientFishing}; no gameplay state here.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID, value = Dist.CLIENT)
public final class RodHandAnimator
{
    private RodHandAnimator() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event)
    {
        if (!NiceCatchConfig.CLIENT.rodAnimationsEnabled.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !RodUtil.isRod(event.getItemStack())) return;

        PoseStack pose = event.getPoseStack();
        float pt = event.getPartialTick();
        float t = mc.player.tickCount + pt;

        // Sign convention (playtested): positive X-rotation raises the rod tip, negative
        // dips it toward the water.

        // The forward whip of a just-released cast plays over whatever phase follows.
        float whip = ClientFishing.castWhip(pt);
        if (whip > 0.0F) {
            float arc = Mth.sin(whip * (float) Math.PI); // back-swing peaks mid-flight
            pose.translate(0.0D, 0.0D, -0.08D * arc);
            pose.mulPose(Axis.XP.rotationDegrees(-32.0F * arc)); // snaps forward and down
        }

        switch (ClientFishing.phase()) {
            case CHARGING -> {
                // Raised and cocked back over the shoulder, rising with the power bar,
                // with a keen tremble.
                float c = ClientFishing.chargeValue(pt);
                pose.translate(0.0D, 0.03D * c, 0.08D * c);
                pose.mulPose(Axis.XP.rotationDegrees(24.0F * c + Mth.sin(t * 1.6F) * 1.5F * c));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 1.1F) * 1.2F * c));
            }
            case BITE -> {
                // The tip jerks with the fish worrying the bait.
                float twitch = Mth.sin(t * 2.8F) * 4.0F + Mth.sin(t * 4.3F) * 1.5F;
                pose.translate(0.0D, -0.008D * Math.abs(twitch) / 5.0D, 0.0D);
                pose.mulPose(Axis.XP.rotationDegrees(twitch));
            }
            case FIGHT -> {
                float tension = ClientFishing.tension();
                float lift = ClientFishing.liftAnim();
                // Bent down toward the water by the load on the line; lifting hauls it back up.
                pose.translate(0.0D, -0.035D * tension + 0.05D * lift, 0.015D * tension);
                pose.mulPose(Axis.XP.rotationDegrees(-10.0F * tension + 22.0F * lift));
                // ...juddering while the fish runs...
                if (ClientFishing.isFishRunning()) {
                    pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 3.1F) * 1.6F));
                    pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 2.3F) * 2.2F));
                }
                applyCrankWobble(pose, pt);
            }
            case REEL -> {
                // A lighter version of the fight pose: slight working dip plus the crank.
                pose.mulPose(Axis.XP.rotationDegrees(-4.0F));
                applyCrankWobble(pose, pt);
            }
            default -> { }
        }
    }

    /** A small circular stir of the hand, phase-locked to the player's actual crank input. */
    private static void applyCrankWobble(PoseStack pose, float partialTick)
    {
        float vel = ClientFishing.crankVel();
        if (vel < 0.005F) return;
        float amp = Math.min(1.0F, vel * 6.0F);
        float angle = ClientFishing.crankAngle(partialTick);
        pose.translate(0.013D * Mth.sin(angle) * amp, 0.013D * Mth.cos(angle) * amp, 0.0D);
        pose.mulPose(Axis.ZP.rotationDegrees(4.0F * Mth.sin(angle) * amp));
    }
}
