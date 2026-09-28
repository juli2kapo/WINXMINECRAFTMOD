package net.juli2kapo.factoryascent.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.juli2kapo.factoryascent.energy.EnergyNetwork;
import net.juli2kapo.factoryascent.energy.EnergyNetworkManager;
import net.juli2kapo.factoryascent.energy.PowerCableBlock;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.pipe.ItemNetwork;
import net.juli2kapo.factoryascent.pipe.ItemNetworkManager;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlock;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlockEntity;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.juli2kapo.factoryascent.storagenet.StorageControllerBlockEntity;
import net.juli2kapo.factoryascent.storagenet.StorageNet;
import net.juli2kapo.factoryascent.storagenet.StorageNodeBlockEntity;
import net.juli2kapo.factoryascent.ui.ScreenPayloads.CableView;
import net.juli2kapo.factoryascent.ui.ScreenPayloads.PipeView;
import net.juli2kapo.factoryascent.ui.ScreenPayloads.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Server side of the item pipe, power cable and storage network screens: builds the views the
 * client shows and applies the pipe screen's face changes (same rules as the Wrench).
 */
public final class NetworkScreens {
    /** How far (beyond normal reach) a player may be from the block an open screen talks to. */
    private static final double REACH_BUFFER = 4.0;
    private static final int TOP_ITEMS = 22;

    private NetworkScreens() {}

    /** Opens the screen matching the block at {@code pos} (pipe, cable, or any storage network block). */
    public static void open(ServerPlayer player, BlockPos pos) {
        send(player, pos, true);
    }

    /** A screen asked for a fresh view. */
    static void refresh(ServerPlayer player, BlockPos pos) {
        if (!inReach(player, pos)) return;
        send(player, pos, false);
    }

    private static boolean inReach(ServerPlayer player, BlockPos pos) {
        return player.level().isLoaded(pos) && player.isWithinBlockInteractionRange(pos, REACH_BUFFER);
    }

