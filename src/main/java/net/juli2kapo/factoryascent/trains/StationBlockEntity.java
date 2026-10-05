package net.juli2kapo.factoryascent.trains;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.DelegatingEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EmptyEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * The Train Station's logic: which trains it stops, for how long, and the loading.
 *
 * <ul>
 * <li>Zone: the rails touching the station's sides (same level, one up or one down).</li>
 * <li>Stop: {@link #STOP_ALWAYS}, {@link #STOP_POWERED} (while the station gets a redstone signal)
 * or {@link #STOP_NEVER}. A stopping train is braked to a halt ({@link RollingStock#stationHold})
 * and, once stopped, waits {@link #dwell} seconds (0 = until nothing moved for 2 s); a
 * redstone-held train waits until the signal goes off. It is then released and not stopped again
 * until it has left the zone.</li>
 * <li>Transfer (every 4 ticks, per stopped vehicle in the zone): {@link #MODE_LOAD} moves items,
 * fluids and FE from the containers/tanks/batteries touching the station into the wagons (fuel
 * into a locomotive's bunker, water/diesel into its tank, FE into its battery);
 * {@link #MODE_UNLOAD} moves wagons' cargo and tank contents out into them.</li>
 * <li>Pipes connected to the station itself reach the docked vehicles (item, fluid and energy
 * capabilities delegate to the first one that has each).</li>
 * </ul>
 */
public class StationBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STOP_ALWAYS = 0, STOP_POWERED = 1, STOP_NEVER = 2;
    public static final int MODE_LOAD = 0, MODE_UNLOAD = 1, MODE_OFF = 2;
    public static final int MAX_DWELL = 300;

    int stopMode = STOP_ALWAYS;
    int dwell = 10;
    int transfer = MODE_LOAD;
    private int timer, idle;
    private boolean holding;
    private final Set<UUID> released = new HashSet<>();
    private List<RollingStock> docked = List.of();
    private int moved;

    private final ResourceHandler<ItemResource> itemsView = new DelegatingResourceHandler<>(this::dockedItems);
    private final ResourceHandler<FluidResource> fluidView = new DelegatingResourceHandler<>(this::dockedFluid);
    private final EnergyHandler energyView = new DelegatingEnergyHandler(this::dockedEnergy);

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(TrainContent.STATION_BE.get(), pos, state);
    }

    public int stopMode() {
        return stopMode;
    }

    public int dwell() {
        return dwell;
    }

    public int transferMode() {
        return transfer;
    }

    public boolean holding() {
        return holding;
    }

    public List<RollingStock> docked() {
        return docked;
    }

    // ---------------------------------------------------------------- the zone

    /** Rail blocks the station serves. */
    public List<BlockPos> zoneRails() {
        List<BlockPos> out = new ArrayList<>();
        if (level == null) return out;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = worldPosition.relative(d);
            for (BlockPos p : new BlockPos[] {side, side.above(), side.below()}) {
                if (TrackWalker.isRail(level.getBlockState(p))) out.add(p);
            }
        }
        return out;
    }

    /** Vehicles standing on the zone's rails. */
    public List<RollingStock> zoneMembers() {
        List<RollingStock> out = new ArrayList<>();
        if (level == null) return out;
        for (BlockPos p : zoneRails()) {
            for (RollingStock r : level.getEntitiesOfClass(RollingStock.class, new AABB(p).deflate(0.2, 0, 0.2).expandTowards(0, 0.5, 0))) {
                if (!out.contains(r)) out.add(r);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- ticking

    public void serverTick(ServerLevel level) {
        List<RollingStock> zone = zoneMembers();
        if (zone.isEmpty()) {
            released.clear();
            reset();
            return;
        }
        if (zone.stream().anyMatch(r -> released.contains(r.getUUID()))) {
            reset(); // the train we let go is still pulling out
            return;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        boolean stop = stopMode == STOP_ALWAYS || stopMode == STOP_POWERED && powered;
        if (!stop) {
            if (holding && stopMode == STOP_POWERED) release(zone);
            reset();
            return;
        }
        holding = true;
        setLit(true);
        for (RollingStock r : zone) r.stationHold = 3;
        boolean stopped = zone.stream().allMatch(r -> Math.abs(r.speed()) < 0.01);
        docked = stopped ? zone : List.of();
        if (!stopped) return;
        timer++;
        if (timer % 4 == 0) {
            boolean any = transfer != MODE_OFF && transferAll(level, zone);
            idle = any ? 0 : idle + 4;
        }
        boolean done = stopMode != STOP_POWERED && (dwell > 0 ? timer >= dwell * 20 : timer >= 40 && idle >= 40);
        if (done) release(zone);
    }

    private void reset() {
        holding = false;
        timer = 0;
        idle = 0;
        docked = List.of();
        setLit(false);
    }

    private void release(List<RollingStock> zone) {
        for (RollingStock r : zone) {
            for (RollingStock m : Consist.of(r).members()) released.add(m.getUUID());
            r.stationHold = 0;
        }
        reset();
    }

    private void setLit(boolean lit) {
        if (level == null) return;
        BlockState state = getBlockState();
        if (state.hasProperty(StationBlock.LIT) && state.getValue(StationBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(StationBlock.LIT, lit), 3);
        }
    }

    /** Seconds left before the docked train leaves (-1: waiting for loading to finish or for the signal). */
    public int secondsLeft() {
        if (!holding || docked.isEmpty() || stopMode == STOP_POWERED || dwell <= 0) return -1;
        return Math.max(0, (dwell * 20 - timer + 19) / 20);
    }

    // ---------------------------------------------------------------- transfers

    private boolean transferAll(ServerLevel level, List<RollingStock> zone) {
        boolean load = transfer == MODE_LOAD;
        int before = moved;
        List<BlockPos> rails = zoneRails();
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (rails.contains(p) || TrackWalker.isRail(level.getBlockState(p)) || level.getBlockEntity(p) instanceof StationBlockEntity) continue;
            Direction face = d.getOpposite();
            var items = level.getCapability(Capabilities.Item.BLOCK, p, face);
            var fluids = level.getCapability(Capabilities.Fluid.BLOCK, p, face);
            var energy = level.getCapability(Capabilities.Energy.BLOCK, p, face);
            if (items == null && fluids == null && energy == null) continue;
            for (RollingStock r : zone) {
                if (items != null) {
                    ResourceHandler<ItemResource> wagon = itemsOf(r);
                    if (wagon != null) {
                        if (load) moved += ResourceHandlerUtil.moveStacking(items, wagon, x -> true, TrainConfig.stationItems(), null);
                        else if (!r.isLocomotive()) moved += ResourceHandlerUtil.moveStacking(wagon, items, x -> true, TrainConfig.stationItems(), null);
                    }
                }
                if (fluids != null) {
                    ResourceHandler<FluidResource> tank = fluidOf(r);
                    if (tank != null) {
                        if (load) moved += ResourceHandlerUtil.move(fluids, tank, x -> true, TrainConfig.stationFluid(), null);
                        else if (r instanceof TankWagon) moved += ResourceHandlerUtil.move(tank, fluids, x -> true, TrainConfig.stationFluid(), null);
                    }
                }
                if (energy != null && load && r instanceof DieselLocomotive diesel) {
                    moved += EnergyHandlerUtil.move(energy, diesel.energyHandler(), TrainConfig.stationEnergy(), null);
                }
            }
        }
        return moved != before;
    }

    static @Nullable ResourceHandler<ItemResource> itemsOf(RollingStock r) {
        return r instanceof Container c && r.inventorySize() > 0 ? VanillaContainerWrapper.of(c) : null;
    }

    static @Nullable ResourceHandler<FluidResource> fluidOf(RollingStock r) {
        if (r instanceof TankWagon t) return t.tank();
        if (r instanceof SteamLocomotive s) return s.water();
        if (r instanceof DieselLocomotive d) return d.diesel();
        return null;
    }

    // ---------------------------------------------------------------- capabilities (pipes on the station)

    private ResourceHandler<ItemResource> dockedItems() {
        for (RollingStock r : docked) if (r.isAlive() && !r.isLocomotive() && itemsOf(r) != null) return itemsOf(r);
        return EmptyResourceHandler.instance();
    }

    private ResourceHandler<FluidResource> dockedFluid() {
        for (RollingStock r : docked) if (r.isAlive() && fluidOf(r) != null) return fluidOf(r);
        return EmptyResourceHandler.instance();
    }

    private EnergyHandler dockedEnergy() {
        for (RollingStock r : docked) if (r.isAlive() && r instanceof DieselLocomotive d) return d.energyHandler();
        return EmptyEnergyHandler.INSTANCE;
    }

    public ResourceHandler<ItemResource> itemHandler() {
        return itemsView;
    }

    public ResourceHandler<FluidResource> fluidHandler() {
        return fluidView;
    }

    public EnergyHandler energyHandler() {
        return energyView;
    }

    // ---------------------------------------------------------------- settings, menu, save

    /** Buttons of the station screen. */
    public void press(int button) {
        switch (button) {
            case StationMenu.BUTTON_STOP -> stopMode = (stopMode + 1) % 3;
            case StationMenu.BUTTON_DWELL_DOWN -> dwell = Math.max(0, dwell - (dwell > 30 ? 10 : 5));
            case StationMenu.BUTTON_DWELL_UP -> dwell = Math.min(MAX_DWELL, dwell + (dwell >= 30 ? 10 : 5));
            case StationMenu.BUTTON_MODE -> transfer = (transfer + 1) % 3;
            default -> {}
        }
        setChanged();
    }

    public void configure(int stop, int dwellSeconds, int mode) {
        stopMode = stop;
        dwell = dwellSeconds;
        transfer = mode;
        setChanged();
    }

    final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> stopMode;
                case 1 -> dwell;
                case 2 -> transfer;
                case 3 -> holding ? 1 : 0;
                case 4 -> docked.size();
                case 5 -> secondsLeft();
                case 6 -> zoneRails().size();
                case 7 -> level != null && level.hasNeighborSignal(worldPosition) ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return StationMenu.DATA;
        }
    };

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.factoryascent.train_station");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new StationMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("StopMode", stopMode);
        output.putInt("Dwell", dwell);
        output.putInt("Transfer", transfer);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stopMode = input.getIntOr("StopMode", STOP_ALWAYS);
        dwell = input.getIntOr("Dwell", 10);
        transfer = input.getIntOr("Transfer", MODE_LOAD);
    }
}
