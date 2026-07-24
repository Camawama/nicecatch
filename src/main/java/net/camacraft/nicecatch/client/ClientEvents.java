package net.camacraft.nicecatch.client;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.RodUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NiceCatch.MODID, value = Dist.CLIENT)
public class ClientEvents
{
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ClientFishing.reset();
            return;
        }
        ClientFishing.clientTick(mc);
    }

    /**
     * First interception point: the use key itself. While charging or fighting, all right-clicks
     * belong to the fishing system. When idle and aiming at nothing, a right-click with a rod
     * starts charging instead of casting. Aiming at blocks/entities keeps vanilla behavior
     * (doors, chests, villagers still work while holding a rod).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event)
    {
        if (!event.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        switch (ClientFishing.phase()) {
            case BITE -> {
                // Entity bites lock the click for the hook-set; loot bites do too when gradual
                // reel-in is on (holding reels the item in), else they retrieve vanilla-style.
                if (ClientFishing.isEntityBite() || ClientFishing.gradualReelEnabled()) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
            }
            case CHARGING, FIGHT, REEL -> {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            case IDLE -> {
                ItemStack stack = player.getItemInHand(event.getHand());
                if (!RodUtil.isRod(stack)) break;
                if (player.fishing == null) {
                    boolean aimingAtNothing = mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS;
                    if (aimingAtNothing && !player.isHandsBusy() && ClientFishing.canStartCharge()) {
                        event.setCanceled(true);
                        event.setSwingHand(false);
                        ClientFishing.beginCharge(event.getHand());
                    }
                } else if (ClientFishing.gradualReelEnabled()) {
                    // Bobber is out: block the vanilla instant retract; hold to reel it in.
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
            }
        }
    }

    /**
     * Second interception point: catches rod uses that fell through a block interaction
     * (e.g. right-clicking while aiming at plain stone), which skip the path above.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (!event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        if (!RodUtil.isRod(event.getItemStack())) return;

        switch (ClientFishing.phase()) {
            case IDLE -> {
                if (player.fishing == null) {
                    if (!player.isHandsBusy() && ClientFishing.canStartCharge()) {
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.CONSUME);
                        ClientFishing.beginCharge(event.getHand());
                    }
                } else if (ClientFishing.gradualReelEnabled()) {
                    // Bobber is out: block the vanilla instant retract; hold to reel it in.
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.CONSUME);
                }
            }
            case BITE -> {
                if (ClientFishing.isEntityBite() || ClientFishing.gradualReelEnabled()) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.CONSUME);
                }
            }
            case CHARGING, FIGHT, REEL -> {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.CONSUME);
            }
        }
    }

    @SubscribeEvent
    public static void onOverlayRender(RenderGuiOverlayEvent.Pre event)
    {
        if (ClientFishing.phase() == ClientFishing.Phase.IDLE && ClientFishing.celebrateTicks() <= 0) return;
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) return;
        event.setCanceled(true);
        FishingBarOverlay.render(event.getGuiGraphics(), event.getWindow(), event.getPartialTick());
    }

    /** Per-frame (not per-tick) camera follow while reeling, so the pan never steps or jitters. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientFishing.followFishFrame(mc, event.renderTickTime, mc.getDeltaFrameTime());
    }

    /** Slight zoom while focused on reeling; vanilla smooths the transition for us. */
    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event)
    {
        if (ClientFishing.isCapturingMouse()) {
            event.setNewFovModifier(event.getNewFovModifier() * NiceCatchConfig.CLIENT.reelFovMultiplier.get().floatValue());
        }
    }

    /** Subtle rod-strain shake while the fish is running. */
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event)
    {
        if (!NiceCatchConfig.CLIENT.cameraShake.get()) return;
        if (ClientFishing.phase() != ClientFishing.Phase.FIGHT || !ClientFishing.isFishRunning()) return;

        double t = ClientFishing.fightTicks() + event.getPartialTick();
        float amp = 0.25F + ClientFishing.tension() * 0.35F;
        event.setPitch(event.getPitch() + (float) Math.sin(t * 2.1D) * amp * 0.4F);
        event.setYaw(event.getYaw() + (float) Math.sin(t * 1.7D + 0.6D) * amp * 0.3F);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 2.7D + 1.3D) * amp * 0.5F);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientFishing.reset();
    }
}
