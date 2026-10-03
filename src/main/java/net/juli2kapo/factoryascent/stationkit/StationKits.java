package net.juli2kapo.factoryascent.stationkit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.juli2kapo.factoryascent.space.Orbit;
import net.juli2kapo.factoryascent.space.OxygenMachineBlock;
import net.juli2kapo.factoryascent.space.OxygenSealerBlockEntity;
import net.juli2kapo.factoryascent.space.ReturnPodBlock;
import net.juli2kapo.factoryascent.space.SpaceConfig;
import net.juli2kapo.factoryascent.space.SpaceContent;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.space.station.StationContent;
import net.juli2kapo.factoryascent.space.station.StationCoreBlockEntity;
import net.juli2kapo.factoryascent.space.station.StationRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * The two station payloads of the Launch Pad, called from small hooks in
 * {@code orbital/LaunchControllerBlockEntity}:
 *
 * <ul>
 *   <li><b>Station Kit</b>: deploys a prefabricated sealed module in orbit straight above the pad
 *       (at {@link Orbit#DECK_Y}): the {@link #layout} below, with the Station Core claimed for
 *       the launcher's team and the Oxygen Sealer charged. The launch is refused while anything
 *       is built in that space.</li>
 *   <li><b>Cargo Pod</b>: its 27 slots are unloaded into the cargo hold of the launcher's team's
 *       Station Core claiming the spot above the pad; the launch is refused without one.</li>
 * </ul>
 * Both fly from the Overworld only (orbit is the Overworld's).
 */
public final class StationKits {
    /** Blocks of the module, relative to the centre of its floor. */
    public enum Part { FLOOR, WALL, CEILING, WINDOW, DOOR_LOWER, DOOR_UPPER, SEALER, CORE, POD, PORCH }

    /** Half-width of the module (7×7 outside, 5×5×3 of air inside). */
    public static final int HALF = 3, HEIGHT = 4;

    private StationKits() {}

    public static boolean isKit(ItemStack stack) {
        return stack.getItem() instanceof StationKitItem;
    }

    public static boolean isPod(ItemStack stack) {
        return stack.getItem() instanceof CargoPodItem;
    }

    public static boolean isPayload(ItemStack stack) {
        return isKit(stack) || isPod(stack);
    }

    /** The module: floor, hull walls with windows east, west and north, an Airlock south, a porch outside it. */
    public static Map<BlockPos, Part> layout() {
        Map<BlockPos, Part> m = new LinkedHashMap<>();
        for (int y = 0; y <= HEIGHT; y++) {
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
                    if (y == 0) m.put(new BlockPos(x, y, z), Part.FLOOR);
                    else if (y == HEIGHT) m.put(new BlockPos(x, y, z), Part.CEILING);
                    else if (edge) m.put(new BlockPos(x, y, z), Part.WALL);
                }
            }
        }
        m.put(new BlockPos(HALF, 2, 0), Part.WINDOW);
        m.put(new BlockPos(-HALF, 2, 0), Part.WINDOW);
        m.put(new BlockPos(0, 2, -HALF), Part.WINDOW);
        m.put(new BlockPos(0, 1, HALF), Part.DOOR_LOWER);
        m.put(new BlockPos(0, 2, HALF), Part.DOOR_UPPER);
        m.put(new BlockPos(-2, 1, -2), Part.SEALER);
        m.put(new BlockPos(2, 1, -2), Part.CORE);
        m.put(new BlockPos(0, 1, -2), Part.POD);
        for (int x = -1; x <= 1; x++) {
            for (int z = HALF + 1; z <= HALF + 2; z++) m.put(new BlockPos(x, 0, z), Part.PORCH);
        }
        return m;
    }

    /** Where the floor centre of a kit launched from {@code pad} goes. */
    public static BlockPos moduleCentre(BlockPos pad) {
        return Orbit.deckCentre(pad);
    }

    /** The first non-air block in the module's space (with a block of margin), or null if it is free. */
    public static @Nullable BlockPos obstruction(Level orbit, BlockPos centre) {
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-HALF - 1, -1, -HALF - 1), centre.offset(HALF + 1, HEIGHT + 1, HALF + 3))) {
            if (!orbit.getBlockState(p).isAir()) return p.immutable();
        }
        return null;
    }

    /** The team's station claiming the spot above a pad, or null. */
    public static StationRegistry.@Nullable Station stationAbove(MinecraftServer server, BlockPos pad, String team) {
        return stationAbove(server, SpaceRules.ORBIT, pad, team);
    }

    /** Same, in any dimension (game tests run where they can). */
    public static StationRegistry.@Nullable Station stationAbove(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension,
                                                                 BlockPos pad, String team) {
        StationRegistry.Station s = StationRegistry.get(server).claiming(dimension, Orbit.deckCentre(pad),
                SpaceConfig.get(SpaceConfig.STATION_RADIUS));
        return s != null && s.team().equals(team) ? s : null;
    }

    private static String teamOf(MinecraftServer server, @Nullable UUID player) {
        return FactoryTeams.get(server).teamOf(player != null ? player : new UUID(0, 0));
    }

    // ---------------------------------------------------------------- launch hooks

    /** Extra launch rule for these payloads (null if fine). */
    public static @Nullable Component launchProblem(LaunchControllerBlockEntity pad) {
        ItemStack payload = pad.satellite();
        if (!isPayload(payload) || !(pad.getLevel() instanceof ServerLevel level)) return null;
        if (level.dimension() != Level.OVERWORLD) {
            return Component.translatable("message.factoryascent.kit_earth_only").withStyle(ChatFormatting.RED);
        }
        ServerLevel orbit = Orbit.level(level.getServer());
        if (orbit == null) return Component.translatable("message.factoryascent.orbit_unavailable").withStyle(ChatFormatting.RED);
        BlockPos centre = moduleCentre(pad.getBlockPos());
        if (isKit(payload)) {
            orbit.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
            BlockPos blocked = obstruction(orbit, centre);
            if (blocked != null) {
                return Component.translatable("message.factoryascent.kit_occupied", blocked.getX(), blocked.getY(), blocked.getZ())
                        .withStyle(ChatFormatting.RED);
            }
        } else if (stationAbove(level.getServer(), pad.getBlockPos(), teamOf(level.getServer(), pad.launcher())) == null) {
            return Component.translatable("message.factoryascent.cargo_no_station", centre.getX(), Orbit.DECK_Y, centre.getZ())
                    .withStyle(ChatFormatting.RED);
        }
        return null;
    }

    /** The payload reaches orbit. */
    public static void arrive(ServerLevel level, LaunchControllerBlockEntity pad, UUID launcher) {
        ItemStack payload = pad.satellite();
        ServerLevel orbit = Orbit.level(level.getServer());
        String team = teamOf(level.getServer(), launcher);
        if (orbit == null) return;
        if (isKit(payload)) {
            BlockPos centre = moduleCentre(pad.getBlockPos());
            orbit.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
            if (obstruction(orbit, centre) != null) { // built over during the climb: it docks onto nothing, the kit comes back
                tell(level.getServer(), team, Component.translatable("message.factoryascent.kit_aborted").withStyle(ChatFormatting.RED));
                Block.popResource(level, pad.getBlockPos().above(), payload.copy());
                return;
            }
            String name = deploy(orbit, centre, team);
            tell(level.getServer(), team, Component.translatable("message.factoryascent.kit_deployed", name,
                    centre.getX(), centre.getY(), centre.getZ()).withStyle(ChatFormatting.AQUA));
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(launcher);
            if (p != null) {
                SpaceContent.award(p, "station_kit");
                SpaceContent.award(p, "station_core");
            }
        } else {
            StationRegistry.Station station = stationAbove(level.getServer(), pad.getBlockPos(), team);
            if (station == null) { // the station went away during the climb: the pod comes back down
                tell(level.getServer(), team, Component.translatable("message.factoryascent.cargo_returned").withStyle(ChatFormatting.YELLOW));
                Block.popResource(level, pad.getBlockPos().above(), payload.copy());
                return;
            }
            int stacks = deliver(orbit, station.pos(), payload);
            tell(level.getServer(), team, Component.translatable("message.factoryascent.cargo_delivered", stacks, station.name())
                    .withStyle(ChatFormatting.AQUA));
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(launcher);
            if (p != null) SpaceContent.award(p, "station_cargo");
        }
    }

    private static void tell(MinecraftServer server, String team, Component message) {
        for (UUID id : FactoryTeams.get(server).members(team)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(message);
        }
    }

    // ---------------------------------------------------------------- the module

    /** Builds the module with its floor centre at {@code centre}; returns the new station's name. */
    public static String deploy(ServerLevel orbit, BlockPos centre, String team) {
        BlockState hull = StationContent.HULLS.get("white").get(0).get().defaultBlockState();
        BlockState porch = StationContent.HULLS.get("orange").get(0).get().defaultBlockState();
        BlockState door = StationContent.AIRLOCK_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.OPEN, false).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        for (Map.Entry<BlockPos, Part> e : layout().entrySet()) {
            BlockPos p = centre.offset(e.getKey());
            BlockState s = switch (e.getValue()) {
                case FLOOR, WALL, CEILING -> hull;
                case PORCH -> porch;
                case WINDOW -> StationContent.STATION_WINDOW.get().defaultBlockState();
                case DOOR_LOWER -> door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
                case DOOR_UPPER -> door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                case SEALER -> SpaceContent.OXYGEN_SEALER.get().defaultBlockState().setValue(OxygenMachineBlock.FACING, Direction.SOUTH);
                case CORE -> StationContent.STATION_CORE.get().defaultBlockState();
                case POD -> SpaceContent.RETURN_POD.get().defaultBlockState().setValue(ReturnPodBlock.FACING, Direction.SOUTH);
            };
            orbit.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (Map.Entry<BlockPos, Part> e : layout().entrySet()) {
            BlockPos p = centre.offset(e.getKey());
            if (e.getValue() == Part.SEALER && orbit.getBlockEntity(p) instanceof OxygenSealerBlockEntity sealer) sealer.fillEnergy();
            if (e.getValue() == Part.CORE && orbit.getBlockEntity(p) instanceof StationCoreBlockEntity core) core.claimFor(orbit, team);
        }
        orbit.playSound(null, centre.above(2), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1f, 0.6f);
        orbit.playSound(null, centre.above(2), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.2f);
        return orbit.getBlockEntity(centre.offset(2, 1, -2)) instanceof StationCoreBlockEntity core ? core.name() : "";
    }

    /** Unloads a Cargo Pod into the hold of the Station Core at {@code core}; what doesn't fit floats next to it. Returns the stacks delivered. */
    public static int deliver(ServerLevel orbit, BlockPos core, ItemStack pod) {
        orbit.getChunk(core.getX() >> 4, core.getZ() >> 4);
        NonNullList<ItemStack> items = CargoPodItem.contents(pod);
        StationCoreBlockEntity be = orbit.getBlockEntity(core) instanceof StationCoreBlockEntity c ? c : null;
        BlockPos drop = Orbit.standingSpotNear(orbit, core);
        if (drop == null) drop = core.above();
        int n = 0;
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            n++;
            ItemStack left = be != null ? be.store(s.copy()) : s.copy();
            if (!left.isEmpty()) {
                ItemEntity item = new ItemEntity(orbit, drop.getX() + 0.5, drop.getY() + 0.2, drop.getZ() + 0.5, left);
                item.setDeltaMovement(0, 0, 0);
                orbit.addFreshEntity(item);
            }
        }
        orbit.playSound(null, core, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 1f, 0.7f);
        return n;
    }
}
