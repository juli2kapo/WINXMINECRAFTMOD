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

                if (isTransformed) {
                    player.getAbilities().mayfly = true;
                } else {
                    if (!player.isCreative() && !player.isSpectator()) {
                        player.getAbilities().mayfly = false;
                        player.getAbilities().flying = false;
                    }
                }
                player.onUpdateAbilities();
            }
        });
        return true;
    }
}