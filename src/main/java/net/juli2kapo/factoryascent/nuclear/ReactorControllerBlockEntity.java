package net.juli2kapo.factoryascent.nuclear;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.machine.MachineEnergy;
import net.juli2kapo.factoryascent.power.PowerConfig;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.juli2kapo.factoryascent.util.NeighborCaches;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The brain of a fission reactor.
 *
 * <p><b>Heat.</b> Each fuel rod loaded (up to one per Fuel Channel) makes {@code rodHeat} heat per
 * tick, times the reactivity {@code 1 - insertion x authority} (authority: one Control Rod per four
 * channels gives full control), times the neighbour bonus {@code 1 + 0.3 x} the average number of
 * channels touching each channel: packed cores run hotter and burn their fuel better. That is why
 * size and layout matter.
 *
 * <p><b>Cooling and power.</b> Above 100 C the coolant boils: the walls can carry away
 * {@code 0.01 x volume x (T - 100)} heat per tick, using 1 mB of coolant for every 4 heat, and each
 * unit of heat carried away becomes {@code fePerHeat} FE. So a reactor settles at the temperature
 * where cooling matches heating, higher the more rods and the fewer control rods, as long as the
 * coolant lasts. Coolant: water buckets, ice (1000 mB), packed ice (9000), blue ice (81000),
 * Coolant Cells (4000), or water source blocks touching a Coolant Port (50 mB/t each).
 *
 * <p><b>Danger.</b> Without coolant the heat has nowhere to go. At {@code alarmTemperature} the
 * alarm sounds; at {@code meltdownTemperature} the core melts down: an explosion (breaking blocks
 * only if the config allows), the fuel is lost, and the channels turn into radioactive corium.
 * SCRAM (the screen's button, or a redstone signal on the controller or a Redstone Port) drops
 * every control rod in at once.
 *
 * <p><b>Waste.</b> A rod is spent after {@code rodLifeSeconds} at full power and comes out as a
 * Depleted Fuel Rod, which is very radioactive: keep it in a Waste Barrel, or centrifuge it into
 * nuclear waste and then pellets for RTGs. If there's no room for it, the reactor SCRAMs itself.
 */
public class ReactorControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FUEL_SLOTS = 6, FIRST_DEPLETED = 6, DEPLETED_SLOTS = 3, COOLANT_IN = 9, COOLANT_OUT = 10, SLOTS = 11;
    public static final int ENERGY_CAPACITY = 2_000_000, PUSH = 32_768;
    public static final float AMBIENT = 20f, BOILING = 100f;
    public static final int COOLANT_PER_SOURCE = 50;

    private final MachineEnergy energy = new MachineEnergy(this::setChanged);
    private final Stacks inventory = new Stacks();
    private final NeighborCaches neighbors = new NeighborCaches(this);
    private final ReactorData data = new ReactorData();
    private final ResourceHandler<ItemResource> automation = new Automation(false);
    private final ResourceHandler<ItemResource> coolantAutomation = new Automation(true);
    private final EnergyHandler outputOnly = new OutputOnly();

    private ReactorStructure structure = ReactorStructure.invalid(ReactorStructure.Error.TOO_SMALL, null, BlockPos.ZERO);
    private float temperature = AMBIENT;
    private float coolant;
    private int insertion = 50;
    private boolean scramButton;
    private boolean redstoneScram;
    private boolean wasteScram;
    private boolean meltedDown;
    private float burn;
    private float heat, removed;
    private int activeRods;
    private int rate;
    private int scanCooldown;
    private int alarmCooldown;
    /** GameTests: melt down without breaking the arena (null = follow the config). */
    public @Nullable Boolean testDestroyBlocks;

    public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.REACTOR_CONTROLLER_BE.get(), pos, state);
        energy.configure(ENERGY_CAPACITY, 0, PUSH);
    }

    // ---------------------------------------------------------------- simulation

    public void serverTick(ServerLevel level) {
        tickReactor(level);
        EnergyUtil.push(neighbors, energy, PUSH, Direction.values());
        data.update(this);
    }

    /** One tick of the reactor (GameTests call this in a loop to fast-forward). */
    public void tickReactor(ServerLevel level) {
        if (--scanCooldown <= 0 || !structure.valid() && level.getGameTime() % 10 == 0) {
            scanCooldown = 40;
            rescan(level);
        }
        redstoneScram = level.hasNeighborSignal(worldPosition);
        if (structure.valid() && !redstoneScram) {
            for (BlockPos port : structure.ports()) {
                if (level.getBlockState(port).getBlock() instanceof ReactorPortBlock b && b.kind() == ReactorPortBlock.Kind.REDSTONE
                        && level.hasNeighborSignal(port)) {
                    redstoneScram = true;
                    break;
                }
            }
        }
        fillCoolantFromItems();
        if (meltedDown || !structure.valid()) {
            heat = removed = 0;
            activeRods = 0;
            rate = 0;
            coolDown();
            updateActive(level, false);
            return;
        }
        int rods = 0;
        for (int i = 0; i < FUEL_SLOTS; i++) {
            if (inventory.stack(i).is(PowerContent.FUEL_ROD.get())) rods += inventory.stack(i).getCount();
        }
        activeRods = Math.min(rods, structure.channels());
        if (wasteScram && hasRoomForWaste()) wasteScram = false;
        float reactivity = 1f - insertionEffective() * authority();
        float k = neighbourBonus();
        heat = (float) (activeRods * PowerConfig.get(PowerConfig.REACTOR_ROD_HEAT) * reactivity * k);
        // burn-up: rod-ticks at full reactivity
        burn += activeRods * reactivity;
        int life = PowerConfig.get(PowerConfig.REACTOR_ROD_LIFE_SECONDS) * 20;
        while (burn >= life && activeRods > 0) {
            if (!spendRod()) {
                wasteScram = true;
                burn = life;
                break;
            }
            burn -= life;
        }
        // cooling
        int volume = structure.volume();
        float capacity = 0.01f * volume * Math.max(0f, temperature - BOILING);
        float possible = Math.min(capacity, coolant * 4f);
        removed = Math.max(0f, possible);
        coolant = Math.max(0f, coolant - removed / 4f);
        float passive = 0.0005f * volume * (temperature - AMBIENT);
        temperature += (heat - removed - passive) / (5f * volume);
        if (temperature < AMBIENT) temperature = AMBIENT;
        float fe = (float) (removed * PowerConfig.get(PowerConfig.REACTOR_FE_PER_HEAT) * PowerConfig.generatorMultiplier());
        rate = energy.produce(Math.round(fe));
        alarm(level);
        if (temperature >= PowerConfig.get(PowerConfig.MELTDOWN_TEMPERATURE)) {
            meltdown(level);
            return;
        }
        updateActive(level, heat > 0);
        setChanged();
    }

    private void coolDown() {
        if (temperature > AMBIENT) temperature = Math.max(AMBIENT, temperature - (temperature - AMBIENT) * 0.002f - 0.01f);
    }

    private void updateActive(Level level, boolean active) {
        BlockState state = getBlockState();
        if (state.hasProperty(ReactorControllerBlock.ACTIVE) && state.getValue(ReactorControllerBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(ReactorControllerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    public void rescan(Level level) {
        List<BlockPos> oldPorts = structure.ports();
        structure = ReactorStructure.scan(level, worldPosition, getBlockState().getValue(ReactorControllerBlock.FACING));
        for (BlockPos p : oldPorts) {
            if (!structure.ports().contains(p) && level.getBlockEntity(p) instanceof ReactorPortBlockEntity port
                    && worldPosition.equals(port.controller())) port.link(null);
        }
        for (BlockPos p : structure.ports()) {
            if (level.getBlockEntity(p) instanceof ReactorPortBlockEntity port) port.link(worldPosition);
        }
    }

    /** Moves one spent rod from the fuel slots to the depleted slots; false if there is no room. */
    private boolean spendRod() {
        if (!hasRoomForWaste()) return false;
        for (int i = 0; i < FUEL_SLOTS; i++) {
            ItemStack s = inventory.stack(i);
            if (s.is(PowerContent.FUEL_ROD.get())) {
                s.shrink(1);
                inventory.changed();
                ItemStack spent = new ItemStack(PowerContent.DEPLETED_FUEL_ROD.get());
                for (int j = FIRST_DEPLETED; j < FIRST_DEPLETED + DEPLETED_SLOTS; j++) {
                    if (inventory.addOutput(j, spent)) return true;
                }
                return true;
            }
        }
        return false;
    }

    private boolean hasRoomForWaste() {
        ItemStack spent = new ItemStack(PowerContent.DEPLETED_FUEL_ROD.get());
        for (int j = FIRST_DEPLETED; j < FIRST_DEPLETED + DEPLETED_SLOTS; j++) {
            if (inventory.canAddOutput(j, spent)) return true;
        }
        return false;
    }

    public int coolantCapacity() {
        return 16_000 + 1000 * structure.interior();
    }

    /** mB of coolant an item gives, and what's left of it. */
    public static int coolantValue(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) return 1000;
        if (stack.is(Items.ICE)) return 1000;
        if (stack.is(Items.PACKED_ICE)) return 9000;
        if (stack.is(Items.BLUE_ICE)) return 81000;
        if (stack.is(PowerContent.COOLANT_CELL.get())) return 4000;
        return 0;
    }

    private static ItemStack coolantRemainder(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) return new ItemStack(Items.BUCKET);
        if (stack.is(PowerContent.COOLANT_CELL.get())) return new ItemStack(PowerContent.EMPTY_CELL.get());
        return ItemStack.EMPTY;
    }

    private void fillCoolantFromItems() {
        ItemStack in = inventory.stack(COOLANT_IN);
        int value = coolantValue(in);
        if (value <= 0 || coolant + value > Math.max(coolantCapacity(), value)) return;
        ItemStack rest = coolantRemainder(in);
        if (!rest.isEmpty() && !inventory.canAddOutput(COOLANT_OUT, rest)) return;
        in.shrink(1);
        inventory.changed();
        if (!rest.isEmpty()) inventory.addOutput(COOLANT_OUT, rest);
        coolant += value;
    }

    /** Called by Coolant Ports: water pumped in from source blocks next to them. */
    public void addCoolant(float mB) {
        coolant = Math.min(Math.max(coolant, coolantCapacity()), coolant + mB);
    }

    private void alarm(ServerLevel level) {
        if (temperature < PowerConfig.get(PowerConfig.ALARM_TEMPERATURE)) {
            alarmCooldown = 0;
            return;
        }
        if (--alarmCooldown > 0) return;
        alarmCooldown = 30;
        level.playSound(null, worldPosition, PowerContent.REACTOR_ALARM.get(), SoundSource.BLOCKS, 3f, 1f);
        Component msg = Component.translatable("message.factoryascent.reactor_alarm", Math.round(temperature),
                PowerConfig.get(PowerConfig.MELTDOWN_TEMPERATURE)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(32))) {
            p.sendOverlayMessage(msg);
        }
    }

    /** The core melts: explosion, lost fuel, corium where the channels were. */
    public void meltdown(ServerLevel level) {
        if (meltedDown) return;
        meltedDown = true;
        heat = removed = 0;
        rate = 0;
        BlockPos c = structure.center();
        boolean destroy = testDestroyBlocks != null ? testDestroyBlocks : PowerConfig.get(PowerConfig.MELTDOWN_DESTROYS_BLOCKS);
        for (int i = 0; i < FIRST_DEPLETED + DEPLETED_SLOTS; i++) inventory.setStack(i, ItemStack.EMPTY);
        List<BlockPos> channels = new ArrayList<>(structure.channelBlocks());
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c).inflate(48))) {
            PowerContent.award(p, "industrial_meltdown");
            p.sendSystemMessage(Component.translatable("message.factoryascent.meltdown").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        setChanged();
        if (PowerConfig.get(PowerConfig.MELTDOWN_LEAVES_CORIUM)) {
            for (BlockPos p : channels) level.setBlock(p, PowerContent.CORIUM.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        float power = (float) (PowerConfig.get(PowerConfig.MELTDOWN_EXPLOSION_POWER) * (1 + Math.min(1f, structure.interior() / 125f)));
        if (power > 0) {
            level.explode(null, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, power, destroy,
                    destroy ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        }
        if (destroy && PowerConfig.get(PowerConfig.MELTDOWN_LEAVES_CORIUM)) {
            // molten core splashed into the crater
            var random = level.getRandom();
            int splashes = 2 + channels.size() / 3;
            for (int i = 0; i < splashes; i++) {
                BlockPos p = c.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
                while (p.getY() > level.getMinY() && level.getBlockState(p.below()).isAir()) p = p.below();
                if (level.getBlockState(p).isAir()) level.setBlock(p, PowerContent.CORIUM.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (!isRemoved()) updateActive(level, false);
    }

    // ---------------------------------------------------------------- controls & read-outs

    public float authority() {
        return structure.channels() == 0 ? 0 : Math.min(1f, structure.controlRods() * 4f / structure.channels());
    }

    public float neighbourBonus() {
        return 1f + 0.3f * structure.neighbours();
    }

    public boolean scrammed() {
        return scramButton || redstoneScram || wasteScram;
    }

    public float insertionEffective() {
        return scrammed() ? 1f : insertion / 100f;
    }

    public void setInsertion(int percent) {
        insertion = Math.max(0, Math.min(100, percent));
        setChanged();
    }

    public void toggleScram() {
        scramButton = !scramButton;
        setChanged();
    }

    public int insertion() {
        return insertion;
    }

    public ReactorStructure structure() {
        return structure;
    }

    public float temperature() {
        return temperature;
    }

    public void setTemperature(float t) {
        temperature = t;
    }

    public float coolant() {
        return coolant;
    }

    public float heat() {
        return heat;
    }

    /** Heat the coolant carried away last tick. */
    public float removedHeat() {
        return removed;
    }

    public int activeRods() {
        return activeRods;
    }

    public boolean meltedDown() {
        return meltedDown;
    }

    public boolean redstoneScram() {
        return redstoneScram;
    }

    public boolean scramButton() {
        return scramButton;
    }

    public boolean wasteScram() {
        return wasteScram;
    }

    public int rate() {
        return rate;
    }

    public float burnFraction() {
        return burn / (PowerConfig.get(PowerConfig.REACTOR_ROD_LIFE_SECONDS) * 20f);
    }

    /** GameTests: jump the current rods' burn-up. */
    public void setBurn(float rodTicks) {
        burn = rodTicks;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public Stacks inventory() {
        return inventory;
    }

    public ReactorData data() {
        return data;
    }

    public int comparatorSignal() {
        return Math.max(0, Math.min(15, Math.round(15f * (temperature - AMBIENT) / (PowerConfig.get(PowerConfig.MELTDOWN_TEMPERATURE) - AMBIENT))));
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler() {
        return automation;
    }

    public @Nullable ResourceHandler<ItemResource> coolantHandler() {
        return coolantAutomation;
    }

    public EnergyHandler energyHandler() {
        return outputOnly;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ReactorMenu(id, playerInventory, this);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        energy.deserialize(input.childOrEmpty("energy"));
        temperature = input.getFloatOr("temperature", AMBIENT);
        coolant = input.getFloatOr("coolant", 0f);
        insertion = input.getIntOr("insertion", 50);
        scramButton = input.getBooleanOr("scram", false);
        meltedDown = input.getBooleanOr("melted_down", false);
        burn = input.getFloatOr("burn", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        energy.serialize(output.child("energy"));
        output.putFloat("temperature", temperature);
        output.putFloat("coolant", coolant);
        output.putInt("insertion", insertion);
        output.putBoolean("scram", scramButton);
        output.putBoolean("melted_down", meltedDown);
        output.putFloat("burn", burn);
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
            for (BlockPos p : structure.ports()) {
                if (level.getBlockEntity(p) instanceof ReactorPortBlockEntity port) port.link(null);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        neighbors.clear();
    }

    // ---------------------------------------------------------------- inventory

    public static boolean isFuelSlot(int i) {
        return i < FUEL_SLOTS;
    }

    public static boolean isOutputSlot(int i) {
        return i >= FIRST_DEPLETED && i < FIRST_DEPLETED + DEPLETED_SLOTS || i == COOLANT_OUT;
    }

    public static boolean isValid(int i, ItemResource r) {
        if (isFuelSlot(i)) return r.is(PowerContent.FUEL_ROD.get());
        if (i == COOLANT_IN) return coolantValue(r.toStack(1)) > 0;
        return false;
    }

    public final class Stacks extends ItemStacksResourceHandler {
        Stacks() {
            super(SLOTS);
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

        boolean addOutput(int index, ItemStack stack) {
            if (!canAddOutput(index, stack)) return false;
            ItemStack there = stacks.get(index);
            if (there.isEmpty()) setStack(index, stack.copy());
            else {
                there.grow(stack.getCount());
                setChanged();
            }
            return true;
        }

        boolean canAddOutput(int index, ItemStack stack) {
            ItemStack there = stacks.get(index);
            return there.isEmpty() || ItemStack.isSameItemSameComponents(there, stack)
                    && there.getCount() + stack.getCount() <= there.getMaxStackSize();
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return isOutputSlot(index) || ReactorControllerBlockEntity.isValid(index, resource);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }

    /** Access Ports (and the controller's faces): fuel and coolant in, spent rods and empties out. */
    private final class Automation implements ResourceHandler<ItemResource> {
        private final boolean coolantOnly;

        Automation(boolean coolantOnly) {
            this.coolantOnly = coolantOnly;
        }

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
            if (coolantOnly && index != COOLANT_IN) return false;
            return !isOutputSlot(index) && ReactorControllerBlockEntity.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return isValid(index, resource) ? inventory.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (coolantOnly && index != COOLANT_OUT) return 0;
            return isOutputSlot(index) ? inventory.extract(index, resource, amount, transaction) : 0;
        }
    }

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
