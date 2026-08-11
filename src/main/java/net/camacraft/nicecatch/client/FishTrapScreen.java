package net.camacraft.nicecatch.client;

import net.camacraft.nicecatch.menu.FishTrapMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The trap's GUI: the vanilla hopper layout — bait slot left, the haul to its right. */
public class FishTrapScreen extends AbstractContainerScreen<FishTrapMenu>
{
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/hopper.png");

    public FishTrapScreen(FishTrapMenu menu, Inventory playerInventory, Component title)
    {
        super(menu, playerInventory, title);
        imageHeight = 133;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}
