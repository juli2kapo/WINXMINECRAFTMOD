package net.juli2kapo.factoryascent.guide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.dyson.DysonContent;
import net.juli2kapo.factoryascent.dyson.DysonReceiverBlockEntity;
import net.juli2kapo.factoryascent.dyson.MassDriverBlockEntity;
import net.juli2kapo.factoryascent.ender.EnderAnchorBlock;
import net.juli2kapo.factoryascent.ender.EnderAnchorBlockEntity;
import net.juli2kapo.factoryascent.ender.EnderContent;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachine;
import net.juli2kapo.factoryascent.fluid.machine.OilDerrickBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.RefineryBlockEntity;
import net.juli2kapo.factoryascent.fusion.TokamakStructure;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.Multiblocks;
import net.juli2kapo.factoryascent.nuclear.ReactorStructure;
import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.power.Generator;
import net.juli2kapo.factoryascent.power.PowerBlockEntity;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.power.WindTurbineBlockEntity;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * Every multiblock of the mod as one data table: the layouts the Manual's 3D viewer draws, the
 * hologram projector projects and the bill of materials counts. Each layout carries the real
 * structure check of the code ({@link Validator}), and the GameTests place every layout and assert
 * that the check accepts it, so the guide cannot drift from the code.
 *
 * <p>Layouts are in a canonical orientation: the controller faces NORTH (towards -z, the first row
 * of each layer). Layers go bottom to top; each layer is a list of rows north to south, each row a
 * string west to east. {@code ' '} is "anything", {@code '_'} must be air.
 */
public final class GuideMultiblocks {
    private GuideMultiblocks() {}

    /** The real structure check: whether the structure whose controller is at {@code controller} is formed. */
    @FunctionalInterface
    public interface Validator {
        boolean formed(ServerLevel level, BlockPos controller);
    }

    /** One block of a layout, in grid coordinates (x east, y up, z south). */
    public record Cell(int x, int y, int z, BlockState state) {}

    /** One multiblock layout (a variant of an entry's structure). */
    public static final class Layout {
        public final String id;
        /** Lang key of the variant's name ({@code guide.factoryascent.mb.<id>}). */
        public final String nameKey;
        public final int width, height, depth;
        private final BlockState[][][] grid; // [y][z][x], null = anything
        private final boolean[][][] air;
        public final BlockPos controller;
        public final Validator validator;
        /** Extra note keys shown under the viewer (open sky, oil underground...). */
        public final List<String> notes;

        Layout(String id, int w, int h, int d, BlockState[][][] grid, boolean[][][] air, BlockPos controller,
               Validator validator, List<String> notes) {
            this.id = id;
            this.nameKey = "guide.factoryascent.mb." + id;
            this.width = w;
            this.height = h;
            this.depth = d;
            this.grid = grid;
            this.air = air;
            this.controller = controller;
            this.validator = validator;
            this.notes = notes;
        }

        public @Nullable BlockState at(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) return null;
            return grid[y][z][x];
        }

