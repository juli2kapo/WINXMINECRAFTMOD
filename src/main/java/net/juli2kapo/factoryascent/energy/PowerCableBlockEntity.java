package net.juli2kapo.factoryascent.energy;

import net.juli2kapo.factoryascent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Cables do not tick. Each one only remembers its share of the network buffer so energy survives
 * saving and chunk unloads; the {@link EnergyNetwork} does the work.
 */
public class PowerCableBlockEntity extends BlockEntity {
    long stored;
    @Nullable EnergyNetwork network;

    private final EnergyHandler handler = new EnergyHandler() {
        @Override
        public long getAmountAsLong() {
            EnergyNetwork net = network();
            return net == null ? stored : net.energy();
        }

        @Override
        public long getCapacityAsLong() {
            EnergyNetwork net = network();
            return net == null ? EnergyNetwork.CAPACITY_PER_CABLE : net.capacity();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            EnergyNetwork net = network();
            return net == null ? 0 : net.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    };

    public PowerCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CABLE.get(), pos, state);
    }

    private @Nullable EnergyNetwork network() {
        if (network == null && level instanceof ServerLevel server) {
            network = EnergyNetworkManager.get(server).networkAt(worldPosition);
        }
        return network;
    }

    public EnergyHandler energyHandler() {
        return handler;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) EnergyNetworkManager.get(server).onCableLoaded(worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server && network != null) {
            EnergyNetworkManager.get(server).dissolve(network);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stored = input.getLongOr("stored", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("stored", network != null ? network.shareFor(this) : stored);
    }
}
