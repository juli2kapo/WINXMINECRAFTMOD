package net.juli2kapo.factoryascent.fusion;

import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.ProcessingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Electrolyzer: an ordinary processing machine, except that the empty bucket a water bucket
 * leaves behind moves on into an output slot, so pipes can feed it water buckets forever.
 *
 * <p>Fluids: it also takes water by pipe and, whenever its cell recipes are idle, splits it into
 * deuterium gas ({@value #RATE} mB/t, 1 mB of water to 1 mB of deuterium, like the cell recipe);
 * once its deuterium tank is full it enriches deuterium into tritium (4 to 1). Both gases go out
 * to pipes and tanks next to it (e.g. straight into a Fusion Port).
 */
public class ElectrolyzerBlockEntity extends ProcessingMachineBlockEntity {
    public static final int RATE = 10;
    private final net.juli2kapo.factoryascent.fluid.FluidTank water = new net.juli2kapo.factoryascent.fluid.FluidTank(16_000,
            r -> r.getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER), this::setChanged).input();
    private final net.juli2kapo.factoryascent.fluid.FluidTank deuterium = new net.juli2kapo.factoryascent.fluid.FluidTank(8_000,
            r -> r.getFluid().isSame(net.juli2kapo.factoryascent.fluid.ModFluids.DEUTERIUM.source()), this::setChanged).output();
    private final net.juli2kapo.factoryascent.fluid.FluidTank tritium = new net.juli2kapo.factoryascent.fluid.FluidTank(8_000,
            r -> r.getFluid().isSame(net.juli2kapo.factoryascent.fluid.ModFluids.TRITIUM.source()), this::setChanged).output();
    private final net.juli2kapo.factoryascent.fluid.TankPorts ports = new net.juli2kapo.factoryascent.fluid.TankPorts(water, deuterium, tritium);
    private final net.juli2kapo.factoryascent.fluid.FluidNeighbors fluidNeighbors = new net.juli2kapo.factoryascent.fluid.FluidNeighbors(this);

    public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.ELECTROLYZER, pos, state);
    }

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> fluidHandler() {
        return ports;
    }

    public net.juli2kapo.factoryascent.fluid.FluidTank waterTank() {
        return water;
    }

    public net.juli2kapo.factoryascent.fluid.FluidTank deuteriumTank() {
        return deuterium;
    }

    public net.juli2kapo.factoryascent.fluid.FluidTank tritiumTank() {
        return tritium;
    }

    /** Splits piped water into deuterium, and (with the deuterium tank full) deuterium into tritium. */
    private boolean electrolyseFluids() {
        int fePerMb = net.juli2kapo.factoryascent.fluid.FluidsConfig.get(net.juli2kapo.factoryascent.fluid.FluidsConfig.ELECTROLYZER_ENERGY);
        int cost = Math.round(RATE * fePerMb * energyMultiplier());
        if (energy.energy() < cost) return false;
        if (water.amount() >= RATE && deuterium.space() >= RATE) {
            water.drain(RATE);
            deuterium.forceFill(net.juli2kapo.factoryascent.fluid.ModFluids.DEUTERIUM.source(), RATE);
            energy.consume(cost);
            return true;
        }
        int t = RATE / 4;
        if (deuterium.space() < RATE && deuterium.amount() >= 4 * t && tritium.space() >= t && energy.energy() >= cost * 4) {
            deuterium.drain(4 * t);
            tritium.forceFill(net.juli2kapo.factoryascent.fluid.ModFluids.TRITIUM.source(), t);
            energy.consume(cost * 4);
            return true;
        }
        return false;
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        water.load(input, "water");
        deuterium.load(input, "deuterium");
        tritium.load(input, "tritium");
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        water.save(output, "water");
        deuterium.save(output, "deuterium");
        tritium.save(output, "tritium");
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        fluidNeighbors.clear();
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        boolean working = super.tickMachine(level);
        if (!working && electrolyseFluids()) {
            working = true;
            status = STATUS_WORKING;
        }
        fluidNeighbors.push(deuterium, 200, net.minecraft.core.Direction.values());
        fluidNeighbors.push(tritium, 200, net.minecraft.core.Direction.values());
        for (int i = slots.firstInput(); i < slots.firstMold(); i++) {
            ItemStack in = inventory.stack(i);
            if (!in.is(Items.BUCKET)) continue;
            for (int o = slots.firstUpgrade() - 1; o >= slots.firstOutput() && !in.isEmpty(); o--) {
                ItemStack out = inventory.stack(o);
                if (out.isEmpty()) {
                    inventory.setStack(o, in.copy());
                    in.setCount(0);
                } else if (out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize()) {
                    int move = Math.min(in.getCount(), out.getMaxStackSize() - out.getCount());
                    out.grow(move);
                    in.shrink(move);
                    inventory.changed(o);
                }
            }
            inventory.changed(i);
        }
        return working;
    }
}
