package net.juli2kapo.factoryascent.fluid.pipe;

import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Pipes hold no fluid. Inserting into a pipe routes straight into the network (at most the
 * tier's rate per tick through one pipe); a pipe with extracting faces pumps out of those blocks
 * every tick. The pipe remembers the last fluid that went through (synced, for the window).
 */
public class FluidPipeBlockEntity extends BlockEntity {
    @SuppressWarnings("unchecked")
    private final ResourceHandler<FluidResource>[] faces = new ResourceHandler[6];
    private Fluid shown = Fluids.EMPTY;
    private long lastFlow = Long.MIN_VALUE / 2;
    private long lastSynced = Long.MIN_VALUE / 2;
    private long budgetTick = -1;
    private int budgetUsed;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(FluidContent.FLUID_PIPE_BE.get(), pos, state);
    }

    private @Nullable FluidNetwork network() {
        return level instanceof ServerLevel server ? FluidNetworkManager.get(server).networkAt(worldPosition) : null;
    }

    public int rate() {
        return getBlockState().getBlock() instanceof FluidPipeBlock p ? p.rate() : 0;
    }

    /** What still fits through this pipe this tick. */
    private int budget() {
        long now = level == null ? 0 : level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            budgetUsed = 0;
        }
        return Math.max(0, rate() - budgetUsed);
    }

    private void spend(int amount) {
        budget();
        budgetUsed += amount;
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        if (side == null) return null;
        int i = side.ordinal();
        if (faces[i] == null) faces[i] = new FaceHandler(worldPosition.relative(side));
        return faces[i];
    }

    public void serverTick(ServerLevel level) {
        FluidNetwork network = network();
        if (network == null || network.destinationCount() == 0) return;
        for (Direction dir : Direction.values()) {
            if (getBlockState().getValue(FluidPipeBlock.PROPERTIES.get(dir)) != PipeConnection.EXTRACT) continue;
            BlockPos sourcePos = worldPosition.relative(dir);
            ResourceHandler<FluidResource> source = level.getCapability(Capabilities.Fluid.BLOCK, sourcePos, dir.getOpposite());
            if (source == null) continue;
            pull(network, source, sourcePos);
        }
    }

    private void pull(FluidNetwork network, ResourceHandler<FluidResource> source, BlockPos sourcePos) {
        for (int i = 0; i < source.size(); i++) {
            int budget = budget();
            if (budget <= 0) return;
            FluidResource resource = source.getResource(i);
            int available = source.getAmountAsInt(i);
            if (resource.isEmpty() || available <= 0) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int routed = network.route(resource, Math.min(available, budget), sourcePos, tx);
                if (routed <= 0) continue;
                int taken = source.extract(i, resource, routed, tx);
                if (taken == routed) {
                    tx.commit();
                    spend(taken);
                    network.showFlow(resource.getFluid());
                }
            }
        }
    }

    /** Called by the network when fluid moved through it. */
    void showFlow(Fluid fluid, long now) {
        lastFlow = now;
        if (fluid != shown || now - lastSynced >= 20) {
            lastSynced = now;
            shown = fluid;
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public Fluid shownFluid() {
        return shown;
    }

    public long lastFlow() {
        return lastFlow;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) FluidNetworkManager.get(server).onPipeChanged(worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) FluidNetworkManager.get(server).onPipeChanged(worldPosition);
    }

    // ---------------------------------------------------------------- persistence & sync

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String id = input.getStringOr("fluid", "");
        Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
        shown = key == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(key);
        lastFlow = input.getLongOr("last_flow", Long.MIN_VALUE / 2);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (shown != Fluids.EMPTY) output.putString("fluid", BuiltInRegistries.FLUID.getKey(shown).toString());
        output.putLong("last_flow", lastFlow);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    /** Insert-only view of the network for one face. */
    private final class FaceHandler implements ResourceHandler<FluidResource> {
        private final BlockPos source;

        FaceHandler(BlockPos source) {
            this.source = source;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return rate();
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return true;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            FluidNetwork net = network();
            if (net == null) return 0;
            int budget = budget();
            if (budget <= 0) return 0;
            int moved = net.route(resource, Math.min(amount, budget), source, transaction);
            if (moved > 0) {
                // Committed or not, count it: a simulated insert followed by the real one is rare and harmless.
                spend(moved);
                net.showFlow(resource.getFluid());
            }
            return moved;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }
}
