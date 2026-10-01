package net.juli2kapo.factoryascent.phone;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Who is using which phone, handed to every {@link PhoneApp} call: the player, the phone stack in
 * their hand (its {@link PhoneMemory} and battery), and whether they have uplink signal.
 */
public record PhoneContext(ServerPlayer player, ItemStack phone, InteractionHand hand, boolean signal) {
    public MinecraftServer server() {
        return player.level().getServer();
    }

    public PhoneMemory memory() {
        return PhoneMemory.of(phone);
    }

    public void store(PhoneMemory memory) {
        memory.store(phone);
    }
}
