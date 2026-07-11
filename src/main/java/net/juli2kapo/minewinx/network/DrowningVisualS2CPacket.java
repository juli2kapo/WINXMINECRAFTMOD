package net.juli2kapo.minewinx.network;

import net.juli2kapo.minewinx.client.ClientDrowningTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Los efectos de mobs NO se sincronizan al cliente en vanilla, así que la capa
 * de la burbuja nunca puede verlos con hasEffect(). Este paquete avisa a los
 * clientes cercanos qué entidad está ahogándose y por cuánto tiempo.
 */
public class DrowningVisualS2CPacket {

    private final int entityId;
    private final int durationTicks;

    public DrowningVisualS2CPacket(int entityId, int durationTicks) {
        this.entityId = entityId;
        this.durationTicks = durationTicks;
    }

    public DrowningVisualS2CPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.durationTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(durationTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDrowningTracker.mark(entityId, durationTicks)));
        return true;
    }
}
