package net.juli2kapo.factoryascent.space.station;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Sealed rooms: a flood fill of the air around an Oxygen Sealer (or Air Vent), bounded by
 * airtight blocks. If the fill stays under the volume limit the room is sealed and every cell of
 * it holds air; if it runs past the limit, reaches the edge of the world or an unloaded chunk,
 * the room is open to space.
 *
 * <p>Airtight: any block with a full collision cube (hull, glass, stone...), slabs and stairs,
 * closed doors and trapdoors (the Airlock), and the {@link #AIRTIGHT} tag. Everything else (air,
 * torches, fences, bars, open doors...) lets air through.
 */
public final class AirVolume {
    /** Extra blocks that hold air although their shape isn't a full cube. */
    public static final TagKey<Block> AIRTIGHT = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "airtight"));

    private AirVolume() {}

    /** What a flood fill finds in a cell. */
    public enum Cell {
        /** Air can be here (and flows on). */
        OPEN,
        /** An airtight block. */
        WALL,
        /** Outside the world or not loaded: counts as open space. */
        VOID
    }

    /**
     * Result of a fill.
     *
     * @param sealed whether the room is closed
     * @param cells  the room's cells when sealed (packed {@link BlockPos#asLong()}), empty otherwise
     */
    public record Result(boolean sealed, LongOpenHashSet cells) {
        public int size() {
            return cells.size();
        }
    }

    public static boolean airtight(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) return false;
        Block block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock) {
            return !state.getValueOrElse(BlockStateProperties.OPEN, false);
        }
        if (block instanceof SlabBlock || block instanceof StairBlock || state.is(AIRTIGHT)) return true;
        return state.isCollisionShapeFullBlock(level, pos);
    }

    public static Cell classify(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) return Cell.VOID;
        return airtight(level.getBlockState(pos), level, pos) ? Cell.WALL : Cell.OPEN;
    }

    /** Fills from a source block: its six neighbours are where the air comes out. */
    public static Result fromSource(Level level, BlockPos source, int limit) {
        List<BlockPos> starts = new ArrayList<>(6);
        for (Direction d : Direction.values()) starts.add(source.relative(d));
        return flood(p -> classify(level, p), starts, limit);
    }

    /**
     * Breadth-first fill from the open cells among {@code starts}. Sealed if it ends with at most
     * {@code limit} cells and never touched {@link Cell#VOID}. A source walled in on all sides
     * (no open start) is not sealed: it has no room to fill.
     */
    public static Result flood(Function<BlockPos, Cell> probe, Collection<BlockPos> starts, int limit) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (BlockPos s : starts) {
            Cell c = probe.apply(s);
            if (c == Cell.VOID) return new Result(false, new LongOpenHashSet());
            if (c == Cell.OPEN && seen.add(s.asLong())) queue.enqueue(s.asLong());
        }
        if (seen.isEmpty()) return new Result(false, new LongOpenHashSet());
        while (!queue.isEmpty()) {
            long at = queue.dequeueLong();
            for (Direction d : Direction.values()) {
                p.set(at).move(d);
                long key = p.asLong();
                if (seen.contains(key)) continue;
                Cell c = probe.apply(p);
                if (c == Cell.VOID) return new Result(false, new LongOpenHashSet());
                if (c == Cell.WALL) continue;
                seen.add(key);
                if (seen.size() > limit) return new Result(false, new LongOpenHashSet());
                queue.enqueue(key);
            }
        }
        return new Result(true, seen);
    }

    /**
     * Where a room that used to be {@code cells} now leaks: pairs of {inside cell, the opening next
     * to it} (at most {@code max}), found where a cell's neighbour is neither in the room nor a wall.
     */
    public static List<BlockPos[]> breaches(LongSet cells, Function<BlockPos, Cell> probe, int max) {
        List<BlockPos[]> out = new ArrayList<>();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (LongIterator it = cells.iterator(); it.hasNext() && out.size() < max; ) {
            long at = it.nextLong();
            BlockPos inside = BlockPos.of(at);
            for (Direction d : Direction.values()) {
                n.set(at).move(d);
                if (cells.contains(n.asLong())) continue;
                if (probe.apply(n) != Cell.WALL) {
                    out.add(new BlockPos[] {inside, n.immutable()});
                    break;
                }
            }
        }
        return out;
    }
}
