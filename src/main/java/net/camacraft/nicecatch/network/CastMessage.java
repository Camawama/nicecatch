package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player released the charge bar and wants to cast at the given power (0..1). */
public class CastMessage
{
    private final float power;
    private final InteractionHand hand;

    public CastMessage(float power, InteractionHand hand)
    {
        this.power = power;
        this.hand = hand;
    }

    public static void encode(CastMessage msg, FriendlyByteBuf buf)
    {
        buf.writeFloat(msg.power);
        buf.writeBoolean(msg.hand == InteractionHand.OFF_HAND);
    }

    public static CastMessage decode(FriendlyByteBuf buf)
    {
        float power = buf.readFloat();
        InteractionHand hand = buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return new CastMessage(power, hand);
    }

    public static void handle(CastMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                ServerFishingManager.onCast(sender, msg.power, msg.hand);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
