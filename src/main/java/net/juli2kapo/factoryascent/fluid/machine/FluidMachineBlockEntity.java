package net.juli2kapo.factoryascent.fluid.machine;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.FluidNeighbors;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.TankPorts;
import net.juli2kapo.factoryascent.machine.MachineEnergy;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.juli2kapo.factoryascent.util.NeighborCaches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Shared plumbing of the fluid machines: tanks (exposed to pipes through {@link TankPorts}), an
 * FE buffer (filled by cables for consumers, emptied into cables for producers), a small
 * inventory, pushing output tanks into neighbouring pipes and tanks, GUI sync and drops.
 */
public abstract class FluidMachineBlockEntity extends BlockEntity implements MenuProvider {
    // status codes (status.factoryascent.fluid.<key>)
    public static final int ST_IDLE = 0, ST_RUNNING = 1, ST_NO_POWER = 2, ST_NO_INPUT = 3, ST_FULL = 4, ST_HEATING = 5,
            ST_NO_WATER = 6, ST_INCOMPLETE = 7, ST_NO_DEPOSIT = 8, ST_NO_FUEL = 9, ST_NO_AIR = 10, ST_NO_SOURCE = 11,
            ST_SPINNING_UP = 12, ST_NO_TARGET = 13, ST_DIGESTING = 14;
    public static final String[] STATUS_KEYS = {"idle", "running", "no_power", "no_input", "full", "heating", "no_water",
            "incomplete", "no_deposit", "no_fuel", "no_air", "no_source", "spinning_up", "no_target", "digesting"};

    protected final FluidMachine machine;
    protected final MachineEnergy energy = new MachineEnergy(this::setChanged);
    protected final Stacks inventory;
    protected final List<FluidTank> tanks = new ArrayList<>();
    protected TankPorts ports;
    protected final NeighborCaches neighbors = new NeighborCaches(this);
    protected final FluidNeighbors fluidNeighbors = new FluidNeighbors(this);
    protected final FluidMachineData data = new FluidMachineData();
    private final ResourceHandler<ItemResource> automation = new Automation();
    private final EnergyHandler energyPort = new EnergyPort();
    protected int status = ST_IDLE;
    /** FE made (producers) or used (consumers) last tick. */
    protected int lastRate;
    /** 0..1000 progress for the screen's arrow. */
    protected int progress;
    private boolean lastActive;
    /** Client only: animation state for renderers. */
    public float animAngle, animSpeed, animLastTime = -1;

    protected FluidMachineBlockEntity(FluidMachine machine, BlockPos pos, BlockState state) {
        super(FluidContent.machineType(machine).get(), pos, state);
        this.machine = machine;
        this.inventory = new Stacks(Math.max(0, machine.slots().stream().mapToInt(FluidMachine.SlotSpec::index).max().orElse(-1) + 1));
    }

    /** Subclasses add their tanks in their constructor and then call this. */
    protected final void finishTanks() {
        ports = new TankPorts(tanks);
    }

    protected FluidTank addTank(FluidTank tank) {
        tanks.add(tank);
        return tank;
    }

    // ---------------------------------------------------------------- hooks

    /** One server tick; returns whether the machine is working (lights its ACTIVE state). */
    protected abstract boolean tick(ServerLevel level);

    /** Whether an item may go in that slot (players and pipes). */
    public boolean isItemValid(int slot, ItemResource resource) {
        return false;
    }

    public boolean isOutputSlot(int slot) {
        return machine.slots().stream().anyMatch(s -> s.index() == slot && s.output());
    }

    /** Up to 4 machine-specific numbers for the screen. */
    protected void writeExtra(int[] extra) {}

    /** Sides output tanks push to (all six by default). */
    protected Direction[] pushSides() {
        return Direction.values();
    }

    // ---------------------------------------------------------------- tick

    public final void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean active = tick(level);
        for (FluidTank t : tanks) {
            if (!t.pipesFill() && !t.isEmpty()) fluidNeighbors.push(t, Math.max(250, t.capacity() / 16), pushSides());
        }
        if (machine.power() == FluidMachine.Power.PRODUCER) EnergyUtil.push(neighbors, energy, Math.max(1, energy.capacity() / 20), Direction.values());
        state = getBlockState(); // tick() may have changed it (FORMED)
        if (state.hasProperty(FluidMachineBlock.ACTIVE) && state.getValue(FluidMachineBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(FluidMachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        if (active != lastActive) {
            lastActive = active;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        data.update(this);
    }

    // ---------------------------------------------------------------- access

    public FluidMachine machine() {
        return machine;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public Stacks inventory() {
        return inventory;
    }

    public List<FluidTank> tanks() {
        return tanks;
    }

    public FluidMachineData data() {
        return data;
    }

    public int status() {
        return status;
    }

    public int lastRate() {
        return lastRate;
    }

    public int progress() {
        return progress;
    }

    int[] extras() {
        int[] e = new int[FluidMachineData.EXTRA];
        writeExtra(e);
        return e;
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return ports;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return inventory.size() == 0 ? null : automation;
    }

    public @Nullable EnergyHandler energyHandler(@Nullable Direction side) {
        return machine.power() == FluidMachine.Power.NONE ? null : energyPort;
    }

    /** Whether the machine is working, as seen by renderers (synced). */
    public boolean active() {
        return lastActive;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new FluidMachineMenu(id, playerInventory, this);
    }

    // ---------------------------------------------------------------- persistence & sync

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        energy.deserialize(input.childOrEmpty("energy"));
        for (int i = 0; i < tanks.size(); i++) tanks.get(i).load(input, "tank" + i);
        lastActive = input.getBooleanOr("active", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        energy.serialize(output.child("energy"));
        for (int i = 0; i < tanks.size(); i++) tanks.get(i).save(output, "tank" + i);
        output.putBoolean("active", lastActive);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            NonNullList<ItemStack> drops = NonNullList.create();
            for (int i = 0; i < inventory.size(); i++) if (!inventory.stack(i).isEmpty()) drops.add(inventory.stack(i).copy());
            Containers.dropContents(level, pos, drops);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        neighbors.clear();
        fluidNeighbors.clear();
    }

    // ---------------------------------------------------------------- inventory

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

        public boolean canAddOutput(int index, ItemStack stack) {
            ItemStack there = stacks.get(index);
            return there.isEmpty() || ItemStack.isSameItemSameComponents(there, stack)
                    && there.getCount() + stack.getCount() <= there.getMaxStackSize();
        }

        public boolean addOutput(int index, ItemStack stack) {
            if (!canAddOutput(index, stack)) return false;
            ItemStack there = stacks.get(index);
            if (there.isEmpty()) setStack(index, stack.copy());
            else {
                there.grow(stack.getCount());
                setChanged();
            }
            return true;
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

    /** Consumers take energy in, producers give it out. */
    private final class EnergyPort implements EnergyHandler {
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
            return machine.power() == FluidMachine.Power.CONSUMER ? energy.insert(amount, transaction) : 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return machine.power() == FluidMachine.Power.PRODUCER ? energy.extract(amount, transaction) : 0;
        }
    }
}
