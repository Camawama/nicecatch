package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.client.ClientFishing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: something grabbed (or let go of) the player's line.
 * Entity bites are real fish and want a hook-set; loot bites (fishless water)
 * are plain vanilla retrieves.
 */
public class BiteMessage
{
    private final boolean biting;
    private final boolean entity;

    public BiteMessage(boolean biting, boolean entity)
    {
        this.biting = biting;
        this.entity = entity;
    }

    public static void encode(BiteMessage msg, FriendlyByteBuf buf)
    {
        buf.writeBoolean(msg.biting);
        buf.writeBoolean(msg.entity);
    }

    public static BiteMessage decode(FriendlyByteBuf buf)
    {
        return new BiteMessage(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(BiteMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFishing.handleBite(msg.biting, msg.entity)));
        ctx.get().setPacketHandled(true);
    }
}
