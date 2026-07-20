package net.camacraft.nicecatch.network;

import net.camacraft.nicecatch.NiceCatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NiceCatchNet
{
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(NiceCatch.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register()
    {
        int id = 0;
        CHANNEL.registerMessage(id++, CastMessage.class, CastMessage::encode, CastMessage::decode, CastMessage::handle);
        CHANNEL.registerMessage(id++, HookSetMessage.class, HookSetMessage::encode, HookSetMessage::decode, HookSetMessage::handle);
        CHANNEL.registerMessage(id++, ReelMessage.class, ReelMessage::encode, ReelMessage::decode, ReelMessage::handle);
        CHANNEL.registerMessage(id++, BiteMessage.class, BiteMessage::encode, BiteMessage::decode, BiteMessage::handle);
        CHANNEL.registerMessage(id++, FightTickMessage.class, FightTickMessage::encode, FightTickMessage::decode, FightTickMessage::handle);
        CHANNEL.registerMessage(id++, FightEndMessage.class, FightEndMessage::encode, FightEndMessage::decode, FightEndMessage::handle);
    }

    public static void sendToServer(Object message)
    {
        CHANNEL.sendToServer(message);
    }

    public static void sendTo(ServerPlayer player, Object message)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
