package net.juli2kapo.factoryascent.machine;

import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Multiblock structures, Immersive-Engineering style: plain blocks arranged around a controller.
 * The controller sits at the bottom centre of the front face of a 3×3×3 cube and faces outwards.
 *
 * <ul>
 *   <li>Coke Oven: the other 26 blocks are Coke Oven Bricks (solid).</li>
 *   <li>Blast Furnace: the other blocks are Fire Bricks, the centre is hollow (air).</li>
 * </ul>
 */
public final class Multiblocks {
    private Multiblocks() {}

    /** How many blocks of the structure are missing or wrong; 0 means it is complete. */
    public static int missing(MachineType type, Level level, BlockPos controller, Direction facing) {
        Block wall = switch (type) {
            case COKE_OVEN -> ModBlocks.COKE_OVEN_BRICKS.get();
            case BLAST_FURNACE -> ModBlocks.FIRE_BRICKS.get();
            default -> null;
        };
        if (wall == null) return 0;
        boolean hollow = type == MachineType.BLAST_FURNACE;
        BlockPos center = controller.relative(facing.getOpposite()).above();
        int missing = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (p.equals(controller)) continue;
                    if (!level.isLoaded(p)) return 27;
                    BlockState state = level.getBlockState(p);
                    boolean isCenter = dx == 0 && dy == 0 && dz == 0;
                    boolean ok = isCenter && hollow ? state.isAir() : state.is(wall);
                    if (!ok) missing++;
                }
            }
        }
        return missing;
    }
}
