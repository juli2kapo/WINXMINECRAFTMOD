package net.juli2kapo.factoryascent.space.station;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.space.OxygenSealerBlockEntity;
import net.juli2kapo.factoryascent.space.SpaceConfig;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/**
 * The Station Core's data: which team owns the station and what it is called. Its report walks the
 * station (every block connected to the core, within the claim radius) and counts its modules, the
 * energy stored in it and the state of its air.
 */
public class StationCoreBlockEntity extends BlockEntity {
    /** Most blocks a report walks (a big station, but a bounded amount of work). */
    public static final int SCAN_LIMIT = 40_000;

    private String team = "";
    private String name = "";

    public StationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(StationContent.STATION_CORE_BE.get(), pos, state);
    }

    public String team() {
        return team;
    }

    public String name() {
        return name;
    }

    /** Claims the station for the placer's team (named after the item's custom name, if any). */
    void claim(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.level();
        team = FactoryTeams.get(level.getServer()).teamOf(player.getUUID());
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null) {
            name = custom.getString();
        } else {
            int n = (int) StationRegistry.get(level.getServer()).all().stream().filter(s -> s.team().equals(team)).count() + 1;
            name = FactoryTeams.get(level.getServer()).displayName(team) + " Station " + n;
        }
        setChanged();
        StationRegistry.get(level.getServer()).put(new StationRegistry.Station(level.dimension(), worldPosition, team, name));
        player.sendSystemMessage(Component.translatable("message.factoryascent.station_claimed", name,
                SpaceConfig.get(SpaceConfig.STATION_RADIUS)).withStyle(ChatFormatting.AQUA));
        if (SpaceRules.isAirless(level)) StationContent.award(player, "station_core");
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) StationRegistry.get(server.getServer()).remove(server.dimension(), pos);
    }

    /** What the station is made of and how it is doing. */
    public record Report(int hull, int windows, int airlocks, int ports, int sealers, int sealed, int leaking, int airVolume,
                         int solar, int machines, long energy, long capacity, int blocks, boolean truncated) {}

    public Report survey(ServerLevel level) {
        int radius = SpaceConfig.get(SpaceConfig.STATION_RADIUS);
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(worldPosition.asLong());
        queue.enqueue(worldPosition.asLong());
        int hull = 0, windows = 0, airlocks = 0, ports = 0, sealers = 0, sealed = 0, leaking = 0, volume = 0, solar = 0, machines = 0;
        long energy = 0, capacity = 0;
        boolean truncated = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            long at = queue.dequeueLong();
            BlockPos pos = BlockPos.of(at);
            BlockState state = level.getBlockState(pos);
            String id = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
            if (id.startsWith("station_hull")) hull++;
            else if (state.is(StationContent.STATION_WINDOW.get()) || id.contains("glass")) windows++;
            else if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) airlocks++;
            else if (state.is(StationContent.DOCKING_PORT.get())) ports++;
            else if (id.contains("solar")) solar++;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OxygenSealerBlockEntity sealer) {
                sealers++;
                if (sealer.sealed()) {
                    sealed++;
                    volume += sealer.volume();
                } else if (sealer.leaking()) {
                    leaking++;
                }
            }
            if (be != null && be != this) {
                EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, pos, null);
                if (handler != null) {
                    machines++;
                    energy += handler.getAmountAsLong();
                    capacity += handler.getCapacityAsLong();
                }
            }
            for (Direction d : Direction.values()) {
                p.set(at).move(d);
                if (Math.abs(p.getX() - worldPosition.getX()) > radius || Math.abs(p.getY() - worldPosition.getY()) > radius
                        || Math.abs(p.getZ() - worldPosition.getZ()) > radius || !level.isLoaded(p)) continue;
                long key = p.asLong();
                if (seen.contains(key) || level.getBlockState(p).isAir()) continue;
                if (seen.size() >= SCAN_LIMIT) {
                    truncated = true;
                    continue;
                }
                seen.add(key);
                queue.enqueue(key);
            }
        }
        return new Report(hull, windows, airlocks, ports, sealers, sealed, leaking, volume, solar, machines, energy, capacity,
                seen.size(), truncated);
    }

    /** The status screen's lines. */
    public List<Component> lines(ServerLevel level, Report r) {
        List<Component> out = new ArrayList<>();
        FactoryTeams teams = FactoryTeams.get(level.getServer());
        String G = "gui.factoryascent.station.";
        out.add(Component.translatable(G + "owner", teams.displayName(team)).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable(G + "modules").withStyle(ChatFormatting.AQUA));
        out.add(Component.translatable(G + "hull", r.hull(), r.windows()));
        out.add(Component.translatable(G + "airlocks", r.airlocks(), r.ports()));
        out.add(Component.translatable(G + "blocks", r.blocks()).append(r.truncated() ? Component.literal("+") : Component.empty())
                .withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable(G + "power").withStyle(ChatFormatting.AQUA));
        out.add(Component.translatable(G + "energy", EnergyUtil.format(r.energy()), EnergyUtil.format(r.capacity()), r.machines()));
        out.add(Component.translatable(G + "solar", r.solar()));
        out.add(Component.translatable(G + "air").withStyle(ChatFormatting.AQUA));
        if (r.sealers() == 0) {
            out.add(Component.translatable(G + "no_sealer").withStyle(ChatFormatting.RED));
        } else {
            out.add(Component.translatable(G + "sealers", r.sealers(), r.sealed(), r.airVolume()));
            if (r.leaking() > 0) out.add(Component.translatable(G + "leaking", r.leaking()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            else if (r.sealed() < r.sealers()) out.add(Component.translatable(G + "unsealed", r.sealers() - r.sealed()).withStyle(ChatFormatting.GOLD));
            else out.add(Component.translatable(G + "all_sealed").withStyle(ChatFormatting.GREEN));
        }
        boolean here = SpaceRules.airSourceAt(level, worldPosition.above()) != null;
        if (SpaceRules.isAirless(level)) {
            out.add(Component.translatable(here ? G + "air_here" : G + "vacuum_here")
                    .withStyle(here ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return out;
    }

    public void sendView(ServerLevel level, ServerPlayer player, boolean open) {
        Report r = survey(level);
        PacketDistributor.sendToPlayer(player, new StationPayloads.StationView(open, worldPosition, name, lines(level, r)));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        team = input.getStringOr("team", "");
        name = input.getStringOr("name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("team", team);
        output.putString("name", name);
    }
}
