package net.juli2kapo.factoryascent.fusion;

import java.util.List;
import net.juli2kapo.factoryascent.machine.MachineEnergy;
import net.juli2kapo.factoryascent.power.PowerConfig;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Quantum age: the Tokamak Core, heart of a fusion reactor ({@link TokamakStructure}).
 *
 * <p>Start-up: switch it on, then charge the magnets with {@code fusionStartupEnergy} FE through
 * Fusion Ports (at up to 131k FE/t). With the magnets charged and fuel loaded it ignites the
 * plasma with one Tritium Cell (a deuterium-tritium spark) and the charge is spent. Burning:
 * deuterium and helium-3 fuse into {@code fusionOutput} FE/t; a Deuterium Cell lasts
 * {@code deuteriumSeconds}, a Helium-3 {@code helium3Seconds}. When either runs out, or it is
 * switched off, the plasma cools down safely and the magnets must be charged again.
 *
 * <p>Containment failure: breaking the ring (any magnet, casing or blocking the plasma channel)
 * while the plasma burns causes a disruption: the plasma hits the wall in a blast (breaking blocks
 * only if the config says so), lightning arcs from the core, and the magnets need a new charge.
 */
public class TokamakCoreBlockEntity extends BlockEntity implements MenuProvider {
    public static final int DEUTERIUM = 0, HELIUM3 = 1, TRITIUM = 2, EMPTY_OUT = 3, SLOTS = 4;
    public static final int PUSH = 131_072, CHARGE_RATE = 131_072, IGNITION_TICKS = 60, DISRUPTION_TICKS = 200;
    public enum State { COLD, CHARGING, READY, IGNITING, RUNNING, DISRUPTED }

    private final MachineEnergy output = new MachineEnergy(this::setChanged);
    private final Stacks inventory = new Stacks();
    private final TokamakData data = new TokamakData();
    private final ResourceHandler<ItemResource> automation = new Automation();
    private final EnergyHandler ports = new Ports();

    private TokamakStructure.Result structure = new TokamakStructure.Result(TokamakStructure.Error.NEEDS_MAGNET, null, List.of());
    private State state = State.COLD;
    private boolean enabled;
    private long charge;
    private int timer;
    private int deuteriumBurn, helium3Burn;
    private int rate;
    private int scanCooldown;
    /** Client only: plasma ring spin for the renderer. */
    public float spinAngle, spinLastTime = -1, plasmaShown;

