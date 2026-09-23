package net.juli2kapo.minewinx.network;

import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TransformC2SPacket {

    public TransformC2SPacket() {
    }

    public TransformC2SPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                boolean isTransformed = !PlayerDataProvider.isTransformed(player);
                PlayerDataProvider.setTransformed(player, isTransformed);

                player.sendSystemMessage(Component.literal("Servidor: Estado de transformación cambiado a: " + isTransformed));
                PacketHandler.sendToTracking(new WingStateS2CPacket(player), player);

                // Las winx ya no vuelan: la transformación no otorga mayfly.
                // Por las dudas, limpiar el vuelo si quedó de una versión anterior.
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
        });
        return true;
    }
}