package net.juli2kapo.factoryascent.ender;

import java.util.UUID;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.MachineEnergy;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Energy store and owner of an Ender Beacon. */
public class EnderBeaconBlockEntity extends BlockEntity {
    public static final int CAPACITY = 200_000;
    private final MachineEnergy energy = new MachineEnergy(this::setChanged);
    private @Nullable UUID owner;

    public EnderBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(EnderContent.ENDER_BEACON_BE.get(), pos, state);
        energy.configure(CAPACITY, 2_000, 0);
    }

    public MachineEnergy energy() {
        return energy;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** Anyone may link to an unowned beacon; otherwise only its owner. */
    boolean mayLink(UUID player) {
        return owner == null || owner.equals(player);
    }

    boolean canRecall() {
        return energy.energy() >= Config.RECALL_ENERGY.get();
    }

    /** Spends the energy of one recall; false if there isn't enough. */
    boolean spendRecall() {
        if (!canRecall()) return false;
        energy.consume(Config.RECALL_ENERGY.get());
        return true;
    }

    void serverTick(ServerLevel level, BlockState state) {
        if (level.getGameTime() % 10 != 0) return;
        boolean charged = canRecall();
        if (state.getValue(EnderBeaconBlock.CHARGED) != charged) {
            level.setBlock(worldPosition, state.setValue(EnderBeaconBlock.CHARGED, charged), 3);
        }
    }

    Component statusLine() {
        int cost = Config.RECALL_ENERGY.get();
        int recalls = cost <= 0 ? 99 : energy.energy() / cost;
        return Component.translatable("message.factoryascent.beacon_status",
                EnergyUtil.format(energy.energy()), EnergyUtil.format(CAPACITY), recalls);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
    }
}