    public TokamakCoreBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.TOKAMAK_CORE_BE.get(), pos, state);
        int out = Math.max(1, outputPerTick());
        output.configure(Math.max(1_000_000, out * 40), 0, PUSH);
    }

    public static int outputPerTick() {
        return (int) Math.min(Integer.MAX_VALUE / 8, PowerConfig.get(PowerConfig.FUSION_OUTPUT) * PowerConfig.generatorMultiplier());
    }

    public static long startupEnergy() {
        return PowerConfig.get(PowerConfig.FUSION_STARTUP_ENERGY);
    }

    public static Item helium3() {
        return PowerContent.helium3();
    }

    // ---------------------------------------------------------------- ticking

    public void serverTick(ServerLevel level) {
        if (--scanCooldown <= 0) {
            scanCooldown = 20;
            rescan(level);
        }
        rate = 0;
        boolean burning = state == State.IGNITING || state == State.RUNNING;
        if (burning && !structure.valid()) {
            disruption(level);
        } else {
            switch (state) {
                case COLD, CHARGING, READY -> {
                    if (!enabled) state = State.COLD;
                    else if (charge < startupEnergy()) state = State.CHARGING;
                    else if (!hasFuel(true)) state = State.READY;
                    else if (structure.valid()) ignite(level);
                    else state = State.READY;
                }
                case IGNITING -> {
                    if (!enabled) shutDown(level);
                    else if (++timer >= IGNITION_TICKS) {
                        state = State.RUNNING;
                        timer = 0;
                        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(32))) {
                            PowerContent.award(p, "quantum_fusion");
                        }
                    }
                }
                case RUNNING -> {
                    if (!enabled) shutDown(level);
                    else burn(level);
                }
                case DISRUPTED -> {
                    if (++timer >= DISRUPTION_TICKS) {
                        state = State.COLD;
                        timer = 0;
                    }
                }
            }
        }
        boolean lit = state == State.IGNITING || state == State.RUNNING;
        BlockState bs = getBlockState();
        if (bs.hasProperty(TokamakCoreBlock.ACTIVE) && bs.getValue(TokamakCoreBlock.ACTIVE) != lit) {
            level.setBlock(worldPosition, bs.setValue(TokamakCoreBlock.ACTIVE, lit), Block.UPDATE_CLIENTS);
        }
        if (lit && level.getGameTime() % 40 == 0) {
            level.playSound(null, worldPosition, PowerContent.FUSION_HUM.get(), SoundSource.BLOCKS, 1.5f, 0.7f);
        }
        data.update(this);
        setChanged();
    }

    private void rescan(Level level) {
        List<BlockPos> old = structure.ports();
        structure = TokamakStructure.scan(level, worldPosition);
        for (BlockPos p : old) {
            if (!structure.ports().contains(p) && level.getBlockEntity(p) instanceof FusionPortBlockEntity port) port.link(null);
        }
        for (BlockPos p : structure.ports()) {
            if (level.getBlockEntity(p) instanceof FusionPortBlockEntity port) port.link(worldPosition);
        }
    }

    /** GameTests (and the screen, on opening) check the ring right away. */
    public void rescanNow(Level level) {
        scanCooldown = 20;
        rescan(level);
    }

    private boolean hasFuel(boolean withTritium) {
        return inventory.stack(DEUTERIUM).is(PowerContent.DEUTERIUM_CELL.get()) && inventory.stack(HELIUM3).is(helium3())
                && (!withTritium || inventory.stack(TRITIUM).is(PowerContent.TRITIUM_CELL.get()));
    }

    private void ignite(ServerLevel level) {
        if (!inventory.canAddOutput(EMPTY_OUT, new ItemStack(PowerContent.EMPTY_CELL.get()))) {
            state = State.READY;
            return;
        }
        inventory.stack(TRITIUM).shrink(1);
        inventory.addOutput(EMPTY_OUT, new ItemStack(PowerContent.EMPTY_CELL.get()));
        inventory.changed();
        charge = 0;
        state = State.IGNITING;
        timer = 0;
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2f, 0.5f);
    }

    private void burn(ServerLevel level) {
        int dTicks = PowerConfig.get(PowerConfig.FUSION_DEUTERIUM_SECONDS) * 20;
        int hTicks = PowerConfig.get(PowerConfig.FUSION_HELIUM3_SECONDS) * 20;
        if (deuteriumBurn <= 0) {
            if (!inventory.stack(DEUTERIUM).is(PowerContent.DEUTERIUM_CELL.get())
                    || !inventory.canAddOutput(EMPTY_OUT, new ItemStack(PowerContent.EMPTY_CELL.get()))) {
                shutDown(level);
                return;
            }
            inventory.stack(DEUTERIUM).shrink(1);
            inventory.addOutput(EMPTY_OUT, new ItemStack(PowerContent.EMPTY_CELL.get()));
            inventory.changed();
            deuteriumBurn = dTicks;
        }
        if (helium3Burn <= 0) {
            if (!inventory.stack(HELIUM3).is(helium3())) {
                shutDown(level);
                return;
            }
            inventory.stack(HELIUM3).shrink(1);
            inventory.changed();
            helium3Burn = hTicks;
        }
        deuteriumBurn--;
        helium3Burn--;
        rate = output.produce(outputPerTick());
    }

    /** A clean stop: the plasma cools, the charge is gone. */
    public void shutDown(Level level) {
        state = State.COLD;
        charge = 0;
        timer = 0;
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 0.5f);
    }

    /** Containment failure. */
    public void disruption(ServerLevel level) {
        state = State.DISRUPTED;
        timer = 0;
        charge = 0;
        deuteriumBurn = helium3Burn = 0;
        var random = level.getRandom();
        int dx = random.nextInt(5) - 2, dz = random.nextBoolean() ? 2 : -2;
        if (random.nextBoolean()) {
            int t = dx;
            dx = dz;
            dz = t;
        }
        BlockPos hit = worldPosition.offset(dx, 0, dz);
        boolean destroy = PowerConfig.get(PowerConfig.FUSION_DISRUPTION_DESTROYS_BLOCKS);
        level.explode(null, hit.getX() + 0.5, hit.getY() + 0.5, hit.getZ() + 0.5, 5f, false,
                destroy ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.snapTo(worldPosition.getX() + 0.5, worldPosition.getY() + 2, worldPosition.getZ() + 0.5);
            level.addFreshEntity(bolt);
        }
        Component msg = Component.translatable("message.factoryascent.fusion_disruption").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(32))) p.sendOverlayMessage(msg);
    }

    // ---------------------------------------------------------------- access

    public void toggle() {
        enabled = !enabled;
        setChanged();
    }

    public boolean enabled() {
        return enabled;
    }

    public State state() {
        return state;
    }

    public boolean running() {
        return state == State.RUNNING;
    }

    public long charge() {
        return charge;
    }

    /** GameTests: fill the magnets without a power plant. */
    public void setCharge(long fe) {
        charge = Math.min(fe, startupEnergy());
    }

    public int timer() {
        return timer;
    }

    public int rate() {
        return rate;
    }

    public int deuteriumBurn() {
        return deuteriumBurn;
    }

    public int helium3Burn() {
        return helium3Burn;
    }

    public TokamakStructure.Result structure() {
        return structure;
    }

    public MachineEnergy output() {
        return output;
    }

    public Stacks inventory() {
        return inventory;
    }

    public TokamakData data() {
        return data;
    }

    public EnergyHandler energyHandler() {
        return ports;
    }

    public ResourceHandler<ItemResource> itemHandler() {
        return automation;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new TokamakMenu(id, playerInventory, this);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        output.deserialize(input.childOrEmpty("energy"));
        enabled = input.getBooleanOr("enabled", false);
        charge = input.getLongOr("charge", 0L);
        int s = input.getIntOr("state", 0);
        state = State.values()[Math.max(0, Math.min(State.values().length - 1, s))];
        timer = input.getIntOr("timer", 0);
        deuteriumBurn = input.getIntOr("deuterium_burn", 0);
        helium3Burn = input.getIntOr("helium3_burn", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        inventory.serialize(out.child("inventory"));
        output.serialize(out.child("energy"));
        out.putBoolean("enabled", enabled);
        out.putLong("charge", charge);
        out.putInt("state", state.ordinal());
        out.putInt("timer", timer);
        out.putInt("deuterium_burn", deuteriumBurn);
        out.putInt("helium3_burn", helium3Burn);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            NonNullList<ItemStack> drops = NonNullList.create();
            for (int i = 0; i < SLOTS; i++) if (!inventory.stack(i).isEmpty()) drops.add(inventory.stack(i).copy());
            Containers.dropContents(level, pos, drops);
            if (level instanceof ServerLevel server && (this.state == State.RUNNING || this.state == State.IGNITING)) disruption(server);
            for (BlockPos p : structure.ports()) {
                if (level.getBlockEntity(p) instanceof FusionPortBlockEntity port) port.link(null);
            }
        }
    }

    // ---------------------------------------------------------------- inventory

    public static boolean isValid(int slot, ItemResource r) {
        return switch (slot) {
            case DEUTERIUM -> r.is(PowerContent.DEUTERIUM_CELL.get());
            case HELIUM3 -> r.is(helium3());
            case TRITIUM -> r.is(PowerContent.TRITIUM_CELL.get());
            default -> false;
        };
    }

    public final class Stacks extends ItemStacksResourceHandler {
        Stacks() {
            super(SLOTS);
        }

        public ItemStack stack(int i) {
            return stacks.get(i);
        }

        public void setStack(int i, ItemStack stack) {
            ItemStack old = stacks.set(i, stack);
            onContentsChanged(i, old);
        }

        void changed() {
            setChanged();
        }

        boolean canAddOutput(int i, ItemStack stack) {
            ItemStack there = stacks.get(i);
            return there.isEmpty() || ItemStack.isSameItemSameComponents(there, stack) && there.getCount() + stack.getCount() <= there.getMaxStackSize();
        }

        void addOutput(int i, ItemStack stack) {
            ItemStack there = stacks.get(i);
            if (there.isEmpty()) setStack(i, stack.copy());
            else there.grow(stack.getCount());
            setChanged();
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return index == EMPTY_OUT || TokamakCoreBlockEntity.isValid(index, resource);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }

    private final class Automation implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return SLOTS;
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
            return TokamakCoreBlockEntity.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return isValid(index, resource) ? inventory.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index == EMPTY_OUT ? inventory.extract(index, resource, amount, transaction) : 0;
        }
    }

    /** Fusion Ports: energy in charges the magnets (while switched on and cold), energy out while burning. */
    private final class Ports implements EnergyHandler {
        private final SnapshotJournal<Long> journal = new SnapshotJournal<>() {
            @Override
            protected Long createSnapshot() {
                return charge;
            }

            @Override
            protected void revertToSnapshot(Long snapshot) {
                charge = snapshot;
            }

            @Override
            protected void onRootCommit(Long original) {
                setChanged();
            }
        };

        @Override
        public long getAmountAsLong() {
            return running() ? output.getAmountAsLong() : charge;
        }

        @Override
        public long getCapacityAsLong() {
            return running() ? output.getCapacityAsLong() : startupEnergy();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (!enabled || state == State.RUNNING || state == State.IGNITING || state == State.DISRUPTED) return 0;
            int accepted = (int) Math.max(0, Math.min(Math.min(amount, CHARGE_RATE), startupEnergy() - charge));
            if (accepted > 0) {
                journal.updateSnapshots(transaction);
                charge += accepted;
            }
            return accepted;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return output.extract(amount, transaction);
        }
    }
}
