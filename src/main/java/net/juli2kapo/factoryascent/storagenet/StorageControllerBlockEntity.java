package net.juli2kapo.factoryascent.storagenet;

import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * The heart of a storage network. It takes Forge Energy on any side (insert only) and burns
 * {@link #BASE_DRAIN} FE/t plus {@link #PER_DEVICE} FE/t per connected device; without enough
 * energy the whole network is offline.
 */
public class StorageControllerBlockEntity extends StorageNodeBlockEntity {
    public static final int CAPACITY = 32_000;
    public static final int MAX_INSERT = 1_024;
    public static final int BASE_DRAIN = 8;
    public static final int PER_DEVICE = 1;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INSERT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private boolean powered;
    private int drain;

    public StorageControllerBlockEntity(BlockPos pos, BlockState state) {
        super(StorageContent.CONTROLLER_BE.get(), pos, state);
    }

    public EnergyHandler energyHandler() {
        return energy;
    }

    public boolean isPowered() {
        return powered;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    /** FE per tick the network currently needs. */
    public int drain() {
        return drain;
    }

    /** Fills the buffer directly (creative/testing helper). */
    public void fill() {
        energy.set(CAPACITY);
    }

    @Override
    public void serverTick(ServerLevel level) {
        StorageNet net = network();
        drain = BASE_DRAIN + PER_DEVICE * (net == null ? 0 : net.deviceCount());
        int stored = energy.getAmountAsInt();
        powered = stored >= drain;
        if (powered) energy.set(stored - drain);
        setOnline(level, powered && net != null && net.status() == StorageNet.Status.ONLINE);
    }

    /** One-line summary for right-clicking the controller. */
    public Component statusMessage() {
        StorageNet net = network();
        StorageNet.Status status = net == null ? StorageNet.Status.NO_POWER : net.status();
        ChatFormatting color = status == StorageNet.Status.ONLINE ? ChatFormatting.GREEN : ChatFormatting.RED;
        return Component.translatable(status.key()).withStyle(color)
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("gui.factoryascent.storage.controller_info",
                        EnergyUtil.format(energyStored()), EnergyUtil.format(CAPACITY), drain,
                        net == null ? 0 : net.deviceCount(), net == null ? 0 : net.drives().size()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
    }
}
