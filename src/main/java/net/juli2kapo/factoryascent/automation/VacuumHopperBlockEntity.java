package net.juli2kapo.factoryascent.automation;

import java.util.List;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Sucks up dropped items within {@link #RADIUS} blocks: far ones are pulled towards it, the ones
 * that arrive go into its nine slots (and on through auto-eject into a chest or pipe touching
 * it). Items a player has just thrown keep their pickup delay, so it never snatches a drop out
 * of the air in front of you.
 */
public class VacuumHopperBlockEntity extends AbstractMachineBlockEntity {
    public static final int RADIUS = 6;
    private static final double ABSORB = 1.3;
    private static final int ENERGY_PER_ITEM = 4;

    private int pulled;

    public VacuumHopperBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.VACUUM_HOPPER, pos, state);
    }

    @Override
    protected void configureEnergy() {
        energy.configure(16_000, 1_000, 0);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        lastEnergyRate = 0;
        if ((level.getGameTime() + worldPosition.asLong()) % 2 != 0) return status == STATUS_WORKING;
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(worldPosition).inflate(RADIUS),
                e -> e.isAlive() && !e.hasPickUpDelay());
        if (items.isEmpty()) {
            status = STATUS_IDLE;
            return false;
        }
        int per = Math.max(1, (int) Math.round(ENERGY_PER_ITEM * Config.MACHINE_ENERGY.get()));
        if (energy.energy() < per) {
            status = STATUS_NO_POWER;
            return false;
        }
        boolean full = MachineOutputs.emptySlots(inventory) == 0;
        int used = 0;
        for (ItemEntity item : items) {
            if (energy.energy() < used + per) break;
            Vec3 to = centre.subtract(item.position());
            if (to.length() <= ABSORB) {
                ItemStack rest = MachineOutputs.insert(inventory, item.getItem().copy());
                if (rest.getCount() != item.getItem().getCount()) {
                    pulled += item.getItem().getCount() - rest.getCount();
                    if (rest.isEmpty()) item.discard();
                    else item.setItem(rest);
                    used += per;
                }
            } else if (!full) {
                item.setDeltaMovement(to.normalize().scale(Math.min(0.45, 0.12 + to.length() * 0.04)));
                item.hurtMarked = true;
                used += per;
            }
        }
        energy.consume(used);
        lastEnergyRate = used / 2;
        status = used > 0 ? STATUS_WORKING : full ? STATUS_OUTPUT_FULL : STATUS_IDLE;
        return used > 0;
    }

    /** Nothing goes in by pipe: it only collects. */
    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return false;
    }

    public int pulled() {
        return pulled;
    }

    @Override
    public int extraA() {
        return RADIUS;
    }
}