        public boolean mustBeAir(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) return false;
            return air[y][z][x];
        }

        /** Every block, bottom layer first. */
        public List<Cell> cells() {
            List<Cell> out = new ArrayList<>();
            for (int y = 0; y < height; y++) out.addAll(layer(y));
            return out;
        }

        public List<Cell> layer(int y) {
            List<Cell> out = new ArrayList<>();
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    BlockState s = grid[y][z][x];
                    if (s != null) out.add(new Cell(x, y, z, s));
                }
            }
            return out;
        }

        /** Bill of materials: item → count, in first-use order. */
        public Map<Item, Integer> materials() {
            Map<Item, Integer> bom = new LinkedHashMap<>();
            for (Cell c : cells()) {
                if (c.state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        && c.state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) continue;
                Item item = c.state.getBlock().asItem();
                bom.merge(item, 1, Integer::sum);
            }
            return bom;
        }

        public int blockCount() {
            return materials().values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    // ---------------------------------------------------------------- builder

    private static final class Builder {
        final String id;
        final Map<Character, Supplier<BlockState>> legend = new LinkedHashMap<>();
        final List<String[]> layers = new ArrayList<>();
        Validator validator = (l, p) -> false;
        final List<String> notes = new ArrayList<>();

        Builder(String id) {
            this.id = id;
        }

        Builder key(char c, Supplier<BlockState> state) {
            legend.put(c, state);
            return this;
        }

        Builder layer(String... rows) {
            layers.add(rows);
            return this;
        }

        Builder check(Validator v) {
            validator = v;
            return this;
        }

        Builder note(String key) {
            notes.add("guide.factoryascent.note." + key);
            return this;
        }

        Layout build() {
            int h = layers.size(), d = layers.get(0).length, w = layers.get(0)[0].length();
            BlockState[][][] grid = new BlockState[h][d][w];
            boolean[][][] air = new boolean[h][d][w];
            BlockPos controller = null;
            for (int y = 0; y < h; y++) {
                String[] rows = layers.get(y);
                if (rows.length != d) throw new IllegalStateException(id + ": layer " + y + " has " + rows.length + " rows");
                for (int z = 0; z < d; z++) {
                    if (rows[z].length() != w) throw new IllegalStateException(id + ": row " + y + "/" + z + " width");
                    for (int x = 0; x < w; x++) {
                        char c = rows[z].charAt(x);
                        if (c == ' ') continue;
                        if (c == '_') {
                            air[y][z][x] = true;
                            continue;
                        }
                        Supplier<BlockState> s = legend.get(c);
                        if (s == null) throw new IllegalStateException(id + ": unknown key '" + c + "'");
                        grid[y][z][x] = s.get();
                        if (c == '@') controller = new BlockPos(x, y, z);
                    }
                }
            }
            if (controller == null) throw new IllegalStateException(id + ": no controller '@'");
            return new Layout(id, w, h, d, grid, air, controller, validator, List.copyOf(notes));
        }
    }

    /** The block's default state turned to face {@code dir} (horizontal or full facing, if it has one). */
    static BlockState facing(Block block, Direction dir) {
        BlockState s = block.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && dir.getAxis().isHorizontal()) {
            s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        } else if (s.hasProperty(BlockStateProperties.FACING)) {
            s = s.setValue(BlockStateProperties.FACING, dir);
        }
        return s;
    }

    private static Supplier<BlockState> plain(Supplier<? extends Block> block) {
        return () -> block.get().defaultBlockState();
    }

    private static Supplier<BlockState> facing(Supplier<? extends Block> block, Direction dir) {
        return () -> facing(block.get(), dir);
    }

    private static Direction facingOf(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return Direction.NORTH;
    }

    // ---------------------------------------------------------------- the table

    /** Structure id → its variants (first = recommended). Built lazily (needs the registries). */
    private static @Nullable Map<String, List<Layout>> all;

    public static synchronized Map<String, List<Layout>> all() {
        if (all == null) all = Collections.unmodifiableMap(build());
        return all;
    }

    public static List<Layout> variants(String id) {
        return all().getOrDefault(id, List.of());
    }

    private static Map<String, List<Layout>> build() {
        Map<String, List<Layout>> m = new LinkedHashMap<>();
        // Coke Oven / Blast Furnace (machine/Multiblocks): controller at the bottom centre of the front.
        m.put("coke_oven", List.of(new Builder("coke_oven")
                .key('#', plain(ModBlocks.COKE_OVEN_BRICKS))
                .key('@', facing(() -> ModBlocks.machine(MachineType.COKE_OVEN).get(), Direction.NORTH))
                .layer("#@#", "###", "###").layer("###", "###", "###").layer("###", "###", "###")
                .check((l, p) -> Multiblocks.missing(MachineType.COKE_OVEN, l, p, facingOf(l, p)) == 0).build()));
        m.put("blast_furnace", List.of(new Builder("blast_furnace")
                .key('#', plain(ModBlocks.FIRE_BRICKS))
                .key('@', facing(() -> ModBlocks.machine(MachineType.BLAST_FURNACE).get(), Direction.NORTH))
                .layer("#@#", "###", "###").layer("###", "#_#", "###").layer("###", "###", "###")
                .note("hollow")
                .check((l, p) -> Multiblocks.missing(MachineType.BLAST_FURNACE, l, p, facingOf(l, p)) == 0).build()));
        m.put("fission_reactor", List.of(
                reactor(5, new String[]{"CRC", "CCC", "CRC"}),
                reactor(3, new String[]{"C"}),
                reactor(7, new String[]{"CCRCC", "CCCCC", "RCRCR", "CCCCC", "CCRCC"})));
        m.put("tokamak", List.of(tokamak()));
        m.put("oil_derrick", List.of(new Builder("oil_derrick")
                .key('b', plain(FluidContent.DERRICK_BASE))
                .key('@', facing(() -> FluidContent.machine(FluidMachine.OIL_DERRICK).get(), Direction.NORTH))
                .layer("bbb", "b@b", "bbb")
                .note("oil")
                .check(OilDerrickBlockEntity::structureValid).build()));
        m.put("refinery", List.of(new Builder("refinery")
                .key('t', plain(FluidContent.REFINERY_TOWER))
                .key('@', facing(() -> FluidContent.machine(FluidMachine.REFINERY).get(), Direction.NORTH))
                .layer("@").layer("t").layer("t").layer("t")
                .check(RefineryBlockEntity::structureValid).build()));
        m.put("launch_pad", List.of(new Builder("launch_pad")
                .key('p', plain(OrbitalContent.LAUNCH_PAD))
                .key('@', facing(OrbitalContent.LAUNCH_CONTROLLER, Direction.NORTH))
                .layer("ppp", "p@p", "ppp")
                .note("clearance")
                .check((l, p) -> l.getBlockEntity(p) instanceof LaunchControllerBlockEntity be && be.isFormed()).build()));
        m.put("mass_driver", List.of(new Builder("mass_driver")
                .key('r', plain(DysonContent.MASS_DRIVER_RAIL))
                .key('@', facing(DysonContent.MASS_DRIVER, Direction.NORTH))
                .layer("@").layer("r").layer("r").layer("r").layer("r")
                .note("sky")
                .check((l, p) -> l.getBlockEntity(p) instanceof MassDriverBlockEntity be && be.isFormed()).build()));
        m.put("dyson_receiver", List.of(new Builder("dyson_receiver")
                .key('a', plain(DysonContent.DYSON_RECEIVER_ARRAY))
                .key('@', facing(DysonContent.DYSON_RECEIVER, Direction.NORTH))
                .layer("aaa", "a@a", "aaa")
                .note("daylight")
                .check((l, p) -> l.getBlockEntity(p) instanceof DysonReceiverBlockEntity be && be.isFormed()).build()));
        m.put("wind_turbine", List.of(new Builder("wind_turbine")
                .key('m', plain(PowerContent.TURBINE_MAST))
                .key('@', facing(() -> PowerContent.generator(Generator.WIND_TURBINE).get(), Direction.NORTH))
                // row 0: the 5x5 rotor disc in front of the nacelle (corners free), row 1: mast + nacelle
                .layer("     ", "  m  ")
                .layer("     ", "  m  ")
                .layer(" ___ ", "  m  ")
                .layer("_____", "  m  ")
                .layer("_____", "  @  ")
                .layer("_____", "     ")
                .layer(" ___ ", "     ")
                .note("rotor").note("mast_power")
                .check((l, p) -> WindTurbineBlockEntity.countMasts(l, p) >= WindTurbineBlockEntity.MIN_MAST
                        && l.getBlockEntity(p) instanceof PowerBlockEntity be && be.status() != PowerBlockEntity.ST_BLOCKED
                        && be.status() != PowerBlockEntity.ST_NO_MAST).build()));
        m.put("ender_anchor", List.of(new Builder("ender_anchor")
                .key('@', () -> EnderContent.ENDER_ANCHOR.get().defaultBlockState().setValue(EnderAnchorBlock.HALF, DoubleBlockHalf.LOWER))
                .key('u', () -> EnderContent.ENDER_ANCHOR.get().defaultBlockState().setValue(EnderAnchorBlock.HALF, DoubleBlockHalf.UPPER))
                .layer("@").layer("u")
                .note("pearl")
                .check((l, p) -> l.getBlockEntity(p) instanceof EnderAnchorBlockEntity
                        && l.getBlockState(p.above()).is(EnderContent.ENDER_ANCHOR.get())).build()));
        return m;
    }

    /**
     * A fission reactor of {@code size}³: casing frame, glass on the front and sides, the controller in
     * the front face, Access, Coolant and Power ports (and a Redstone Port on the 7). The interior is
     * filled column by column from {@code columns} (top-down map, first row at the front):
     * C = fuel channel, R = control rod, . = air.
     */
    private static Layout reactor(int size, String[] columns) {
        int n = size - 1, mid = size / 2;
        String id = "fission_reactor_" + size;
        Builder b = new Builder(id)
                .key('#', plain(PowerContent.REACTOR_CASING))
                .key('g', plain(PowerContent.REACTOR_GLASS))
                .key('C', plain(PowerContent.REACTOR_FUEL_CHANNEL))
                .key('R', plain(PowerContent.REACTOR_CONTROL_ROD))
                .key('@', facing(PowerContent.REACTOR_CONTROLLER, Direction.NORTH))
                .key('A', facing(PowerContent.REACTOR_ACCESS_PORT, Direction.WEST))
                .key('W', facing(PowerContent.REACTOR_COOLANT_PORT, Direction.EAST))
                .key('P', facing(PowerContent.REACTOR_POWER_PORT, Direction.SOUTH))
                .key('S', facing(PowerContent.REACTOR_REDSTONE_PORT, Direction.UP));
        int ctrlY = 1;
        for (int y = 0; y < size; y++) {
            String[] rows = new String[size];
            for (int z = 0; z < size; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < size; x++) {
                    int onEdge = (x == 0 || x == n ? 1 : 0) + (y == 0 || y == n ? 1 : 0) + (z == 0 || z == n ? 1 : 0);
                    char c;
                    if (onEdge >= 2) {
                        c = '#';
                    } else if (onEdge == 1) {
                        if (z == 0) c = x == mid && y == ctrlY ? '@' : 'g';
                        else if (x == 0) c = z == mid && y == mid ? 'A' : 'g';
                        else if (x == n) c = z == mid && y == mid ? 'W' : 'g';
                        else if (z == n) c = x == mid && y == mid ? 'P' : '#';
                        else if (y == n) c = size == 7 && x == mid && z == mid ? 'S' : '#';
                        else c = '#';
                    } else {
                        char col = columns[z - 1].charAt(x - 1);
                        c = col == '.' ? '_' : col;
                    }
                    row.append(c);
                }
                rows[z] = row.toString();
            }
            b.layer(rows);
        }
        b.note("reactor_" + size);
        b.check((l, p) -> ReactorStructure.scan(l, p, facingOf(l, p)).valid());
        return b.build();
    }

    /** The 7x7x3 tokamak from {@link TokamakStructure#role}: glass windows on top, two Fusion Ports underneath. */
    private static Layout tokamak() {
        Builder b = new Builder("tokamak")
                .key('M', plain(PowerContent.FUSION_MAGNET))
                .key('#', plain(PowerContent.FUSION_CASING))
                .key('g', plain(PowerContent.REACTOR_GLASS))
                .key('P', facing(PowerContent.FUSION_PORT, Direction.DOWN))
                .key('@', facing(PowerContent.TOKAMAK_CORE, Direction.NORTH));
        for (int dy = -1; dy <= 1; dy++) {
            String[] rows = new String[7];
            for (int dz = -3; dz <= 3; dz++) {
                StringBuilder row = new StringBuilder();
                for (int dx = -3; dx <= 3; dx++) {
                    int d = Math.max(Math.abs(dx), Math.abs(dz));
                    row.append(switch (TokamakStructure.role(dx, dy, dz)) {
                        case CORE -> '@';
                        case MAGNET -> 'M';
                        case CHANNEL -> '_';
                        case CASING -> dy == -1 && dx == 0 && Math.abs(dz) == 3 ? 'P'
                                : dy == 1 && d == 2 && (dx == 0 || dz == 0) ? 'g' : '#';
                    });
                }
                rows[dz + 3] = row.toString();
            }
            b.layer(rows);
        }
        b.note("tokamak");
        b.check((l, p) -> TokamakStructure.scan(l, p).valid());
        return b.build();
    }
}
