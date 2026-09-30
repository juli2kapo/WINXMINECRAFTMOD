package net.juli2kapo.factoryascent.power;

import java.util.List;
import net.juli2kapo.factoryascent.machine.MachineEnergy;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.juli2kapo.factoryascent.util.NeighborCaches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Shared plumbing of the power ladder's generators (and the RTG): an FE buffer that other blocks
 * can only take from, a small inventory with input and output slots, pushing energy into cables
 * and machines around it, GUI sync ({@link PowerData}) and drops. Subclasses implement
 * {@link #tickGenerator} and describe their slots and read-outs.
 */
public abstract class PowerBlockEntity extends BlockEntity implements MenuProvider {
    // Status codes shown on the screen (status.factoryascent.power.<name>).
    public static final int ST_IDLE = 0, ST_RUNNING = 1, ST_NO_FUEL = 2, ST_FULL = 3, ST_HEATING = 4, ST_NO_WATER = 5,
            ST_NO_ROTATION = 6, ST_NO_SKY = 7, ST_NIGHT = 8, ST_NO_MAST = 9, ST_BLOCKED = 10, ST_NO_AIR = 11,
            ST_DIGESTING = 12, ST_NO_PELLET = 13;
    public static final String[] STATUS_KEYS = {"idle", "running", "no_fuel", "full", "heating", "no_water", "no_rotation",
            "no_sky", "night", "no_mast", "blocked", "no_air", "digesting", "no_pellet"};

    /** One slot of the screen: inventory index, position, and whether it only takes items out. */
    public record SlotSpec(int index, int x, int y, boolean output) {}

    protected final MachineEnergy energy = new MachineEnergy(this::setChanged);
    protected final Stacks inventory;
    protected final NeighborCaches neighbors = new NeighborCaches(this);
    protected final PowerData data = new PowerData();
    private final ResourceHandler<ItemResource> automation;
    private final EnergyHandler outputOnly;
    protected int status = ST_IDLE;
    /** FE produced last tick. */
    protected int lastRate;
    private float energyCarry;
    /** GameTests: pretend this block is (or isn't) somewhere airless; null = ask {@link PowerRules}. */
    public @Nullable Boolean testAirless;
    /** Client only: the angle and speed of the part the block entity renderer turns (rotor, flywheel...). */
    public float spinAngle, spinSpeed, spinLastTime = -1;

    protected PowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        this.inventory = new Stacks(slots);
        this.automation = new Automation();
        this.outputOnly = new OutputOnly();
        int out = Math.max(1, maxOutput());
        energy.configure(Math.max(10_000, out * 400), 0, out * 2);
    }

    // ---------------------------------------------------------------- subclass hooks

    /** Runs one server tick; returns whether it is producing (lights the block's ACTIVE state). */
    protected abstract boolean tickGenerator(ServerLevel level);

    /** Top output in FE/t (sizes the buffer and the push rate). */
    public abstract int maxOutput();

    public abstract List<SlotSpec> slotLayout();

    /** Whether the slot takes this item (players, pipes). Output slots never take items. */
    public boolean isItemValid(int slot, ItemResource resource) {
        return false;
    }

    public boolean isOutputSlot(int slot) {
        return slotLayout().stream().anyMatch(s -> s.index() == slot && s.output());
    }

    /** Faces energy is pushed out of. */
    protected Direction[] outputSides() {
        return Direction.values();
    }

    /** Machine-specific read-outs for the screen (up to {@link PowerData#EXTRA} values). */
    protected void writeExtra(int[] extra) {}

    /** No air here: fuel won't burn (the RTG, solar and magmatic heat don't care). */
    protected boolean airless(net.minecraft.world.level.Level level) {
        return testAirless != null ? testAirless : PowerRules.isAirless(level);
    }

    // ---------------------------------------------------------------- ticking

    public final void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean active = tickGenerator(level);
        if (state.hasProperty(PowerBlock.ACTIVE) && state.getValue(PowerBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(PowerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        EnergyUtil.push(neighbors, energy, Math.max(1, maxOutput() * 2), outputSides());
        data.update(this);
    }

    /** Adds {@code perTick} (fractional) FE to the buffer; returns the whole FE added. */
    protected int generate(float perTick) {
        float produced = perTick + energyCarry;
        int whole = (int) produced;
        energyCarry = produced - whole;
        int added = energy.produce(whole);
        lastRate = added;
        if (added > 0) setChanged();
        return added;
    }

    // ---------------------------------------------------------------- access

    public MachineEnergy energy() {
        return energy;
    }

    public Stacks inventory() {
        return inventory;
    }

    public PowerData data() {
        return data;
    }

    public int status() {
        return status;
    }

    public int lastRate() {
        return lastRate;
    }

    int[] extras() {
        int[] extra = new int[PowerData.EXTRA];
        writeExtra(extra);
        return extra;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return inventory.size() == 0 ? null : automation;
    }

    public @Nullable EnergyHandler energyHandler(@Nullable Direction side) {
        return outputOnly;
    }

    public int comparatorSignal() {
        return energy.capacity() <= 0 ? 0 : Math.round(15f * energy.energy() / energy.capacity());
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new PowerMenu(id, playerInventory, this);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        energy.deserialize(input.childOrEmpty("energy"));
        energyCarry = input.getFloatOr("energy_carry", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        energy.serialize(output.child("energy"));
        output.putFloat("energy_carry", energyCarry);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            NonNullList<ItemStack> drops = NonNullList.create();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.stack(i);
                if (!stack.isEmpty()) drops.add(stack.copy());
            }
            Containers.dropContents(level, pos, drops);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        neighbors.clear();
    }

    // ---------------------------------------------------------------- inventory

    /** The full inventory: the owner and players use it directly; pipes go through {@link Automation}. */
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

        /** Puts {@code stack} into an output slot (merging), returns false if there is no room. */
        public boolean addOutput(int index, ItemStack stack) {
            ItemStack there = stacks.get(index);
            if (there.isEmpty()) {
                setStack(index, stack.copy());
                return true;
            }
            if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() + stack.getCount() <= there.getMaxStackSize()) {
                there.grow(stack.getCount());
                setChanged();
                return true;
            }
            return false;
        }

        public boolean canAddOutput(int index, ItemStack stack) {
            ItemStack there = stacks.get(index);
            return there.isEmpty() || ItemStack.isSameItemSameComponents(there, stack)
                    && there.getCount() + stack.getCount() <= there.getMaxStackSize();
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return isOutputSlot(index) || isItemValid(index, resource);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }

    /** What pipes and hoppers see: insert into valid input slots, extract from output slots. */
    private final class Automation implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return inventory.size();
        }

        @Override
        public ItemResource getResource(int index) {
            return inventory.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return inventory.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return inventory.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !isOutputSlot(index) && isItemValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return isValid(index, resource) ? inventory.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return isOutputSlot(index) ? inventory.extract(index, resource, amount, transaction) : 0;
        }
    }

    /** Cables and other mods may take energy out, never put it in. */
    private final class OutputOnly implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return energy.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return energy.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return energy.extract(amount, transaction);
        }
    }
}
