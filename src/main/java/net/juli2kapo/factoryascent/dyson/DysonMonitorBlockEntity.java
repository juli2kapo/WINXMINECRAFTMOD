package net.juli2kapo.factoryascent.dyson;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Dyson Monitor keeps no data of its own: its screen and the hologram above it show the
 * viewer's team's swarm. The block entity only exists so the hologram can be drawn.
 */
public class DysonMonitorBlockEntity extends BlockEntity {
    public DysonMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(DysonContent.DYSON_MONITOR_BE.get(), pos, state);
    }
}
