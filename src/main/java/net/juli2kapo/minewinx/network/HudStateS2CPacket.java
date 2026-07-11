package net.juli2kapo.minewinx.network;

import net.juli2kapo.minewinx.client.ClientHudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sincroniza elemento y stage al cliente para el HUD de poderes. */
public class HudStateS2CPacket {

    private final String element;
    private final int stage;

    public HudStateS2CPacket(String element, int stage) {
        this.element = element;
        this.stage = stage;
    }

    public HudStateS2CPacket(FriendlyByteBuf buf) {
        this.element = buf.readUtf();
        this.stage = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(element);
        buf.writeInt(stage);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHudState.setState(element, stage)));
        return true;
    }
}
