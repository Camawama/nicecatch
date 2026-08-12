package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server, every tick while reeling: crank revolutions, rod lift, the signed
 * sideways swing (right-positive; answers dart events), line deliberately fed out with the
 * scroll wheel, and whether the reel is being held at all.
 */
public class ReelMessage
{
    private final float crank;
    private final float lift;
    private final float side;
    private final float feed;
    private final boolean holding;

    public ReelMessage(float crank, float lift, float side, float feed, boolean holding)
    {
        this.crank = crank;
        this.lift = lift;
        this.side = side;
        this.feed = feed;
        this.holding = holding;
    }

    public static void encode(ReelMessage msg, FriendlyByteBuf buf)
    {
        buf.writeFloat(msg.crank);
        buf.writeFloat(msg.lift);
        buf.writeFloat(msg.side);
        buf.writeFloat(msg.feed);
        buf.writeBoolean(msg.holding);
    }

    public static ReelMessage decode(FriendlyByteBuf buf)
    {
        return new ReelMessage(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(ReelMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                ServerFishingManager.onReelInput(sender, msg.crank, msg.lift, msg.side, msg.feed, msg.holding);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
