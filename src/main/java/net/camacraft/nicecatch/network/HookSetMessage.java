package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the player answered a bite. {@code direction} is which way they yanked
 * the rod (1 left, 2 right; 0 for the plain-click hook-set when the directional minigame is
 * off). The server compares it against the direction it rolled for the bite.
 */
public class HookSetMessage
{
    private final byte direction;

    public HookSetMessage(byte direction)
    {
        this.direction = direction;
    }

    public static void encode(HookSetMessage msg, FriendlyByteBuf buf)
    {
        buf.writeByte(msg.direction);
    }

    public static HookSetMessage decode(FriendlyByteBuf buf)
    {
        return new HookSetMessage(buf.readByte());
    }

    public static void handle(HookSetMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                ServerFishingManager.onHookSet(sender, msg.direction);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
