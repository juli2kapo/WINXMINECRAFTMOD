package net.juli2kapo.factoryascent.farm;

import java.util.List;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.SlotRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Harvests fully grown crops (and melons/pumpkins) in a 9×9 field in front of it and replants
 * from the seeds in its input slots. Like Industrial Foregoing's plant gatherer + sower in one.
 */
public class AutoFarmerBlockEntity extends AbstractMachineBlockEntity {
    public static final int SIZE = 9;
    /** Work points per field position visited; with speed 1 that is one position every 5 ticks. */
    private static final int POINTS_PER_STEP = 5;
    private static final int ENERGY_PER_HARVEST = 200;

    private int cursor;
    private float progress;

    public AutoFarmerBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.AUTO_FARMER, pos, state);
    }

    @Override
    protected void configureEnergy() {
        energy.configure(40_000, 40_000, 0);
    }

    /** Field position for a cursor value: a 9×9 square starting one block in front of the machine. */
    private BlockPos fieldPos(int index) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        Direction right = facing.getClockWise();
        int forward = 1 + index / SIZE;
        int side = index % SIZE - SIZE / 2;
        return worldPosition.relative(facing, forward).relative(right, side);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        lastEnergyRate = 0;
        if (energy.energy() < ENERGY_PER_HARVEST) {
            status = STATUS_NO_POWER;
            return false;
        }
        progress += (float) (type.speed() * speedMultiplier() * Config.MACHINE_SPEED.get());
        boolean worked = false;
        while (progress >= POINTS_PER_STEP) {
            progress -= POINTS_PER_STEP;
            BlockPos pos = fieldPos(cursor);
            cursor = (cursor + 1) % (SIZE * SIZE);
            if (!level.isLoaded(pos)) continue;
            worked |= visit(level, pos);
        }
        status = worked || progress > 0 ? STATUS_WORKING : STATUS_IDLE;
        return true;
    }

    private boolean visit(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isRipe(state)) {
            if (!hasRoomForDrops()) {
                status = STATUS_OUTPUT_FULL;
                return false;
            }
            if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, FakePlayerFactory.getMinecraft(level))).isCanceled()) {
                return false;
            }
            List<ItemStack> drops = Block.getDrops(state, level, pos, null);
            level.removeBlock(pos, false);
            for (ItemStack drop : drops) {
                ItemStack rest = replantFrom(drop, level, pos, state);
                insertOutput(rest);
            }
            if (level.getBlockState(pos).isAir()) plantFromInputs(level, pos);
            energy.consume(ENERGY_PER_HARVEST);
            lastEnergyRate = ENERGY_PER_HARVEST;
            return true;
        }
        if (state.isAir()) {
            return plantFromInputs(level, pos);
        }
        return false;
    }

    private static boolean isRipe(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        if (state.getBlock() instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN);
    }

    /** Uses one seed from the drops to replant the same crop, returning what is left. */
    private ItemStack replantFrom(ItemStack drop, ServerLevel level, BlockPos pos, BlockState harvested) {
        if (!(harvested.getBlock() instanceof CropBlock) && !(harvested.getBlock() instanceof NetherWartBlock)) return drop;
        if (drop.getItem() instanceof BlockItem bi && bi.getBlock() == harvested.getBlock()
                && level.getBlockState(pos).isAir()) {
            BlockState planted = harvested.getBlock().defaultBlockState();
            if (planted.canSurvive(level, pos)) {
                level.setBlock(pos, planted, Block.UPDATE_ALL);
                drop.shrink(1);
            }
        }
        return drop;
    }

    private boolean plantFromInputs(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.is(Blocks.FARMLAND) && !below.is(Blocks.SOUL_SAND)) return false;
        for (int i = 0; i < slots.inputs(); i++) {
            ItemStack seed = inventory.stack(slots.firstInput() + i);
            if (!(seed.getItem() instanceof BlockItem bi)) continue;
            BlockState plant = bi.getBlock().defaultBlockState();
            if (!(bi.getBlock() instanceof CropBlock) && !(bi.getBlock() instanceof NetherWartBlock)) continue;
            if (!plant.canSurvive(level, pos)) continue;
            level.setBlock(pos, plant, Block.UPDATE_ALL);
            seed.shrink(1);
            inventory.changed(slots.firstInput() + i);
            return true;
        }
        return false;
    }

    private boolean hasRoomForDrops() {
        for (int i = slots.firstOutput(); i < slots.firstUpgrade(); i++) {
            if (inventory.stack(i).isEmpty()) return true;
        }
        return false;
    }

    private void insertOutput(ItemStack stack) {
        for (int i = slots.firstOutput(); i < slots.firstUpgrade() && !stack.isEmpty(); i++) {
            ItemStack s = inventory.stack(i);
            if (s.isEmpty()) {
                inventory.setStack(i, stack.copy());
                return;
            }
            if (ItemStack.isSameItemSameComponents(s, stack)) {
                int move = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(move);
                stack.shrink(move);
                inventory.changed(i);
            }
        }
    }

    @Override
    public boolean isItemValid(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.INPUT) {
            return resource.getItem() instanceof BlockItem bi
                    && (bi.getBlock() instanceof CropBlock || bi.getBlock() instanceof NetherWartBlock);
        }
        return super.isItemValid(index, resource);
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return slots.role(index) == SlotRole.INPUT && isItemValid(index, resource);
    }

    @Override
    public int progressPermille() {
        return cursor * 1000 / (SIZE * SIZE);
    }

    @Override
    public int extraA() {
        return SIZE;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cursor = input.getIntOr("cursor", 0);
        progress = input.getFloatOr("progress", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("cursor", cursor);
        output.putFloat("progress", progress);
    }

}
