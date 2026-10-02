package net.juli2kapo.factoryascent.automation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.SlotRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A 7×7 tree plantation in front of it: nine planting spots three blocks apart, level with the
 * machine, on dirt or grass. It replants with the saplings it harvested (output slots) before
 * using the ones in its input slots, feeds them bone meal if it
 * has any, and fells every tree that grows there: the whole trunk and its leaves, up to
 * {@link #MAX_BLOCKS} blocks, drops (logs, saplings, sticks, apples) into its outputs.
 */
public class TreeFarmBlockEntity extends AbstractMachineBlockEntity {
    public static final int SIZE = 7;
    public static final int SPACING = 3;
    public static final int MAX_BLOCKS = 256;
    private static final int POINTS_PER_SPOT = 20;
    private static final int ENERGY_PER_BLOCK = 24;
    private static final int ENERGY_PER_PLANT = 80;

    private int cursor;
    private float progress;

    public TreeFarmBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.TREE_FARM, pos, state);
    }

    @Override
    protected void configureEnergy() {
        energy.configure(60_000, 4_000, 0);
    }

    private static int spots() {
        int n = (SIZE + SPACING - 1) / SPACING;
        return n * n;
    }

    /** Planting spot {@code index}: rows start one block in front of the machine, centred on it. */
    public BlockPos spot(int index) {
        int n = (SIZE + SPACING - 1) / SPACING;
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        Direction right = facing.getClockWise();
        int forward = 1 + (index / n) * SPACING;
        int side = (index % n) * SPACING - SIZE / 2;
        return worldPosition.relative(facing, forward).relative(right, side);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        lastEnergyRate = 0;
        if (energy.energy() < ENERGY_PER_PLANT) {
            status = STATUS_NO_POWER;
            return false;
        }
        progress += (float) (type.speed() * speedMultiplier() * Config.MACHINE_SPEED.get());
        status = STATUS_WORKING;
        boolean worked = false;
        while (progress >= POINTS_PER_SPOT) {
            progress -= POINTS_PER_SPOT;
            BlockPos pos = spot(cursor);
            cursor = (cursor + 1) % spots();
            if (level.isLoaded(pos)) worked |= visit(level, pos);
        }
        return true;
    }

    private boolean visit(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.LOGS)) {
            if (MachineOutputs.emptySlots(inventory) < 2) {
                status = STATUS_OUTPUT_FULL;
                return false;
            }
            status = STATUS_WORKING;
            return fell(level, pos);
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock) return feed(level, pos, state);
        if (state.isAir()) return plant(level, pos);
        return false;
    }

    /** Every log connected to the trunk, then the leaves touching those logs. */
    private boolean fell(ServerLevel level, BlockPos base) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> found = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(base);
        seen.add(base);
        while (!queue.isEmpty() && found.size() < MAX_BLOCKS) {
            BlockPos p = queue.poll();
            BlockState s = level.getBlockState(p);
            boolean log = s.is(BlockTags.LOGS);
            if (!log && !s.is(BlockTags.LEAVES)) continue;
            found.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (n.getY() < base.getY() || Math.abs(n.getX() - base.getX()) > 6 || Math.abs(n.getZ() - base.getZ()) > 6
                                || n.getY() > base.getY() + 32 || !seen.add(n)) continue;
                        BlockState ns = level.getBlockState(n);
                        // leaves only spread the search from logs, so a forest isn't eaten through its canopy
                        if (ns.is(BlockTags.LOGS) || (log && ns.is(BlockTags.LEAVES))) queue.add(n);
                    }
                }
            }
        }
        var player = FakePlayerFactory.getMinecraft(level);
        int energyUsed = 0;
        for (BlockPos p : found) {
            BlockState s = level.getBlockState(p);
            if (energy.energy() < energyUsed + ENERGY_PER_BLOCK) break;
            if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, p, s, player)).isCanceled()) continue;
            for (ItemStack drop : Block.getDrops(s, level, p, level.getBlockEntity(p))) {
                MachineOutputs.insertOrDrop(inventory, drop, level, worldPosition);
            }
            level.removeBlock(p, false);
            energyUsed += ENERGY_PER_BLOCK;
        }
        if (!found.isEmpty()) level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, base,
                Block.getId(net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()));
        energy.consume(energyUsed);
        lastEnergyRate = energyUsed;
        return !found.isEmpty();
    }

    /**
     * Replants with the saplings the farm harvested itself (its output slots) first, then from the
     * input slots; any extra saplings stay in the outputs.
     */
    private boolean plant(ServerLevel level, BlockPos pos) {
        for (int slot : plantingOrder(slots)) {
            ItemStack stack = inventory.stack(slot);
            if (!stack.is(ItemTags.SAPLINGS) || !(stack.getItem() instanceof BlockItem bi)) continue;
            // Each sapling's own rule decides the soil (26.x's #dirt no longer contains grass blocks).
            BlockState sapling = bi.getBlock().defaultBlockState();
            if (!sapling.canSurvive(level, pos)) continue;
            level.setBlock(pos, sapling, Block.UPDATE_ALL);
            stack.shrink(1);
            inventory.changed(slot);
            energy.consume(ENERGY_PER_PLANT);
            lastEnergyRate = ENERGY_PER_PLANT;
            return true;
        }
        return false;
    }

    /** Slots searched for a sapling to plant: every output slot, then every input slot. */
    public static int[] plantingOrder(net.juli2kapo.factoryascent.machine.MachineSlots slots) {
        int[] order = new int[slots.outputs() + slots.inputs()];
        int n = 0;
        for (int i = 0; i < slots.outputs(); i++) order[n++] = slots.firstOutput() + i;
        for (int i = 0; i < slots.inputs(); i++) order[n++] = slots.firstInput() + i;
        return order;
    }

    private boolean feed(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BonemealableBlock plant)) return false;
        for (int i = 0; i < slots.inputs(); i++) {
            ItemStack stack = inventory.stack(slots.firstInput() + i);
            if (!stack.is(Items.BONE_MEAL)) continue;
            if (!plant.isValidBonemealTarget(level, pos, state)) return false;
            if (plant.isBonemealSuccess(level, level.getRandom(), pos, state)) plant.performBonemeal(level, level.getRandom(), pos, state);
            level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
            stack.shrink(1);
            inventory.changed(slots.firstInput() + i);
            return true;
        }
        return false;
    }

    @Override
    public boolean isItemValid(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.INPUT) return resource.toStack(1).is(ItemTags.SAPLINGS) || resource.is(Items.BONE_MEAL);
        return super.isItemValid(index, resource);
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return slots.role(index) == SlotRole.INPUT && isItemValid(index, resource);
    }

    @Override
    public int progressPermille() {
        return cursor * 1000 / spots();
    }

    @Override
    public int extraA() {
        return SIZE;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cursor = input.getIntOr("cursor", 0) % spots();
        progress = input.getFloatOr("progress", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("cursor", cursor);
        output.putFloat("progress", progress);
    }
}
