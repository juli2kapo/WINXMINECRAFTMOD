package net.juli2kapo.factoryascent.fusion;

import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.juli2kapo.factoryascent.util.NeighborCaches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/** Links to its Tokamak Core (set by the core's scan) and forwards energy and items to it. */
public class FusionPortBlockEntity extends BlockEntity {
    private @Nullable BlockPos core;
    private final NeighborCaches neighbors = new NeighborCaches(this);

    public FusionPortBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.FUSION_PORT_BE.get(), pos, state);
    }

    void link(@Nullable BlockPos core) {
        if (java.util.Objects.equals(this.core, core)) return;
        this.core = core;
        setChanged();
        if (level != null) level.invalidateCapabilities(worldPosition);
    }

    public @Nullable TokamakCoreBlockEntity core(Level level) {
        if (core == null) return null;
        return level.getBlockEntity(core) instanceof TokamakCoreBlockEntity c ? c : null;
    }

    void serverTick(ServerLevel level) {
        TokamakCoreBlockEntity c = core(level);
        if (c != null && c.running()) EnergyUtil.push(neighbors, c.output(), TokamakCoreBlockEntity.PUSH, Direction.values());
    }

    public @Nullable EnergyHandler energyHandler() {
        TokamakCoreBlockEntity c = level == null ? null : core(level);
        return c == null ? null : c.energyHandler();
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler() {
        TokamakCoreBlockEntity c = level == null ? null : core(level);
        return c == null ? null : c.itemHandler();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long v = input.getLongOr("core", Long.MIN_VALUE);
        core = v == Long.MIN_VALUE ? null : BlockPos.of(v);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (core != null) output.putLong("core", core.asLong());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        neighbors.clear();
    }
}
