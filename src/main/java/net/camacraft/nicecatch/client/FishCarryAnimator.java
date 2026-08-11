package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Holding your catch like a catch: in first person a light fish dangles by the tail from
 * one outstretched hand, swaying a little as you move, while a heavy one is hefted low
 * toward the middle of the view — a two-handed carry. Weight comes from the caught item's
 * NBT; plain fish items (bought cod etc.) count as light. Pose transforms only, so it works
 * with every fish item from every mod.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID, value = Dist.CLIENT)
public final class FishCarryAnimator
{
    /** At and past this weight the fish is carried with both hands. */
    private static final float TWO_HAND_LBS = 8.0F;
    private static final float KG_TO_LBS = 2.20462F;

    private FishCarryAnimator() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event)
    {
        if (!NiceCatchConfig.CLIENT.fishCarryEnabled.get()) return;
        ItemStack stack = event.getItemStack();
        if (!isFishStack(stack)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float lbs = heldWeightLbs(stack);
        PoseStack pose = event.getPoseStack();
        float t = mc.player.tickCount + event.getPartialTick();
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
                ? mc.options.mainHand().get() : mc.options.mainHand().get().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;

        if (lbs < TWO_HAND_LBS) {
            // Dangling by the tail from one hand, held out a touch, with a gentle sway.
            pose.translate(side * -0.05D, -0.02D, -0.08D);
            pose.mulPose(Axis.ZP.rotationDegrees(side * -100.0F + Mth.sin(t * 0.35F) * 5.0F));
        } else {
            // The two-armed heft: the catch rides low and centered, laid across the hands,
            // shifting slightly with your steps.
            pose.translate(side * -0.28D, -0.1D, 0.02D);
            pose.scale(1.3F, 1.3F, 1.3F);
            pose.mulPose(Axis.ZP.rotationDegrees(side * -80.0F));
            pose.mulPose(Axis.XP.rotationDegrees(-10.0F + Mth.sin(t * 0.3F) * 2.0F));
        }
    }

    /** A fish in hand: anything we stamped at the catch, or any item in the fishes tag. */
    private static boolean isFishStack(ItemStack stack)
    {
        return !stack.isEmpty()
                && (stack.getTagElement("NiceCatch") != null || stack.is(ItemTags.FISHES));
    }

    private static float heldWeightLbs(ItemStack stack)
    {
        CompoundTag info = stack.getTagElement("NiceCatch");
        if (info != null && info.contains("Weight", Tag.TAG_FLOAT)) {
            return info.getFloat("Weight") * KG_TO_LBS;
        }
        return 1.5F; // an ordinary un-stamped fish item: light, one hand
    }
}
