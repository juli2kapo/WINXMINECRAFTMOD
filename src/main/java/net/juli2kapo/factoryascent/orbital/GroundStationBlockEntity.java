package net.juli2kapo.factoryascent.orbital;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no data: it exists so the client can draw the slowly sweeping dish. */
public class GroundStationBlockEntity extends BlockEntity {
    public GroundStationBlockEntity(BlockPos pos, BlockState state) {
        super(OrbitalContent.GROUND_STATION_BE.get(), pos, state);
    }
}
