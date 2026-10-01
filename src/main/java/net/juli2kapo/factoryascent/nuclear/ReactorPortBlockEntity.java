package net.juli2kapo.factoryascent.nuclear;

import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.juli2kapo.factoryascent.util.NeighborCaches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/** Remembers which reactor controller it belongs to (the controller links it when it scans) and forwards to it. */
public class ReactorPortBlockEntity extends BlockEntity {
    private @Nullable BlockPos controller;
    private final NeighborCaches neighbors = new NeighborCaches(this);
    private int sources;

    public ReactorPortBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.REACTOR_PORT_BE.get(), pos, state);
    }

    public ReactorPortBlock.Kind kind() {
        return getBlockState().getBlock() instanceof ReactorPortBlock b ? b.kind() : ReactorPortBlock.Kind.ACCESS;
    }

    public @Nullable BlockPos controller() {
        return controller;
    }

    void link(@Nullable BlockPos controller) {
        if (java.util.Objects.equals(this.controller, controller)) return;
        this.controller = controller;
        setChanged();
        if (level != null) level.invalidateCapabilities(worldPosition);
    }

    public @Nullable ReactorControllerBlockEntity controller(Level level) {
        if (controller == null) return null;
        return level.getBlockEntity(controller) instanceof ReactorControllerBlockEntity c ? c : null;
    }

    void serverTick(ServerLevel level) {
        ReactorControllerBlockEntity c = controller(level);
        if (c == null) return;
        switch (kind()) {
            case POWER -> EnergyUtil.push(neighbors, c.energy(), ReactorControllerBlockEntity.PUSH, Direction.values());
            case COOLANT -> {
                if (level.getGameTime() % 20 == 0) {
                    int n = 0;
                    for (Direction d : Direction.values()) {
                        FluidState f = level.getFluidState(worldPosition.relative(d));
                        if (f.is(FluidTags.WATER) && f.isSource()) n++;
                    }
                    sources = n;
                }
                if (sources > 0) c.addCoolant(sources * ReactorControllerBlockEntity.COOLANT_PER_SOURCE);
                // hand steam to turbines and pipes touching the port
                if (c.steam() >= 1) pushSteam(level, c);
            }
            default -> { }
        }
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler() {
        if (level == null) return null;
        ReactorControllerBlockEntity c = controller(level);
        if (c == null) return null;
        return switch (kind()) {
            case ACCESS -> c.itemHandler();
            case COOLANT -> c.coolantHandler();
            default -> null;
        };
    }

    private void pushSteam(ServerLevel level, ReactorControllerBlockEntity c) {
        var handler = c.coolantFluidHandler();
        var steam = net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.juli2kapo.factoryascent.fluid.ModFluids.STEAM.source());
        for (Direction d : Direction.values()) {
            BlockPos other = worldPosition.relative(d);
            if (level.getBlockEntity(other) instanceof ReactorPortBlockEntity || level.getBlockEntity(other) instanceof ReactorControllerBlockEntity) continue;
            var target = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, other, d.getOpposite());
            if (target == null) continue;
            int have = (int) handler.getAmountAsLong(1);
            if (have <= 0) return;
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                int in = target.insert(steam, Math.min(have, 4000), tx);
                if (in > 0 && handler.extract(1, steam, in, tx) == in) tx.commit();
            }
        }
    }

    /** Coolant Ports: water and coolant fluid in, steam out (to Steam Turbines). */
    public @Nullable ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> fluidHandler() {
        if (level == null || kind() != ReactorPortBlock.Kind.COOLANT) return null;
        ReactorControllerBlockEntity c = controller(level);
        return c == null ? null : c.coolantFluidHandler();
    }

    public @Nullable EnergyHandler energyHandler() {
        if (level == null || kind() != ReactorPortBlock.Kind.POWER) return null;
        ReactorControllerBlockEntity c = controller(level);
        return c == null ? null : c.energyHandler();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long v = input.getLongOr("controller", Long.MIN_VALUE);
        controller = v == Long.MIN_VALUE ? null : BlockPos.of(v);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (controller != null) output.putLong("controller", controller.asLong());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        neighbors.clear();
    }
}
