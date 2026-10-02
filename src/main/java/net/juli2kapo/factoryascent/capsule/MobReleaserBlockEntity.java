package net.juli2kapo.factoryascent.capsule;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.mobs.CapturedMob;
import net.juli2kapo.factoryascent.mobs.MobCapsuleItem;
import net.juli2kapo.factoryascent.mobs.MobContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Mob Releaser: nine capsule slots. A redstone pulse (or the screen's Release All button) lets
 * every captured mob out at once, around a release point a set distance in front of it and a set
 * height above it. Each mob comes out exactly as it was stored: size, health, name, boss bar and
 * all. The emptied capsules stay in their slots (a hopper or pipe can take them out).
 */
public class MobReleaserBlockEntity extends DeviceBlockEntity {
    public static final int SLOTS = 9, MAX_HEIGHT = 16;

    private int distance = 3;
    private int height = 0;
    private boolean powered;
    private int lastReleased = -1;

    public MobReleaserBlockEntity(BlockPos pos, BlockState state) {
        super(CapsuleContent.MOB_RELEASER_BE.get(), pos, state, SLOTS, 0, 0);
    }

    @Override
    public boolean isValid(int slot, ItemResource resource) {
        return resource.is(MobContent.MOB_CAPSULE.get());
    }

    @Override
    public int slotLimit(int slot) {
        return 1;
    }

    public int distance() {
        return Mth.clamp(distance, 1, CapsuleConfig.RELEASER_MAX_DISTANCE.get());
    }

    public int height() {
        return Mth.clamp(height, 0, MAX_HEIGHT);
    }

    public void setDistance(int d) {
        distance = Mth.clamp(d, 1, CapsuleConfig.RELEASER_MAX_DISTANCE.get());
        setChanged();
    }

    public void setHeight(int h) {
        height = Mth.clamp(h, 0, MAX_HEIGHT);
        setChanged();
    }

    public int lastReleased() {
        return lastReleased;
    }

    public int filled() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (MobCapsuleItem.isFull(inventory.stack(i))) n++;
        return n;
    }

    /** Where the mobs come out: {@link #distance} blocks in front, {@link #height} up. */
    public BlockPos releasePoint() {
        Direction facing = getBlockState().getValue(DeviceBlock.FACING);
        return worldPosition.relative(facing, distance()).above(height());
    }

    @Override
    public void redstone(ServerLevel level, boolean signal) {
        if (signal && !powered) releaseAll(level);
        if (signal != powered) {
            powered = signal;
            setChanged();
        }
    }

    @Override
    public void serverTick(ServerLevel level) {
        setActive(filled() > 0);
    }

    /** Spots around the release point, nearest first: the point, then rings around it, also a little higher. */
    private List<BlockPos> spots(BlockPos centre) {
        List<BlockPos> out = new ArrayList<>();
        for (int dy = 0; dy <= 2; dy++) {
            for (int r = 0; r <= 3; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == r) out.add(centre.offset(dx * 2, dy * 2, dz * 2));
                    }
                }
            }
        }
        return out;
    }

    /** Lets every captured mob out; returns how many came out. */
    public int releaseAll(ServerLevel level) {
        BlockPos centre = releasePoint();
        List<BlockPos> spots = spots(centre);
        Direction facing = getBlockState().getValue(DeviceBlock.FACING);
        float yaw = facing.toYRot();
        int released = 0, next = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack capsule = inventory.stack(i);
            CapturedMob mob = MobCapsuleItem.captured(capsule);
            if (mob == null) continue;
            Entity out = null;
            while (out == null && next < spots.size()) {
                BlockPos at = spots.get(next++);
                if (!level.isLoaded(at)) continue;
                out = MobCapsuleItem.restore(level, mob, Vec3.atBottomCenterOf(at), yaw);
            }
            if (out == null) break; // no room left anywhere near the point
            MobCapsuleItem.empty(capsule);
            released++;
            Vec3 c = out.position().add(0, out.getBbHeight() / 2, 0);
            level.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 40, 0.3, 0.4, 0.3, 0.5);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 20, 0.3, 0.4, 0.3, 0.1);
        }
        if (released > 0) {
            inventory.changed();
            Vec3 c = Vec3.atCenterOf(centre);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1f, 0.7f);
            level.playSound(null, worldPosition, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1f, 0.8f);
        } else {
            level.playSound(null, worldPosition, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1f, 1.2f);
        }
        lastReleased = released;
        setChanged();
        return released;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MobReleaserMenu(id, inventory, this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        distance = input.getIntOr("distance", 3);
        height = input.getIntOr("height", 0);
        powered = input.getBooleanOr("powered", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("distance", distance);
        output.putInt("height", height);
        output.putBoolean("powered", powered);
    }
}
