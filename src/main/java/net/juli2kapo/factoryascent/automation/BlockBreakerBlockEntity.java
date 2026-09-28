package net.juli2kapo.factoryascent.automation;

import java.util.List;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.SlotRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Breaks the block in front of it and keeps the drops (ejected into an inventory touching it
 * when auto-eject is on). It gets a block's normal drops without a tool; put a tool in its slot
 * to use that tool's enchantments (Silk Touch, Fortune), which then wears one point per block.
 * Harder blocks take longer: 20 ticks plus 10 per point of hardness at speed 1. Unbreakable
 * blocks (bedrock, barriers) and fluids are left alone.
 */
public class BlockBreakerBlockEntity extends AbstractMachineBlockEntity {
    private static final int ENERGY_PER_BLOCK = 320;

    private float progress;
    private float needed;
    private BlockState target = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

    public BlockBreakerBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.BLOCK_BREAKER, pos, state);
    }

    @Override
    protected void configureEnergy() {
        energy.configure(40_000, 4_000, 0);
    }

    private BlockPos front() {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        return worldPosition.relative(facing);
    }

    private static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && !state.liquid() && state.getDestroySpeed(level, pos) >= 0;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        lastEnergyRate = 0;
        BlockPos pos = front();
        BlockState state = level.getBlockState(pos);
        if (!breakable(level, pos, state)) {
            progress = 0;
            status = STATUS_IDLE;
            return false;
        }
        if (state != target) {
            target = state;
            progress = 0;
            needed = 20 + 10 * Math.max(0, state.getDestroySpeed(level, pos));
        }
        if (MachineOutputs.emptySlots(inventory) == 0) {
            status = STATUS_OUTPUT_FULL;
            return false;
        }
        int cost = (int) Math.ceil(ENERGY_PER_BLOCK * energyMultiplier() * Config.MACHINE_ENERGY.get() / needed);
        if (energy.energy() < cost) {
            status = STATUS_NO_POWER;
            return false;
        }
        energy.consume(cost);
        lastEnergyRate = cost;
        progress += (float) (type.speed() * speedMultiplier() * Config.MACHINE_SPEED.get());
        status = STATUS_WORKING;
        if (progress >= needed) {
            progress = 0;
            breakBlock(level, pos, state);
        }
        return true;
    }

    private void breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        Player player = FakePlayerFactory.getMinecraft(level);
        if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, player)).isCanceled()) return;
        ItemStack tool = inventory.stack(slots.firstInput());
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.removeBlock(pos, false);
        for (ItemStack drop : drops) MachineOutputs.insertOrDrop(inventory, drop, level, worldPosition);
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, level, (net.minecraft.world.entity.LivingEntity) null, item -> {});
            inventory.changed(slots.firstInput());
        }
        target = level.getBlockState(pos);
    }

    @Override
    public boolean isItemValid(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.INPUT) {
            ItemStack stack = resource.toStack(1);
            return stack.isDamageableItem() || stack.has(net.minecraft.core.component.DataComponents.TOOL);
        }
        return super.isItemValid(index, resource);
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return slots.role(index) == SlotRole.INPUT && inventory.stack(index).isEmpty() && isItemValid(index, resource);
    }

    @Override
    public int progressPermille() {
        return needed <= 0 ? 0 : Math.min(1000, Math.round(progress / needed * 1000));
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
