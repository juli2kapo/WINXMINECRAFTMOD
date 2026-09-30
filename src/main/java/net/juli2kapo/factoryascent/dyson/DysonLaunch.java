package net.juli2kapo.factoryascent.dyson;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * The Launch Pad's side of the Dyson project (hooks called from
 * {@link net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity}): a Solar Collector can
 * ride a rocket, one per launch, which is how a team seeds its swarm before it has a Mass Driver.
 */
public final class DysonLaunch {
    private DysonLaunch() {}

    public static boolean isCollector(ItemStack stack) {
        return !stack.isEmpty() && stack.is(DysonContent.DYSON_COLLECTOR.get());
    }

    /** The rocket reached orbit with a collector: it joins the launcher's team's swarm. */
    public static void arrive(ServerLevel level, UUID launcher) {
        var server = level.getServer();
        String team = DysonService.teamOf(server, launcher);
        long added = DysonService.addCollectors(server, team, 1);
        var player = server.getPlayerList().getPlayer(launcher);
        if (player != null) {
            DysonSwarm swarm = DysonSwarm.get(server);
            player.sendSystemMessage(added > 0
                    ? Component.translatable("message.factoryascent.dyson.pad_arrived", swarm.collectors(team), DysonSwarm.target())
                    .withStyle(ChatFormatting.GOLD)
                    : Component.translatable("message.factoryascent.dyson.full").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
