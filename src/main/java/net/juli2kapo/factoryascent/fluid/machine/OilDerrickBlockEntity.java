package net.juli2kapo.factoryascent.fluid.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.FluidsConfig;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Automation age multiblock: the Oil Derrick (a pumpjack). The controller sits in the middle of
 * a 3×3 platform of Derrick Bases. Once formed it drills straight down (any depth) under its
 * platform for crude oil, and draws the connected oil pocket up, one source block (1000 mB)
 * every {@code derrickTicksPerBucket} ticks for {@code derrickEnergy} FE/t. The walking beam
 * nods while it pumps.
 */
public class OilDerrickBlockEntity extends FluidMachineBlockEntity {
    private static final int MAX_POCKET = 4096, POCKET_RADIUS = 16;

    final FluidTank oil;
    private final List<BlockPos> pocket = new ArrayList<>();
    private boolean formed;
    private int depth = -1;
    private int timer;
    private int rescan;
    private boolean checkNow = true;

    /** GameTests: look at the structure and the ground again right away. */
    public void recheck() {
        checkNow = true;
        rescan = 0;
        pocket.clear();
    }

    public OilDerrickBlockEntity(BlockPos pos, BlockState state) {
        super(FluidMachine.OIL_DERRICK, pos, state);
        oil = addTank(new FluidTank(32_000, r -> r.getFluid().isSame(ModFluids.CRUDE_OIL.source()), this::setChanged).output());
        finishTanks();
        energy.configure(40_000, 2_000, 0);
    }

    public FluidTank oil() {
        return oil;
    }

    public boolean formed() {
        return formed;
    }

    public int pocketSize() {
        return pocket.size();
    }

    /** All eight blocks around the controller (same level) are Derrick Bases. */
    public static boolean structureValid(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !level.getBlockState(pos.offset(dx, 0, dz)).is(FluidContent.DERRICK_BASE.get())) return false;
            }
        }
        return true;
    }

    @Override
    protected boolean tick(ServerLevel level) {
        if (level.getGameTime() % 10 == 0 || !formed && checkNow) {
            checkNow = false;
            boolean now = structureValid(level, worldPosition);
            if (now != formed) {
                formed = now;
                BlockState s = getBlockState();
                if (s.getValue(FluidMachineBlock.FORMED) != now) level.setBlock(worldPosition, s.setValue(FluidMachineBlock.FORMED, now), Block.UPDATE_ALL);
                if (now) {
                    for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(8))) {
                        FluidContent.award(p, "automation_derrick");
                    }
                }
            }
        }
        if (!formed) {
            status = ST_INCOMPLETE;
            lastRate = 0;
            return false;
        }
        if (pocket.isEmpty() && (rescan-- <= 0)) {
            rescan = 100;
            scan(level);
        }
        if (pocket.isEmpty()) {
            status = ST_NO_DEPOSIT;
            lastRate = 0;
            return false;
        }
        if (oil.space() < 1000) {
            status = ST_FULL;
            lastRate = 0;
            return false;
        }
        int use = FluidsConfig.get(FluidsConfig.DERRICK_ENERGY);
        if (energy.energy() < use) {
            status = ST_NO_POWER;
            lastRate = 0;
            return false;
        }
        energy.consume(use);
        lastRate = use;
        int interval = FluidsConfig.get(FluidsConfig.DERRICK_INTERVAL);
        progress = timer * 1000 / Math.max(1, interval);
        if (++timer >= interval) {
            timer = 0;
            while (!pocket.isEmpty()) {
                BlockPos p = pocket.remove(pocket.size() - 1);
                FluidState f = level.getFluidState(p);
                if (f.isSource() && f.getType().isSame(ModFluids.CRUDE_OIL.source())) {
                    // the emptied rock settles: an oil pocket leaves stone behind, not a cave
                    level.setBlock(p, p.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), 3);
                    oil.forceFill(ModFluids.CRUDE_OIL.source(), 1000);
                    break;
                }
            }
        }
        status = ST_RUNNING;
        setChanged();
        return true;
    }

    /** Finds the nearest crude oil under the platform and the pocket it belongs to. */
    public void scan(Level level) {
        pocket.clear();
        depth = -1;
        BlockPos found = null;
        for (int y = worldPosition.getY() - 1; y >= level.getMinY() && found == null; y--) {
            for (int dx = -1; dx <= 1 && found == null; dx++) {
                for (int dz = -1; dz <= 1 && found == null; dz++) {
                    BlockPos p = new BlockPos(worldPosition.getX() + dx, y, worldPosition.getZ() + dz);
                    if (isOil(level, p)) found = p;
                }
            }
        }
        if (found == null) return;
        depth = worldPosition.getY() - found.getY();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(found);
        seen.add(found);
        List<BlockPos> sources = new ArrayList<>();
        while (!open.isEmpty() && seen.size() < MAX_POCKET) {
            BlockPos p = open.poll();
            if (level.getFluidState(p).isSource()) sources.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || Math.abs(n.getX() - found.getX()) > POCKET_RADIUS || Math.abs(n.getZ() - found.getZ()) > POCKET_RADIUS
                        || Math.abs(n.getY() - found.getY()) > POCKET_RADIUS || !level.isLoaded(n)) continue;
                if (!level.getFluidState(n).getType().isSame(ModFluids.CRUDE_OIL.source())
                        && !level.getFluidState(n).getType().isSame(ModFluids.CRUDE_OIL.flowing())) continue;
                seen.add(n);
                open.add(n);
            }
        }
        // pump the deepest first, so the pocket empties from the bottom up and never spills
        sources.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        pocket.addAll(sources);
    }

    private static boolean isOil(Level level, BlockPos p) {
        FluidState f = level.getFluidState(p);
        return f.getType().isSame(ModFluids.CRUDE_OIL.source()) || f.getType().isSame(ModFluids.CRUDE_OIL.flowing());
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = pocket.size();
        extra[1] = depth;
        extra[2] = formed ? 1 : 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        timer = input.getIntOr("timer", 0);
        formed = input.getBooleanOr("formed", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("timer", timer);
        output.putBoolean("formed", formed);
    }
}
