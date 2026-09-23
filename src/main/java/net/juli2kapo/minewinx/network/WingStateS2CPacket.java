package net.juli2kapo.minewinx.network;

import net.juli2kapo.minewinx.client.ClientWingState;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sincroniza a los clientes cercanos si un jugador está transformado (para dibujar sus alas). */
public class WingStateS2CPacket {

    private final int entityId;
    private final String element;
    private final boolean transformed;

    public WingStateS2CPacket(Player player) {
        this(player.getId(), PlayerDataProvider.getElement(player), PlayerDataProvider.isTransformed(player));
    }

    public WingStateS2CPacket(int entityId, String element, boolean transformed) {
        this.entityId = entityId;
        this.element = element == null ? "" : element;
        this.transformed = transformed;
    }

    public WingStateS2CPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.element = buf.readUtf();
        this.transformed = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(element);
        buf.writeBoolean(transformed);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWingState.set(entityId, element, transformed)));
        return true;
    }
}
