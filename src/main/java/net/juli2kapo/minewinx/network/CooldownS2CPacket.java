package net.juli2kapo.minewinx.network;

import net.juli2kapo.minewinx.client.ClientHudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Informa al cliente que un slot entró en cooldown (para el HUD). */
public class CooldownS2CPacket {

    private final int slot;
    private final int cooldownTicks;

    public CooldownS2CPacket(int slot, int cooldownTicks) {
        this.slot = slot;
        this.cooldownTicks = cooldownTicks;
    }

    public CooldownS2CPacket(FriendlyByteBuf buf) {
        this.slot = buf.readInt();
        this.cooldownTicks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(slot);
        buf.writeInt(cooldownTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHudState.setCooldown(slot, cooldownTicks)));
        return true;
    }
}
