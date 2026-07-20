package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.client.ClientFishing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: a fish grabbed (or let go of) the player's line. */
public class BiteMessage
{
    private final boolean biting;

    public BiteMessage(boolean biting)
    {
        this.biting = biting;
    }

    public static void encode(BiteMessage msg, FriendlyByteBuf buf)
    {
        buf.writeBoolean(msg.biting);
    }

    public static BiteMessage decode(FriendlyByteBuf buf)
    {
        return new BiteMessage(buf.readBoolean());
    }

    public static void handle(BiteMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFishing.handleBite(msg.biting)));
        ctx.get().setPacketHandled(true);
    }
}
