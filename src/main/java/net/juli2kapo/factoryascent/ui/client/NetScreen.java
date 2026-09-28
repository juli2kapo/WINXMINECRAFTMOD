package net.juli2kapo.factoryascent.ui.client;

import net.juli2kapo.factoryascent.ui.ScreenPayloads;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Base of the pipe / cable / storage network screens: they belong to a block position, ask the
 * server for a fresh view twice a second, and close when the player walks away.
 */
abstract class NetScreen extends Screen {
    private static final double MAX_DISTANCE_SQ = 12 * 12;
    private int ticks;

    NetScreen(Component title) {
        super(title);
    }

    abstract BlockPos pos();

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null) return;
        if (minecraft.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos())) > MAX_DISTANCE_SQ) {
            onClose();
            return;
        }
        if (++ticks % 10 == 0) ClientPacketDistributor.sendToServer(new ScreenPayloads.NetRefresh(pos()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}
