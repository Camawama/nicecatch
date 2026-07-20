package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server, once per tick during a fight: crank revolutions and rod-lift performed this tick. */
public class ReelMessage
{
    private final float crank;
    private final float lift;
    private final boolean holding;

    public ReelMessage(float crank, float lift, boolean holding)
    {
        this.crank = crank;
        this.lift = lift;
        this.holding = holding;
    }

    public static void encode(ReelMessage msg, FriendlyByteBuf buf)
    {
        buf.writeFloat(msg.crank);
        buf.writeFloat(msg.lift);
        buf.writeBoolean(msg.holding);
    }

    public static ReelMessage decode(FriendlyByteBuf buf)
    {
        return new ReelMessage(buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(ReelMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                ServerFishingManager.onReelInput(sender, msg.crank, msg.lift, msg.holding);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
