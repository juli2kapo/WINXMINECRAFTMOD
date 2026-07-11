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
    private final String selectedPlant;

    public HudStateS2CPacket(String element, int stage, String selectedPlant) {
        this.element = element;
        this.stage = stage;
        this.selectedPlant = selectedPlant == null ? "" : selectedPlant;
    }

    public HudStateS2CPacket(FriendlyByteBuf buf) {
        this.element = buf.readUtf();
        this.stage = buf.readInt();
        this.selectedPlant = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(element);
        buf.writeInt(stage);
        buf.writeUtf(selectedPlant);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHudState.setState(element, stage, selectedPlant)));
        return true;
    }
}
