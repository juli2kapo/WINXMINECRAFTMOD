package net.juli2kapo.factoryascent.automation;

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
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Places blocks from its nine slots into the space in front of it, whenever that space is free
 * (air, water, grass…), one every 10 ticks at speed 1. Slots are used in order, so a breaker
 * pointing at the same space and a pipe feeding the placer make a block cycle (cobblestone
 * generator, tree-less log farm, a door that closes itself).
 */
public class BlockPlacerBlockEntity extends AbstractMachineBlockEntity {
    private static final int TICKS_PER_BLOCK = 10;
    private static final int ENERGY_PER_BLOCK = 160;

    private float progress;

    public BlockPlacerBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.BLOCK_PLACER, pos, state);
    }

    @Override
    protected void configureEnergy() {
        energy.configure(20_000, 2_000, 0);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        lastEnergyRate = 0;
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        BlockPos pos = worldPosition.relative(facing);
        int slot = nextSlot();
        if (slot < 0 || !level.getBlockState(pos).canBeReplaced()) {
            progress = 0;
            status = STATUS_IDLE;
            return false;
        }
        int cost = (int) Math.ceil(ENERGY_PER_BLOCK * energyMultiplier() * Config.MACHINE_ENERGY.get());
        if (energy.energy() < cost) {
            status = STATUS_NO_POWER;
            return false;
        }
        progress += (float) (type.speed() * speedMultiplier() * Config.MACHINE_SPEED.get());
        status = STATUS_WORKING;
        if (progress < TICKS_PER_BLOCK) return true;
        progress = 0;
        ItemStack stack = inventory.stack(slot);
        if (stack.getItem() instanceof BlockItem block
                && block.place(new DirectionalPlaceContext(level, pos, facing, stack, facing.getOpposite())).consumesAction()) {
            energy.consume(cost);
            lastEnergyRate = cost;
            inventory.changed(slot);
        }
        return true;
    }

    private int nextSlot() {
        for (int i = 0; i < slots.inputs(); i++) {
            if (inventory.stack(slots.firstInput() + i).getItem() instanceof BlockItem) return slots.firstInput() + i;
        }
        return -1;
    }

    @Override
    public boolean isItemValid(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.INPUT) return resource.getItem() instanceof BlockItem;
        return super.isItemValid(index, resource);
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return slots.role(index) == SlotRole.INPUT && isItemValid(index, resource);
    }

    @Override
    public int progressPermille() {
        return Math.min(1000, Math.round(progress * 1000 / TICKS_PER_BLOCK));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = input.getFloatOr("progress", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("progress", progress);
    }
}
