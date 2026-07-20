package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player grabbed the rod while a fish was biting; start the fight. */
public class HookSetMessage
{
    public HookSetMessage() {}

    public static void encode(HookSetMessage msg, FriendlyByteBuf buf) {}

    public static HookSetMessage decode(FriendlyByteBuf buf)
    {
        return new HookSetMessage();
    }

    public static void handle(HookSetMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                ServerFishingManager.onHookSet(sender);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
