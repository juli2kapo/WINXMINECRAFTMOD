package net.juli2kapo.factoryascent.space.station;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * The Docking Port: an Orbital Shuttle that sets down on it (or drifts onto it in orbit) is caught
 * and held, and refuels from containers touching the port, like on a Launch Pad. The Shuttle does
 * the work (see {@code Shuttle#dock}).
 */
public class DockingPortBlock extends Block implements DescribedBlock {
    public DockingPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.docking_port").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.docking_port_fuel").withStyle(ChatFormatting.DARK_AQUA));
    }
}