    private static void send(ServerPlayer player, BlockPos pos, boolean open) {
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ItemPipeBlock) {
            PacketDistributor.sendToPlayer(player, pipeView(level, pos, open));
        } else if (state.getBlock() instanceof PowerCableBlock) {
            PacketDistributor.sendToPlayer(player, cableView(level, pos, open));
        } else if (level.getBlockEntity(pos) instanceof StorageNodeBlockEntity node) {
            PacketDistributor.sendToPlayer(player, storageView(node, pos, open));
        }
    }

    // ---------------------------------------------------------------- item pipe

    public static PipeView pipeView(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        ItemPipeBlock pipe = (ItemPipeBlock) state.getBlock();
        List<Integer> modes = new ArrayList<>(6), kinds = new ArrayList<>(6);
        List<ItemStack> neighbours = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            PipeConnection mode = state.getValue(ItemPipeBlock.PROPERTIES.get(dir));
            BlockPos other = pos.relative(dir);
            BlockState there = level.isLoaded(other) ? level.getBlockState(other) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            int kind = there.getBlock() instanceof ItemPipeBlock ? ScreenPayloads.SIDE_PIPE
                    : mode != PipeConnection.NONE ? ScreenPayloads.SIDE_INVENTORY : ScreenPayloads.SIDE_NOTHING;
            modes.add(mode.ordinal());
            kinds.add(kind);
            neighbours.add(there.isAir() ? ItemStack.EMPTY : new ItemStack(there.getBlock().asItem()));
        }
        ItemNetwork net = ItemNetworkManager.get(level).networkAt(pos);
        int pipes = net == null ? 1 : net.pipes().size();
        int destinations = net == null ? 0 : net.destinationCount();
        int extracting = 0;
        if (net != null) {
            for (BlockPos p : net.pipes()) {
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof ItemPipeBlock)) continue;
                for (var prop : ItemPipeBlock.PROPERTIES.values()) {
                    if (s.getValue(prop) == PipeConnection.EXTRACT) extracting++;
                }
            }
        }
        int perSecond = pipe.rate() * 20 / ItemPipeBlockEntity.EXTRACT_INTERVAL;
        return new PipeView(open, pos, perSecond, modes, kinds, neighbours, pipes, destinations, extracting);
    }

    /**
     * The pipe screen sets one face to delivering or extracting. Checks reach and that the face
     * touches an inventory (as the Wrench does), then answers with a fresh view.
     */
    static void handlePipeAction(ServerPlayer player, BlockPos pos, int side, int mode) {
        setPipeFace(player, pos, side, mode);
        if (inReach(player, pos) && player.level().getBlockState(pos).getBlock() instanceof ItemPipeBlock) {
            PacketDistributor.sendToPlayer(player, pipeView(player.level(), pos, false));
        }
    }

    /**
     * The checks and the change behind {@link #handlePipeAction}, without the reply packet (GameTests
     * call this). Returns whether the face now has the requested mode.
     */
    public static boolean setPipeFace(ServerPlayer player, BlockPos pos, int side, int mode) {
        if (!inReach(player, pos) || side < 0 || side >= 6) return false;
        if (mode != ScreenPayloads.PipeAction.INSERT && mode != ScreenPayloads.PipeAction.EXTRACT) return false;
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ItemPipeBlock pipe)) return false;
        Direction dir = Direction.from3DDataValue(side);
        boolean extract = mode == ScreenPayloads.PipeAction.EXTRACT;
        boolean changed = state.getValue(ItemPipeBlock.PROPERTIES.get(dir)) != (extract ? PipeConnection.EXTRACT : PipeConnection.CONNECTED);
        boolean ok = pipe.setExtract(level, pos, state, dir, extract);
        if (ok && changed) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, extract ? 1.2f : 0.9f);
        }
        return ok;
    }

    // ---------------------------------------------------------------- power cable

    public static CableView cableView(ServerLevel level, BlockPos pos, boolean open) {
        PowerCableBlock block = (PowerCableBlock) level.getBlockState(pos).getBlock();
        EnergyNetwork net = EnergyNetworkManager.get(level).networkAt(pos);
        if (net == null) return new CableView(open, pos, 0, 0, block.rate(), block.rate(), 1, 0, 0, 0, 0, 0);
        int producers = 0, consumers = 0, storage = 0;
        for (BlockPos end : net.endpointPositions()) {
            if (level.getBlockEntity(end) instanceof AbstractMachineBlockEntity machine) {
                MachineType.Category c = machine.type().category();
                if (c == MachineType.Category.GENERATOR) producers++;
                else if (c == MachineType.Category.STORAGE) storage++;
                else consumers++;
                continue;
            }
            // Other mods' blocks: anything we can pull from counts as a producer.
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, end, null);
            boolean gives = false;
            if (handler != null) {
                try (Transaction tx = Transaction.openRoot()) {
                    gives = handler.extract(1, tx) > 0; // not committed: a dry run
                }
            }
            if (gives) producers++;
            else consumers++;
        }
        return new CableView(open, pos, net.energy(), net.capacity(), net.rate(), block.rate(), net.cables().size(),
                producers, consumers, storage, net.averageIn(), net.averageOut());
    }

    // ---------------------------------------------------------------- storage network

    public static StorageView storageView(StorageNodeBlockEntity node, BlockPos pos, boolean open) {
        StorageNet net = node.network();
        if (net == null) {
            return new StorageView(open, pos, StorageNet.Status.NO_CONTROLLER.ordinal(), 0, 0, 0, 0, 0, 1, 0, 0, 0, 0,
                    List.of(), List.of());
        }
        StorageControllerBlockEntity controller = net.controller();
        List<StorageNet.Stored> contents = new ArrayList<>(net.contents());
        contents.sort(Comparator.comparingLong(StorageNet.Stored::count).reversed());
        List<ItemStack> top = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (StorageNet.Stored s : contents) {
            if (top.size() >= TOP_ITEMS) break;
            top.add(s.resource().toStack(1));
            counts.add(s.count());
        }
        return new StorageView(open, pos, net.status().ordinal(),
                controller == null ? 0 : controller.energyStored(), controller == null ? 0 : StorageControllerBlockEntity.CAPACITY,
                controller == null ? 0 : controller.drain(), net.deviceCount(), net.drives().size(), net.members().size(),
                net.usedItems(), net.capacityItems(), net.contents().size(), net.typeCapacity(), top, counts);
    }
}
