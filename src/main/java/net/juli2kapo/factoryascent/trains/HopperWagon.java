package net.juli2kapo.factoryascent.trains;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * An open-top ore wagon (27 slots). It catches item entities that fall into its open top (from a
 * chute, a farm, a miner's output) like a hopper minecart, and on a powered activator rail it opens its bottom doors
 * and dumps its load: into a container under the rail if there is one, else onto the ground.
 * Hoppers and Train Stations load and unload it like any container wagon.
 */
public class HopperWagon extends RollingStock implements Container {
    public static final int SLOTS = 27;
    private int dumping;

    public HopperWagon(EntityType<? extends HopperWagon> type, Level level) {
        super(type, level);
    }

    @Override
    public double length() {
        return 34 / 16.0;
    }

    @Override
    public int inventorySize() {
        return SLOTS;
    }

    @Override
    public double mass() {
        return 1.0 + 2.0 * fillFraction();
    }

    public double fillFraction() {
        double f = 0;
        for (ItemStack s : items) if (!s.isEmpty()) f += s.getCount() / (double) s.getMaxStackSize();
        return f / SLOTS;
    }

    /** Whether the bottom doors are open (dumping on an activator rail). */
    public boolean dumping() {
        return dumping > 0;
    }

    @Override
    public void activateMinecart(ServerLevel level, int x, int y, int z, boolean powered) {
        if (powered) dumping = 4;
    }

    @Override
    protected void serverTick() {
        if (!(level() instanceof ServerLevel level)) return;
        if (dumping > 0) {
            dumping--;
            dump(level);
        } else if (tickCount % 2 == 0) {
            scoop(level);
        }
        if (tickCount % 10 == 0) setFill((int) Math.round(fillFraction() * 1000) | (dumping > 0 ? 1 << 16 : 0));
    }

    /** Picks up item entities in the open top (and on the rails under it). */
    private void scoop(ServerLevel level) {
        Vec3 f = front();
        double half = length() / 2 - 0.2;
        // the open top only: what lies on the ground under the doors stays there
        AABB box = new AABB(position().add(f.scale(half)), position().subtract(f.scale(half))).inflate(0.45, 0, 0.45)
                .move(0, 0.35, 0).expandTowards(0, 1.3, 0);
        List<ItemEntity> list = level.getEntitiesOfClass(ItemEntity.class, box, EntitySelector.ENTITY_STILL_ALIVE);
        for (ItemEntity item : list) {
            if (HopperBlockEntity.addItem(this, item)) break;
        }
    }

    /** Empties one slot downward: into the container under the rail, else on the ground below. */
    private void dump(ServerLevel level) {
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) if (!items.get(i).isEmpty()) { slot = i; break; }
        if (slot < 0) return;
        ItemStack stack = items.get(slot);
        BlockPos rail = spot != null ? spot.rail() : blockPosition();
        BlockPos below = rail.below();
        var handler = level.getCapability(Capabilities.Item.BLOCK, below, Direction.UP);
        if (handler != null) {
            try (Transaction tx = Transaction.openRoot()) {
                int moved = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
                tx.commit();
                stack.shrink(moved);
            }
            return;
        }
        ItemEntity drop = new ItemEntity(level, getX(), rail.getY() + 0.1, getZ(), stack.copy());
        drop.setDeltaMovement(0, -0.1, 0);
        drop.setPickUpDelay(40); // don't scoop it straight back in
        level.addFreshEntity(drop);
        items.set(slot, ItemStack.EMPTY);
    }
}
