package net.juli2kapo.factoryascent.item.drill;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.juli2kapo.factoryascent.item.ElectricDrillItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * Area (3x3) and vein mining for the Electric Drill.
 * <p>
 * Every extra block is broken through {@link net.minecraft.server.level.ServerPlayerGameMode#destroyBlock}, exactly
 * like a player breaking it by hand: a {@code BreakBlockEvent} fires (so claim/protection mods can veto it), drops,
 * stats and exhaustion apply, and the drill's own {@code mineBlock} charges {@link ElectricDrillItem#COST_PER_BLOCK}.
 * A re-entrancy flag keeps those nested breaks from starting area/vein mining of their own.
 */
public final class DrillMining {
    /** Most blocks a vein can hold, including the one mined by hand. */
    public static final int MAX_VEIN = 32;
    /** An area block may be this much harder than the centre block (stone 1.5 still takes ores and deepslate, 3.0). */
    public static final float HARDNESS_MARGIN = 1.5f;

    private static boolean busy;

    private DrillMining() {}

    /** True while extra blocks are being broken (their {@code mineBlock} must not chain further). */
    public static boolean busy() {
        return busy;
    }

    /** Blocks other than {@code origin} that {@code mode} would also break, in breaking order. */
    public static List<BlockPos> targets(BlockGetter level, BlockPos origin, BlockState originState, Direction face,
                                         DrillMode mode) {
        return switch (mode) {
            case SINGLE -> List.of();
            case AREA -> areaTargets(level, origin, originState, face);
            case VEIN -> veinTargets(level, origin, originState);
        };
    }

    /** The 8 blocks around {@code origin} on the plane of {@code face} that pass {@link #canAreaMine}. */
    public static List<BlockPos> areaTargets(BlockGetter level, BlockPos origin, BlockState originState, Direction face) {
        List<BlockPos> out = new ArrayList<>(8);
        if (!ElectricDrillItem.effectiveOn(originState)) return out;
        float centre = originState.getDestroySpeed(level, origin);
        Direction.Axis axis = face.getAxis();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                BlockPos pos = switch (axis) {
                    case X -> origin.offset(0, a, b);
                    case Y -> origin.offset(a, 0, b);
                    case Z -> origin.offset(a, b, 0);
                };
                if (canAreaMine(level.getBlockState(pos), level, pos, centre)) out.add(pos);
            }
        }
        return out;
    }

    /**
     * Breadth-first walk (26-neighbourhood) over blocks identical to {@code originState}, starting at {@code origin}.
     * Only ores ({@code c:ores}) start a vein; returns at most {@link #MAX_VEIN}{@code - 1} extra blocks.
     */
    public static List<BlockPos> veinTargets(BlockGetter level, BlockPos origin, BlockState originState) {
        List<BlockPos> out = new ArrayList<>();
        if (!originState.is(Tags.Blocks.ORES) || !ElectricDrillItem.effectiveOn(originState)) return out;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin.immutable());
        queue.add(origin.immutable());
        while (!queue.isEmpty() && out.size() < MAX_VEIN - 1) {
            BlockPos at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = at.offset(dx, dy, dz);
                        if (!seen.add(next)) continue;
                        BlockState state = level.getBlockState(next);
                        if (!state.is(originState.getBlock()) || !breakable(state, level, next)) continue;
                        out.add(next);
                        queue.add(next);
                        if (out.size() >= MAX_VEIN - 1) return out;
                    }
                }
            }
        }
        return out;
    }

    /** The drill is effective on it, it can be broken at all, and it is not much harder than the centre. */
    public static boolean canAreaMine(BlockState state, BlockGetter level, BlockPos pos, float centreHardness) {
        return ElectricDrillItem.effectiveOn(state) && breakable(state, level, pos)
                && state.getDestroySpeed(level, pos) <= centreHardness + HARDNESS_MARGIN;
    }

    private static boolean breakable(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.isAir() && !state.is(Blocks.BEDROCK) && state.getDestroySpeed(level, pos) >= 0f;
    }

    /**
     * Breaks the extra blocks {@code mode} adds to mining {@code origin}, one {@code destroyBlock} each, until the
     * drill runs out of charge or leaves the player's main hand. Returns how many were broken.
     */
    public static int harvestExtra(ServerPlayer player, ItemStack drill, BlockPos origin, BlockState originState,
                                   Direction face, DrillMode mode) {
        if (busy) return 0;
        List<BlockPos> targets = targets(player.level(), origin, originState, face, mode);
        if (targets.isEmpty()) return 0;
        float centre = originState.getDestroySpeed(player.level(), origin);
        int broken = 0;
        busy = true;
        try {
            for (BlockPos pos : targets) {
                if (player.getMainHandItem() != drill || !ElectricDrillItem.hasCharge(drill)) break;
                BlockState state = player.level().getBlockState(pos);
                // Re-check: an earlier break (falling blocks, a mod's event handler…) may have changed the world.
                boolean ok = mode == DrillMode.VEIN
                        ? state.is(originState.getBlock()) && breakable(state, player.level(), pos)
                        : canAreaMine(state, player.level(), pos, centre);
                if (ok && player.gameMode.destroyBlock(pos)) broken++;
            }
        } finally {
            busy = false;
        }
        return broken;
    }
}
