package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.platform.Window;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the fishing bar in the XP bar's slot (the way the horse jump bar replaces it):
 * a cyan oscillating bar while charging a cast, a flashing red bar while a fish is biting,
 * and a green-to-red (by line tension) progress bar during the fight.
 */
public class FishingBarOverlay
{
    private static final ResourceLocation GUI_ICONS = ResourceLocation.withDefaultNamespace("textures/gui/icons.png");

    public static void render(GuiGraphics graphics, Window window, float partialTick)
    {
        int x = window.getGuiScaledWidth() / 2 - 91;
        int y = window.getGuiScaledHeight() - 32 + 3;

        switch (ClientFishing.phase()) {
            case CHARGING -> {
                drawBackground(graphics, x, y);
                drawFill(graphics, x, y, ClientFishing.chargeValue(partialTick), 0xFF3CB4E7);
                drawHint(graphics, window, y, "nicecatch.hint.charge", 0xFF9BDCF5, false);
            }
            case BITE -> {
                drawBackground(graphics, x, y);
                boolean flashOn = (ClientFishing.biteTicks() / 4) % 2 == 0;
                if (flashOn) {
                    drawFill(graphics, x, y, 1.0F, ClientFishing.isEntityBite() ? 0xFFE03434 : 0xFFE8C33A);
                }
                String key = ClientFishing.isEntityBite() ? "nicecatch.hint.bite" : "nicecatch.hint.loot_bite";
                drawHint(graphics, window, y, key, 0xFFFF6A5A, true);
            }
            case FIGHT -> {
                float t = (float) (ClientFishing.fightTicks() + partialTick);
                if (ClientFishing.isFishRunning()) {
                    x += Math.round(Mth.sin(t * 1.9F) * 1.5F);
                }
                drawBackground(graphics, x, y);
                int color = tensionColor(ClientFishing.tension());
                // High tension warning: flash the fill toward red.
                if (ClientFishing.tension() > 0.8F && (ClientFishing.fightTicks() / 3) % 2 == 0) {
                    color = 0xFFFF2020;
                }
                drawFill(graphics, x, y, ClientFishing.shownProgress(), color);

                if (ClientFishing.tension() > 0.8F) {
                    drawHint(graphics, window, y, "nicecatch.hint.tension", 0xFFFF4040, true);
                } else if (ClientFishing.isFishRunning()) {
                    drawHint(graphics, window, y, "nicecatch.hint.run", 0xFFE8C33A, false);
                } else if (ClientFishing.shownProgress() > 0.95F) {
                    drawHint(graphics, window, y, "nicecatch.hint.close", 0xFF7EE0C8, false);
                } else {
                    drawHint(graphics, window, y, "nicecatch.hint.reel", 0xFFB6E8A0, false);
                }
            }
            default -> {
                if (ClientFishing.celebrateTicks() > 0) {
                    drawBackground(graphics, x, y);
                    drawFill(graphics, x, y, 1.0F, 0xFF7EE044);
                    drawHint(graphics, window, y, "nicecatch.hint.caught", 0xFF7EE044, false);
                }
            }
        }
    }

    /**
     * One-line coaching text so nobody has to guess the controls. Drawn where the vanilla
     * actionbar text lives (well above the hotbar) so it never overlaps hearts or hunger.
     */
    private static void drawHint(GuiGraphics graphics, Window window, int barY, String key, int color, boolean flash)
    {
        if (!NiceCatchConfig.CLIENT.showHints.get()) return;
        if (flash && (ClientFishing.biteTicks() + ClientFishing.fightTicks()) / 6 % 2 == 1) return;
        Minecraft mc = Minecraft.getInstance();
        graphics.drawCenteredString(mc.font, Component.translatable(key),
                window.getGuiScaledWidth() / 2, window.getGuiScaledHeight() - 68, color);
    }

    private static void drawBackground(GuiGraphics graphics, int x, int y)
    {
        graphics.blit(GUI_ICONS, x, y, 0, 64, 182, 5);
    }

    /** Draws a smooth 5px-tall fill with a highlight top row and shadow bottom row, matching the bar sprites. */
    private static void drawFill(GuiGraphics graphics, int x, int y, float fraction, int color)
    {
        int width = Math.round(Mth.clamp(fraction, 0.0F, 1.0F) * 182.0F);
        if (width <= 0) return;
        int top = brighten(color, 0.45F);
        int bottom = darken(color, 0.45F);
        graphics.fill(x, y, x + width, y + 1, top);
        graphics.fill(x, y + 1, x + width, y + 4, color);
        graphics.fill(x, y + 4, x + width, y + 5, bottom);
    }

    /** Lime when relaxed, gold at half tension, red near the snapping point. */
    private static int tensionColor(float tension)
    {
        if (tension < 0.5F) {
            return lerpColor(tension * 2.0F, 0xFF7EE044, 0xFFE8C33A);
        }
        return lerpColor((tension - 0.5F) * 2.0F, 0xFFE8C33A, 0xFFE03434);
    }

    private static int lerpColor(float t, int from, int to)
    {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int brighten(int color, float amount)
    {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * (1.0F + amount)) + 30);
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * (1.0F + amount)) + 30);
        int b = Math.min(255, (int) ((color & 0xFF) * (1.0F + amount)) + 30);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, float amount)
    {
        int r = (int) (((color >> 16) & 0xFF) * (1.0F - amount));
        int g = (int) (((color >> 8) & 0xFF) * (1.0F - amount));
        int b = (int) ((color & 0xFF) * (1.0F - amount));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
