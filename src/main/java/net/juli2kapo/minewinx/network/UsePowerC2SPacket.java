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
            // Los poderes se pueden usar siempre (ya no hace falta transformarse)
            if (player == null) {
                return;
            }

            String elementStr = PlayerDataProvider.getElement(player);
            EnumPowers.Element element = EnumPowers.Element.fromName(elementStr);

            if (element != EnumPowers.Element.UNKNOWN) {
                EnumPowers power = EnumPowers.getPower(element, this.powerSlot);
                if (power != EnumPowers.UNKNOWN) {
                    // Cooldown: rechazar si el slot todavía está enfriándose
                    // (en creativo no hay cooldowns, para poder testear tranquilo)
                    boolean useCooldowns = !player.isCreative();
                    if (useCooldowns) {
                        long remaining = net.juli2kapo.minewinx.util.PowerCooldowns.remaining(player, this.powerSlot);
                        if (remaining > 0) {
                            player.displayClientMessage(Component.translatable(
                                    "message.minewinx.cooldown", (int) Math.ceil(remaining / 20.0)), true);
                            return;
                        }
                    }
                    power.execute(player);
                    // Cooldown final = base * multiplicador de config del mundo
                    int cooldown = (int) Math.round(power.getCooldownTicks()
                            * net.juli2kapo.minewinx.Config.cooldownMultiplier);
                    if (useCooldowns && cooldown > 0) {
                        net.juli2kapo.minewinx.util.PowerCooldowns.set(player, this.powerSlot, cooldown);
                        PacketHandler.sendToPlayer(new CooldownS2CPacket(this.powerSlot, cooldown), player);
                    }
                }
            }
        });
        return true;
    }
}