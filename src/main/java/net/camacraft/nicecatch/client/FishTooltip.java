package net.camacraft.nicecatch.client;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.server.FishSizing;
import net.camacraft.nicecatch.server.FishTraits;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The caught fish's story on its tooltip: its weight, and any traits it was born with —
 * each trait in its own color with a one-line description underneath. Reads the NBT that
 * {@code FishConversion} stamps at the moment of the catch; items without it (bought cod,
 * trap ambient catches) are left untouched.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID, value = Dist.CLIENT)
public final class FishTooltip
{
    private FishTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        CompoundTag info = event.getItemStack().getTagElement("NiceCatch");
        if (info == null) return;

        if (info.contains("Weight", Tag.TAG_FLOAT)) {
            event.getToolTip().add(Component.translatable("nicecatch.tooltip.weight",
                    FishSizing.formatWeight(info.getFloat("Weight")), FishSizing.unitLabel())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (info.contains("Traits", Tag.TAG_LIST)) {
            var list = info.getList("Traits", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                FishTraits.FishTrait trait = FishTraits.byId(list.getString(i));
                if (trait == null) continue; // a trait from a newer/older version; skip quietly
                event.getToolTip().add(Component.translatable(trait.nameKey()).withStyle(trait.color));
                event.getToolTip().add(Component.translatable(trait.descKey())
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }
}
