package net.juli2kapo.factoryascent.nuclear;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A fission reactor's shape: a closed box from 3x3x3 to 7x7x7 whose walls are Reactor Casing,
 * Reactor Glass, ports and the one controller, and whose inside holds Fuel Channels, Control Rods
 * or air. The controller sits in a wall, facing out.
 *
 * @param error      what is wrong, or {@link Error#OK}
 * @param bad        the first block that breaks the rules (for the screen)
 * @param width      size along the controller's wall (left to right)
 * @param height     size up
 * @param depth      size from the controller's wall inwards
 * @param channels   Fuel Channel blocks inside
 * @param controlRods Control Rod blocks inside
 * @param neighbours average number of other channels touching each channel (0..6)
 * @param columns    interior top-down map, [a][c] = channels * 8 + control rods (up to 5x5)
 */
public record ReactorStructure(Error error, @Nullable BlockPos bad, int width, int height, int depth, int channels,
                               int controlRods, float neighbours, int[][] columns, List<BlockPos> ports,
                               List<BlockPos> channelBlocks, List<BlockPos> interiorBlocks, BlockPos center) {
    public static final int MIN = 3, MAX = 7;

    public enum Error { OK, TOO_SMALL, TOO_BIG, BAD_WALL, BAD_INTERIOR, NO_CHANNELS, TWO_CONTROLLERS }

    public boolean valid() {
        return error == Error.OK;
    }

    public int volume() {
        return width * height * depth;
    }

    public int interior() {
        return Math.max(0, (width - 2) * (height - 2) * (depth - 2));
    }

    static ReactorStructure invalid(Error error, @Nullable BlockPos bad, BlockPos center) {
        return new ReactorStructure(error, bad, 0, 0, 0, 0, 0, 0, new int[0][0], List.of(), List.of(), List.of(), center);
    }

    private static boolean isWall(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof ReactorPartBlock part && part.isWall()
                || s.getBlock() instanceof ReactorPortBlock || s.getBlock() instanceof ReactorControllerBlock;
    }

    private static int walk(Level level, BlockPos from, Direction dir) {
        int n = 0;
        while (n < MAX && isWall(level, from.relative(dir, n + 1))) n++;
        return n;
    }

    /** Scans the reactor whose controller is at {@code controller}, facing {@code facing} (out of the reactor). */
    public static ReactorStructure scan(Level level, BlockPos controller, Direction facing) {
        Direction in = facing.getOpposite();
        Direction right = facing.getClockWise();
        int left = walk(level, controller, right.getOpposite());
        int rightN = walk(level, controller, right);
        int down = walk(level, controller, Direction.DOWN);
        int up = walk(level, controller, Direction.UP);
        BlockPos corner = controller.relative(right.getOpposite(), left).below(down);
        int depth = walk(level, corner, in) + 1;
        int w = left + rightN + 1, h = down + up + 1;
        BlockPos center = corner.relative(right, w / 2).above(h / 2).relative(in, depth / 2);
        if (w < MIN || h < MIN || depth < MIN) return invalid(Error.TOO_SMALL, null, controller.relative(in));
        if (w > MAX || h > MAX || depth > MAX) return invalid(Error.TOO_BIG, null, center);
        int channels = 0, rods = 0, touching = 0;
        int[][] columns = new int[w - 2][depth - 2];
        List<BlockPos> ports = new ArrayList<>();
        List<BlockPos> channelBlocks = new ArrayList<>();
        List<BlockPos> interiorBlocks = new ArrayList<>();
        for (int a = 0; a < w; a++) {
            for (int b = 0; b < h; b++) {
                for (int c = 0; c < depth; c++) {
                    BlockPos p = corner.relative(right, a).above(b).relative(in, c);
                    BlockState s = level.getBlockState(p);
                    boolean edge = a == 0 || a == w - 1 || b == 0 || b == h - 1 || c == 0 || c == depth - 1;
                    if (edge) {
                        if (!isWall(level, p)) return invalid(Error.BAD_WALL, p.immutable(), center);
                        if (s.getBlock() instanceof ReactorControllerBlock && !p.equals(controller)) {
                            return invalid(Error.TWO_CONTROLLERS, p.immutable(), center);
                        }
                        if (s.getBlock() instanceof ReactorPortBlock) ports.add(p.immutable());
                        continue;
                    }
                    interiorBlocks.add(p.immutable());
                    if (s.getBlock() instanceof ReactorPartBlock part && part.part() == ReactorPartBlock.Part.FUEL_CHANNEL) {
                        channels++;
                        channelBlocks.add(p.immutable());
                        columns[a - 1][c - 1] += 8;
                        for (Direction d : Direction.values()) {
                            if (level.getBlockState(p.relative(d)).getBlock() instanceof ReactorPartBlock n
                                    && n.part() == ReactorPartBlock.Part.FUEL_CHANNEL) touching++;
                        }
                    } else if (s.getBlock() instanceof ReactorPartBlock part && part.part() == ReactorPartBlock.Part.CONTROL_ROD) {
                        rods++;
                        columns[a - 1][c - 1] += 1;
                    } else if (!s.isAir()) {
                        return invalid(Error.BAD_INTERIOR, p.immutable(), center);
                    }
                }
            }
        }
        if (channels == 0) return invalid(Error.NO_CHANNELS, null, center);
        return new ReactorStructure(Error.OK, null, w, h, depth, channels, rods, touching / (float) channels, columns,
                ports, channelBlocks, interiorBlocks, center);
    }
}
