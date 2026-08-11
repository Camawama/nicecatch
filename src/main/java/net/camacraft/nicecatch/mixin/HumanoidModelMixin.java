package net.camacraft.nicecatch.mixin;

import net.camacraft.nicecatch.client.FishCarryRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two-handed heft, visible to everyone: while an entity holds a heavy fish, both arms
 * pose forward to cradle it — which also moves the hand socket (translateToHand follows the
 * posed arm), so the fish itself rides out front where the hands are. Runs at the tail of
 * setupAnim so it overrides the ordinary held-item arm poses; players, zombies, armor-stand
 * cousins — anyone with a humanoid model hauling a trophy shows it.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin
{
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void nicecatch$fishCarryPoses(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                          float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci)
    {
        // A swing owns the arm: vanilla's attack animation runs earlier in setupAnim, and
        // stomping it from here is what made swinging look broken. Resume posing after.
        if (entity.swinging || entity.attackAnim > 0.0F) return;

        HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
        if (FishCarryRenderer.usesTwoHandCarry(entity)) {
            // Both forearms out and spread for the wide carry.
            self.rightArm.xRot = -1.05F;
            self.rightArm.yRot = -0.1F;
            self.rightArm.zRot = 0.0F;
            self.leftArm.xRot = -1.05F;
            self.leftArm.yRot = 0.1F;
            self.leftArm.zRot = 0.0F;
            return;
        }
        // A light fish is shown off: the holding arm extends forward instead of the
        // ordinary bent held-item pose, so the dangle hangs from an outstretched hand.
        boolean rightIsMain = entity.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        if (FishCarryRenderer.isLightFishStack(entity.getMainHandItem())) {
            holdOut(self, rightIsMain);
        }
        if (FishCarryRenderer.isLightFishStack(entity.getOffhandItem())) {
            holdOut(self, !rightIsMain);
        }
    }

    private static void holdOut(HumanoidModel<?> model, boolean rightArm)
    {
        var arm = rightArm ? model.rightArm : model.leftArm;
        arm.xRot = -0.75F;
        arm.yRot = 0.0F;
        arm.zRot = 0.0F;
    }
}
