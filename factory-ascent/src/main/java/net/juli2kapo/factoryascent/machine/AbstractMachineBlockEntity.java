package net.juli2kapo.factoryascent.machine;

import net.juli2kapo.factoryascent.item.UpgradeItem;
import net.juli2kapo.factoryascent.registry.ModBlockEntities;
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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Shared plumbing for every machine: energy buffer, inventory, GUI sync,
 * auto-eject and drops. Subclasses implement {@link #tickMachine}.
 */
public abstract class AbstractMachineBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_WORKING = 1;
    public static final int STATUS_NO_POWER = 2;
    public static final int STATUS_OUTPUT_FULL = 3;
    public static final int STATUS_NO_NODE = 4;
    public static final int STATUS_TIER_TOO_LOW = 5;
    public static final int STATUS_NO_FUEL = 6;
    public static final int STATUS_FULL = 7;
    public static final int STATUS_INCOMPLETE = 8;
    public static final int STATUS_NEEDS_CRANK = 9;

    protected final MachineType type;
    protected final MachineSlots slots;
    protected final MachineInventory inventory;
    protected final AutomationItemHandler automation;
    protected final MachineEnergy energy;
    protected final MachineData data = new MachineData();
    protected final NeighborCaches neighbors;

    protected boolean autoEject = true;
    protected int status = STATUS_IDLE;
    /** Energy moved last tick, in FE/t (consumed by machines, produced by generators). */
    protected int lastEnergyRate;
    private int ejectCooldown;

    protected AbstractMachineBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(ModBlockEntities.machine(type).get(), pos, state);
        this.type = type;
        this.slots = MachineSlots.of(type);
        this.inventory = new MachineInventory(this, slots);
        this.automation = new AutomationItemHandler(this, inventory);
        this.energy = new MachineEnergy(this::setChanged);
        this.neighbors = new NeighborCaches(this);
        configureEnergy();
    }

    public MachineType type() {
        return type;
    }

    public MachineInventory inventory() {
        return inventory;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public ContainerData containerData() {
        return data;
    }

    /** Sets the energy buffer limits for this machine. */
    protected abstract void configureEnergy();

    // ---------------------------------------------------------------- ticking

    public final void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean active = tickMachine(level);
        if (MachineBlock.isActive(state) != active) {
            level.setBlock(pos, state.setValue(MachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        if (autoEject && slots.outputs() > 0 && --ejectCooldown <= 0) {
            ejectCooldown = 4;
            ejectOutputs();
        }
        data.update(this);
    }

    /** Runs one tick of machine logic on the server. Returns whether the machine is working. */
    protected abstract boolean tickMachine(ServerLevel level);

    private int countUpgrades(UpgradeItem.Kind kind) {
        int count = 0;
        for (int i = 0; i < slots.upgrades(); i++) {
            if (inventory.stack(slots.firstUpgrade() + i).getItem() instanceof UpgradeItem u && u.kind() == kind) count++;
        }
        return count;
    }

    /** Speed multiplier from Speed Upgrades: +50% each. */
    public float speedMultiplier() {
        return 1f + 0.5f * countUpgrades(UpgradeItem.Kind.SPEED);
    }

    /**
     * Energy multiplier per unit of work: speed upgrades cost extra (power grows with speed^1.6
     * overall), each Energy Upgrade takes 20% off.
     */
    public float energyMultiplier() {
        return (float) (Math.pow(speedMultiplier(), 0.6) * Math.pow(0.8, countUpgrades(UpgradeItem.Kind.ENERGY)));
    }

    private void ejectOutputs() {
        for (int i = slots.firstOutput(); i < slots.firstUpgrade(); i++) {
            ItemStack stack = inventory.stack(i);
            if (stack.isEmpty()) continue;
            for (Direction dir : Direction.values()) {
                ResourceHandler<ItemResource> target = neighbors.items(dir);
                if (target == null) continue;
                int moved;
                try (Transaction tx = Transaction.openRoot()) {
                    moved = target.insert(ItemResource.of(stack), stack.getCount(), tx);
                    tx.commit();
                }
                if (moved > 0) {
                    stack.shrink(moved);
                    inventory.changed(i);
                    if (stack.isEmpty()) break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- items

    /** Whether a player (or the machine itself) may put this item in the slot. */
    public boolean isItemValid(int index, ItemResource resource) {
        return switch (slots.role(index)) {
            case UPGRADE -> resource.getItem() instanceof UpgradeItem;
            default -> true; // outputs: menu slots block players separately
        };
    }

    /** Extra rules for automated insertion (routing, recipe checks). */
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return isItemValid(index, resource);
    }

    public void onInventoryChanged(int index) {
        setChanged();
    }

    public int comparatorSignal() {
        int cap = energy.capacity();
        return cap <= 0 ? 0 : Math.round(15f * energy.energy() / cap);
    }

    // ---------------------------------------------------------------- capabilities

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return slots.size() == 0 ? null : automation;
    }

    public @Nullable EnergyHandler energyHandler(@Nullable Direction side) {
        return energy;
    }

    // ---------------------------------------------------------------- menu

    public int status() {
        return status;
    }

    public int lastEnergyRate() {
        return lastEnergyRate;
    }

    public boolean autoEject() {
        return autoEject;
    }

    public void toggleAutoEject() {
        autoEject = !autoEject;
        setChanged();
    }

    /** Progress of the current operation in [0, 1000]. */
    public int progressPermille() {
        return 0;
    }

    /** Throughput in operations (or items) per minute ×10, for the GUI. */
    public int ratePerMinuteX10() {
        return 0;
    }

    /** Machine-specific extra value for the GUI (node purity, lava count, burn progress…). */
    public int extraA() {
        return 0;
    }

    public int extraB() {
        return 0;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new MachineMenu(id, playerInventory, this);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        energy.deserialize(input.childOrEmpty("energy"));
        autoEject = input.getBooleanOr("auto_eject", true);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        energy.serialize(output.child("energy"));
        output.putBoolean("auto_eject", autoEject);
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
}
