package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the player pressed the cut-line key — give up on whatever the line is
 * doing (fight, bite, retrieve, or a parked bobber), losing the hook but not the fish fight
 * the hard way. No payload; the server acts on the sender's own session state.
 */
public class CutLineMessage
{
    public CutLineMessage() {}

    public static void encode(CutLineMessage msg, FriendlyByteBuf buf) {}

    public static CutLineMessage decode(FriendlyByteBuf buf)
    {
        return new CutLineMessage();
    }

    public static void handle(CutLineMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                ServerFishingManager.handleCutLine(sender);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
