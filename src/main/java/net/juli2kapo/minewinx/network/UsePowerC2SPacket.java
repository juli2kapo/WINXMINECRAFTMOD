package net.juli2kapo.minewinx.network;

import net.juli2kapo.minewinx.powers.EnumPowers;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UsePowerC2SPacket {

    private final int powerSlot;

    public UsePowerC2SPacket(int powerSlot) {
        this.powerSlot = powerSlot;
    }

    public UsePowerC2SPacket(FriendlyByteBuf buf) {
        this.powerSlot = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.powerSlot);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !PlayerDataProvider.isTransformed(player)) {
                return;
            }

            String elementStr = PlayerDataProvider.getElement(player);
            EnumPowers.Element element = EnumPowers.Element.fromName(elementStr);

            if (element != EnumPowers.Element.UNKNOWN) {
                EnumPowers power = EnumPowers.getPower(element, this.powerSlot);
                // Si el poder es UNKNOWN, no hacemos nada.
                if (power != EnumPowers.UNKNOWN) {
                    power.execute(player);
                }
                player.sendSystemMessage(Component.literal("Casteando: " + power.name() + " (" + element.name() + ")"));
            }
        });
        return true;
    }
}