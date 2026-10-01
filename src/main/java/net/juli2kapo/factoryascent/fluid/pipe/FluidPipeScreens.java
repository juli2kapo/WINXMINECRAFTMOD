package net.juli2kapo.factoryascent.fluid.pipe;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipePayloads.FluidPipeView;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server side of the fluid pipe screen: builds its view and applies face changes (same rules as the Wrench). */
public final class FluidPipeScreens {
    private static final double REACH_BUFFER = 4.0;

    private FluidPipeScreens() {}

    public static void open(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockState(pos).getBlock() instanceof FluidPipeBlock) {
            PacketDistributor.sendToPlayer(player, view(player.level(), pos, true));
        }
    }

    static void refresh(ServerPlayer player, BlockPos pos) {
        if (inReach(player, pos) && player.level().getBlockState(pos).getBlock() instanceof FluidPipeBlock) {
            PacketDistributor.sendToPlayer(player, view(player.level(), pos, false));
        }
    }

    private static boolean inReach(ServerPlayer player, BlockPos pos) {
        return player.level().isLoaded(pos) && player.isWithinBlockInteractionRange(pos, REACH_BUFFER);
    }

    public static FluidPipeView view(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        FluidPipeBlock pipe = (FluidPipeBlock) state.getBlock();
        List<Integer> modes = new ArrayList<>(6), kinds = new ArrayList<>(6);
        List<ItemStack> neighbours = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            PipeConnection mode = state.getValue(FluidPipeBlock.PROPERTIES.get(dir));
            BlockPos other = pos.relative(dir);
            BlockState there = level.isLoaded(other) ? level.getBlockState(other) : Blocks.AIR.defaultBlockState();
            int kind = there.getBlock() instanceof FluidPipeBlock ? FluidPipePayloads.SIDE_PIPE
                    : mode != PipeConnection.NONE ? FluidPipePayloads.SIDE_TANK : FluidPipePayloads.SIDE_NOTHING;
            modes.add(mode.ordinal());
            kinds.add(kind);
            neighbours.add(there.isAir() ? ItemStack.EMPTY : new ItemStack(there.getBlock().asItem()));
        }
        FluidNetwork net = FluidNetworkManager.get(level).networkAt(pos);
        int pipes = net == null ? 1 : net.pipes().size();
        int destinations = net == null ? 0 : net.destinationCount();
        int extracting = 0;
        if (net != null) {
            for (BlockPos p : net.pipes()) {
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof FluidPipeBlock)) continue;
                for (var prop : FluidPipeBlock.PROPERTIES.values()) if (s.getValue(prop) == PipeConnection.EXTRACT) extracting++;
            }
        }
        String fluid = "";
        if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity be && be.shownFluid() != Fluids.EMPTY
                && level.getGameTime() - be.lastFlow() < 60) {
            fluid = BuiltInRegistries.FLUID.getKey(be.shownFluid()).toString();
        }
        return new FluidPipeView(open, pos, pipe.rate() * 20, modes, kinds, neighbours, pipes, destinations, extracting,
                net == null ? pipe.rate() * 20 : net.rate() * 20, fluid);
    }

    static void handleAction(ServerPlayer player, BlockPos pos, int side, int mode) {
        setFace(player, pos, side, mode);
        refresh(player, pos);
    }

    /** The checks and the change behind the screen's switch (GameTests call this). */
    public static boolean setFace(ServerPlayer player, BlockPos pos, int side, int mode) {
        if (!inReach(player, pos) || side < 0 || side >= 6) return false;
        if (mode != FluidPipePayloads.INSERT && mode != FluidPipePayloads.EXTRACT) return false;
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FluidPipeBlock pipe)) return false;
        Direction dir = Direction.from3DDataValue(side);
        boolean extract = mode == FluidPipePayloads.EXTRACT;
        boolean changed = state.getValue(FluidPipeBlock.PROPERTIES.get(dir)) != (extract ? PipeConnection.EXTRACT : PipeConnection.CONNECTED);
        boolean ok = pipe.setExtract(level, pos, state, dir, extract);
        if (ok && changed) level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, extract ? 1.2f : 0.9f);
        return ok;
    }
}
