package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.client.ClientFishing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client, once per tick during a fight: line retrieved (the HUD bar), line tension,
 * how played-out the fish is, whether it is running, which tactic (fight phase) it's using
 * right now so the HUD can coach the player through it, the active dart event's required
 * counter-pull (0 none, 1 left, 2 right), and the fish's difficulty-ladder tier (0 basic
 * reel, 1 lifts, 2 charges, 3 darts) so the coaching never demands moves this fish can't make.
 */
public class FightTickMessage
{
    private final float progress;
    private final float tension;
    private final float fatigue;
    private final boolean fishRunning;
    private final byte phase;
    private final byte dartDir;
    private final byte tier;
    /** One-shot: a chain bite just happened (the HUD flashes its announcement). */
    private final boolean chainBite;

    public FightTickMessage(float progress, float tension, float fatigue, boolean fishRunning,
                            byte phase, byte dartDir, byte tier, boolean chainBite)
    {
        this.progress = progress;
        this.tension = tension;
        this.fatigue = fatigue;
        this.fishRunning = fishRunning;
        this.phase = phase;
        this.dartDir = dartDir;
        this.tier = tier;
        this.chainBite = chainBite;
    }

    public static void encode(FightTickMessage msg, FriendlyByteBuf buf)
    {
        buf.writeFloat(msg.progress);
        buf.writeFloat(msg.tension);
        buf.writeFloat(msg.fatigue);
        buf.writeBoolean(msg.fishRunning);
        buf.writeByte(msg.phase);
        buf.writeByte(msg.dartDir);
        buf.writeByte(msg.tier);
        buf.writeBoolean(msg.chainBite);
    }

    public static FightTickMessage decode(FriendlyByteBuf buf)
    {
        return new FightTickMessage(buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean());
    }

    public static void handle(FightTickMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientFishing.handleFightTick(msg.progress, msg.tension, msg.fatigue,
                                msg.fishRunning, msg.phase, msg.dartDir, msg.tier, msg.chainBite)));
        ctx.get().setPacketHandled(true);
    }
}
