package net.juli2kapo.factoryascent.fluid.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.juli2kapo.factoryascent.fluid.FluidContainers;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.FluidsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Electric age: an electric pump. Put it just above a pool (lake, lava pit, oil lake): it finds
 * the source blocks of that fluid connected to the block under it, within {@code pumpRadius},
 * and pumps the farthest first (so the pool drains from its edges), {@code pumpEnergy} FE and
 * 1000 mB per block. Water sources stay by default (an endless supply). Pipes take the fluid out
 * of any side but the bottom; empty buckets in its slot are filled.
 */
public class PumpBlockEntity extends FluidMachineBlockEntity {
    public static final int BUCKET_IN = 0, BUCKET_OUT = 1;
    private static final int MAX_SCAN = 4096;

    final FluidTank tank;
    private final List<BlockPos> queue = new ArrayList<>();
    private Fluid queued = Fluids.EMPTY;
    private int cooldown;
    private int found;

    public PumpBlockEntity(BlockPos pos, BlockState state) {
        super(FluidMachine.PUMP, pos, state);
        tank = addTank(new FluidTank(16_000, r -> true, this::setChanged).output());
        finishTanks();
        energy.configure(20_000, 1_000, 0);
    }

    public FluidTank tank() {
        return tank;
    }

    @Override
    public boolean isItemValid(int slot, ItemResource resource) {
        return slot == BUCKET_IN && (resource.is(Items.BUCKET) || resource.is(net.juli2kapo.factoryascent.power.PowerContent.EMPTY_CELL.get()));
    }

    @Override
    protected Direction[] pushSides() {
        return new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    }

    @Override
    protected boolean tick(ServerLevel level) {
        FluidContainers.process(tank, inventory.stack(BUCKET_IN), inventory.stack(BUCKET_OUT),
                s -> inventory.setStack(BUCKET_OUT, s), inventory::changed, false, true);
        int cost = FluidsConfig.get(FluidsConfig.PUMP_ENERGY);
        int interval = FluidsConfig.get(FluidsConfig.PUMP_INTERVAL);
        progress = Math.min(1000, (interval - cooldown) * 1000 / Math.max(1, interval));
        if (cooldown > 0) {
            cooldown--;
            return status == ST_RUNNING;
        }
        cooldown = interval;
        if (tank.space() < 1000) {
            status = ST_FULL;
            return false;
        }
        if (energy.energy() < cost) {
            status = ST_NO_POWER;
            return false;
        }
        BlockPos target = next(level);
        if (target == null) {
            status = ST_NO_SOURCE;
            return false;
        }
        Fluid fluid = level.getFluidState(target).getType();
        Fluid source = fluid instanceof net.minecraft.world.level.material.FlowingFluid f ? f.getSource() : fluid;
        if (tank.forceFill(source, 1000) < 1000) {
            status = ST_FULL;
            return false;
        }
        energy.consume(cost);
        lastRate = cost / interval;
        boolean keep = source.isSame(Fluids.WATER) && FluidsConfig.get(FluidsConfig.PUMP_KEEPS_WATER);
        if (!keep) {
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            queue.remove(queue.size() - 1);
        }
        if (level.getRandom().nextInt(4) == 0) level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.4f, 0.8f);
        status = ST_RUNNING;
        setChanged();
        return true;
    }

    /** The next source block to pump (the end of the queue), rescanning when the queue is stale. */
    private BlockPos next(ServerLevel level) {
        while (!queue.isEmpty()) {
            BlockPos p = queue.get(queue.size() - 1);
            FluidState f = level.getFluidState(p);
            if (f.isSource() && f.getType().isSame(queued) && level.getBlockState(p).getBlock() instanceof LiquidBlock
                    && (tank.isEmpty() || tank.holds(f.getType()))) return p;
            queue.remove(queue.size() - 1);
        }
        scan(level);
        return queue.isEmpty() ? null : queue.get(queue.size() - 1);
    }

    /** Collects the source blocks connected to the block under the pump, nearest first (pumped from the end). */
    private void scan(ServerLevel level) {
        queue.clear();
        BlockPos start = worldPosition.below();
        FluidState startFluid = level.getFluidState(start);
        if (startFluid.isEmpty()) {
            found = 0;
            return;
        }
        Fluid fluid = startFluid.getType() instanceof net.minecraft.world.level.material.FlowingFluid f ? f.getSource() : startFluid.getType();
        if (!tank.isEmpty() && !tank.holds(fluid)) {
            found = 0;
            return;
        }
        queued = fluid;
        int r = FluidsConfig.get(FluidsConfig.PUMP_RADIUS);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start);
        seen.add(start);
        while (!open.isEmpty() && seen.size() < MAX_SCAN) {
            BlockPos p = open.poll();
            FluidState f = level.getFluidState(p);
            if (f.isSource() && level.getBlockState(p).getBlock() instanceof LiquidBlock) queue.add(p);
            for (Direction d : Direction.values()) {
                if (d == Direction.UP && p.getY() >= start.getY()) continue;
                BlockPos n = p.relative(d);
                if (Math.abs(n.getX() - start.getX()) > r || Math.abs(n.getZ() - start.getZ()) > r || start.getY() - n.getY() > r) continue;
                if (seen.contains(n) || !level.isLoaded(n)) continue;
                if (!level.getFluidState(n).getType().isSame(fluid)) continue;
                seen.add(n);
                open.add(n);
            }
        }
        found = queue.size();
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = found;
        extra[1] = FluidsConfig.get(FluidsConfig.PUMP_RADIUS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cooldown = input.getIntOr("cooldown", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("cooldown", cooldown);
    }
}
