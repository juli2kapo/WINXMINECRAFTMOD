package net.juli2kapo.factoryascent.fluid.tank;

import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.fluid.TankPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A fluid tank: one fluid, any side in or out. Liquids settle into a tank stacked under it, gases
 * rise into one above. Broken, it keeps what it holds (on the item, as a data component). The
 * contents are synced to clients for the fill level drawn inside the glass.
 */
public class FluidTankBlockEntity extends BlockEntity {
    private final FluidTank tank;
    private final TankPorts ports;
    private int lastSentAmount = -1;
    private long lastSent;
    /** Client only: the fill level drawn (eases towards the real one). */
    public float shownFill;

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(FluidContent.FLUID_TANK_BE.get(), pos, state);
        int capacity = state.getBlock() instanceof FluidTankBlock b ? b.size().capacity() : 16_000;
        tank = new FluidTank(capacity, r -> true, this::onContentsChanged);
        ports = new TankPorts(tank);
    }

    public FluidTank tank() {
        return tank;
    }

    public ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return ports;
    }

    private void onContentsChanged() {
        setChanged();
    }

    public void serverTick(ServerLevel level) {
        if (!tank.isEmpty() && level.getGameTime() % 4 == 0) {
            boolean gas = tank.fluid().getFluidType().isLighterThanAir();
            Direction d = gas ? Direction.UP : Direction.DOWN;
            BlockPos other = worldPosition.relative(d);
            if (level.getBlockEntity(other) instanceof FluidTankBlockEntity) {
                ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, other, d.getOpposite());
                if (target != null) {
                    FluidResource r = tank.getResource(0);
                    try (Transaction tx = Transaction.openRoot()) {
                        int in = target.insert(r, Math.min(tank.amount(), 4000), tx);
                        if (in > 0 && tank.extract(0, r, in, tx) == in) tx.commit();
                    }
                }
            }
        }
        // sync the level to watchers, at most every 5 ticks and only when it changed
        if (tank.amount() != lastSentAmount && level.getGameTime() - lastSent >= 5) {
            lastSentAmount = tank.amount();
            lastSent = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public int comparatorSignal() {
        return tank.capacity() <= 0 ? 0 : (int) Math.ceil(15.0 * tank.amount() / tank.capacity());
    }

    // ---------------------------------------------------------------- persistence, item components, sync

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.load(input, "tank");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.save(output, "tank");
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SimpleFluidContent content = components.get(FluidContent.TANK_CONTENTS.get());
        if (content != null && !content.isEmpty()) {
            FluidStack stack = content.copy();
            tank.setContents(stack.getFluid(), Math.min(stack.getAmount(), tank.capacity()));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!tank.isEmpty()) components.set(FluidContent.TANK_CONTENTS.get(), SimpleFluidContent.copyOf(tank.stack()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("tank");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
