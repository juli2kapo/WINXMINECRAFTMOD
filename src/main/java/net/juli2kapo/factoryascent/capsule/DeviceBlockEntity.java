package net.juli2kapo.factoryascent.capsule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * What the small capsule and phone devices share: a slot inventory (also their automation
 * handler: {@link #isValid} filters what goes in), an optional FE buffer, saving, dropping the
 * contents when broken, and the {@link DeviceBlock#ACTIVE} model switch.
 */
public abstract class DeviceBlockEntity extends BlockEntity implements MenuProvider {
    public final Stacks inventory;
    protected final @Nullable SimpleEnergyHandler energy;

    protected DeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots, int energyCapacity, int maxInsert) {
        super(type, pos, state);
        this.inventory = new Stacks(slots);
        this.energy = energyCapacity <= 0 ? null : new SimpleEnergyHandler(energyCapacity, maxInsert, 0) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                setChanged();
            }
        };
    }

    /** Whether this item may sit in that slot (players and automation alike). */
    public abstract boolean isValid(int slot, ItemResource resource);

    /** Largest stack a slot takes. */
    public int slotLimit(int slot) {
        return 64;
    }

    public abstract void serverTick(ServerLevel level);

    /** The redstone input changed. */
    public void redstone(ServerLevel level, boolean powered) {}

    public @Nullable SimpleEnergyHandler energy() {
        return energy;
    }

    public int energyStored() {
        return energy == null ? 0 : energy.getAmountAsInt();
    }

    public int energyCapacity() {
        return energy == null ? 0 : energy.getCapacityAsInt();
    }

    /** Takes {@code amount} FE if the buffer holds it all. */
    protected boolean pay(int amount) {
        if (energy == null || energy.getAmountAsInt() < amount) return false;
        energy.set(energy.getAmountAsInt() - amount);
        return true;
    }

    /** Switches the model between idle and working. */
    protected void setActive(boolean active) {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (state.hasProperty(DeviceBlock.ACTIVE) && state.getValue(DeviceBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(DeviceBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        if (energy != null) energy.deserialize(input.childOrEmpty("energy"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        if (energy != null) energy.serialize(output.child("energy"));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            NonNullList<ItemStack> drops = NonNullList.create();
            for (int i = 0; i < inventory.size(); i++) {
                if (!inventory.stack(i).isEmpty()) drops.add(inventory.stack(i).copy());
            }
            Containers.dropContents(level, pos, drops);
        }
    }

    /** The slots. Live stacks may be changed in place; call {@link #changed} after. */
    public final class Stacks extends ItemStacksResourceHandler {
        Stacks(int size) {
            super(size);
        }

        public ItemStack stack(int index) {
            return stacks.get(index);
        }

        public void setStack(int index, ItemStack stack) {
            ItemStack old = stacks.set(index, stack);
            onContentsChanged(index, old);
        }

        public void changed() {
            setChanged();
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return DeviceBlockEntity.this.isValid(index, resource);
        }

        @Override
        protected int getCapacity(int index, ItemResource resource) {
            return Math.min(slotLimit(index), super.getCapacity(index, resource));
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }
}
