package net.camacraft.nicecatch.mixin;

import net.camacraft.nicecatch.server.FishTraits;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A caught fish born with a trait gleams like an enchanted item: hasFoil is the single
 * switch every renderer (inventory, ground, in-hand) consults for the glint, and we can't
 * override isFoil on items we don't own — vanilla cod is vanilla's.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin
{
    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
    private void nicecatch$traitFishGlint(CallbackInfoReturnable<Boolean> cir)
    {
        if (FishTraits.stackHasTraits((ItemStack) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
