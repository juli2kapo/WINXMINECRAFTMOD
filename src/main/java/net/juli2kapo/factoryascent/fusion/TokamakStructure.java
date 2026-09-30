package net.juli2kapo.factoryascent.fusion;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.nuclear.ReactorPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The tokamak: a 7x7x3 ring around the Tokamak Core, which sits in the middle of the middle layer.
 * Counting rings by distance d from the core (the larger of the x and z offsets):
 * <pre>
 *   middle layer: d=0 the core, d=1 magnets (inner wall), d=2 AIR (the plasma channel), d=3 magnets (outer wall)
 *   top/bottom:   d=0 a magnet (the central solenoid), d=1..3 casing, Reactor Glass or Fusion Ports
 * </pre>
 * 34 magnets, 96 shell blocks, a 16-block ring of vacuum.
 */
public final class TokamakStructure {
    public enum Error { OK, NEEDS_MAGNET, NEEDS_CASING, CHANNEL_BLOCKED }

    public record Result(Error error, @Nullable BlockPos bad, List<BlockPos> ports) {
        public boolean valid() {
            return error == Error.OK;
        }
    }

    private TokamakStructure() {}

    public enum Role { CORE, MAGNET, CASING, CHANNEL }

    /** What belongs at offset (dx, dy, dz) from the core. */
    public static Role role(int dx, int dy, int dz) {
        int d = Math.max(Math.abs(dx), Math.abs(dz));
        if (dy == 0) {
            return switch (d) {
                case 0 -> Role.CORE;
                case 2 -> Role.CHANNEL;
                default -> Role.MAGNET;
            };
        }
        return d == 0 ? Role.MAGNET : Role.CASING;
    }

    public static Result scan(Level level, BlockPos core) {
        List<BlockPos> ports = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    switch (role(dx, dy, dz)) {
                        case MAGNET -> {
                            if (!(s.getBlock() instanceof FusionPartBlock f && f.magnet())) return new Result(Error.NEEDS_MAGNET, p.immutable(), ports);
                        }
                        case CASING -> {
                            boolean ok = s.getBlock() instanceof FusionPartBlock f && !f.magnet()
                                    || s.getBlock() instanceof ReactorPartBlock r && r.part() == ReactorPartBlock.Part.GLASS
                                    || s.getBlock() instanceof FusionPortBlock;
                            if (!ok) return new Result(Error.NEEDS_CASING, p.immutable(), ports);
                            if (s.getBlock() instanceof FusionPortBlock) ports.add(p.immutable());
                        }
                        case CHANNEL -> {
                            if (!s.isAir()) return new Result(Error.CHANNEL_BLOCKED, p.immutable(), ports);
                        }
                        default -> { }
                    }
                }
            }
        }
        return new Result(Error.OK, null, ports);
    }
}
