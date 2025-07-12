package net.juli2kapo.minewinx.network;

import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MineWinx.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        INSTANCE.messageBuilder(TransformC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(TransformC2SPacket::new)
                .encoder(TransformC2SPacket::toBytes)
                .consumerMainThread(TransformC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(UsePowerC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(UsePowerC2SPacket::new)
                .encoder(UsePowerC2SPacket::toBytes)
                .consumerMainThread(UsePowerC2SPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}