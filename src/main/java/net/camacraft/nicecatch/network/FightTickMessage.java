package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.client.ClientFishing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client, once per tick during a fight: current progress, line tension, and whether the fish is running. */
public class FightTickMessage
{
    private final float progress;
    private final float tension;
    private final boolean fishRunning;

    public FightTickMessage(float progress, float tension, boolean fishRunning)
    {
        this.progress = progress;
        this.tension = tension;
        this.fishRunning = fishRunning;
    }

    public static void encode(FightTickMessage msg, FriendlyByteBuf buf)
    {
        buf.writeFloat(msg.progress);
        buf.writeFloat(msg.tension);
        buf.writeBoolean(msg.fishRunning);
    }

    public static FightTickMessage decode(FriendlyByteBuf buf)
    {
        return new FightTickMessage(buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(FightTickMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFishing.handleFightTick(msg.progress, msg.tension, msg.fishRunning)));
        ctx.get().setPacketHandled(true);
    }
}
