package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.client.ClientFishing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client, once per tick during a fight: line retrieved (the HUD bar), line tension,
 * how played-out the fish is, and whether it is running.
 */
public class FightTickMessage
{
    private final float progress;
    private final float tension;
    private final float fatigue;
    private final boolean fishRunning;

    public FightTickMessage(float progress, float tension, float fatigue, boolean fishRunning)
    {
        this.progress = progress;
        this.tension = tension;
        this.fatigue = fatigue;
        this.fishRunning = fishRunning;
    }

    public static void encode(FightTickMessage msg, FriendlyByteBuf buf)
    {
        buf.writeFloat(msg.progress);
        buf.writeFloat(msg.tension);
        buf.writeFloat(msg.fatigue);
        buf.writeBoolean(msg.fishRunning);
    }

    public static FightTickMessage decode(FriendlyByteBuf buf)
    {
        return new FightTickMessage(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(FightTickMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFishing.handleFightTick(msg.progress, msg.tension, msg.fatigue, msg.fishRunning)));
        ctx.get().setPacketHandled(true);
    }
}
