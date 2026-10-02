package net.juli2kapo.factoryascent.capsule;

import net.juli2kapo.factoryascent.mobs.CapturedMob;
import net.juli2kapo.factoryascent.mobs.MobCapsuleItem;
import net.juli2kapo.factoryascent.mobs.MobContent;
import net.juli2kapo.factoryascent.mobs.SizeRayItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Size Chamber: holds one filled Mob Capsule and works on the mob inside without letting it out.
 * First it resizes the mob to the target size set on its screen (within the size rays' limits,
 * {@code sizeRayMinScale}..{@code sizeRayMaxScale}), then it heals it to full health, both paid
 * in FE. The changes are written into the capsule, so the mob keeps them when it is released.
 */
public class SizeChamberBlockEntity extends DeviceBlockEntity {
    public static final int CAPACITY = 200_000, MAX_INSERT = 4_096;
    public static final int ST_EMPTY = 0, ST_RESIZING = 1, ST_HEALING = 2, ST_DONE = 3, ST_NO_POWER = 4;

    /** Target size in thousandths (1000 = normal). */
    private int target = 1000;
    private int resizeTicks;
    private int status;
    /** Fractional energy debt of healing (health is restored in small steps). */
    private double healCarry;

    public SizeChamberBlockEntity(BlockPos pos, BlockState state) {
        super(CapsuleContent.SIZE_CHAMBER_BE.get(), pos, state, 1, CAPACITY, MAX_INSERT);
    }

    @Override
    public boolean isValid(int slot, ItemResource resource) {
        return resource.is(MobContent.MOB_CAPSULE.get()) && resource.toStack(1).has(MobContent.CAPTURED_MOB.get());
    }

    @Override
    public int slotLimit(int slot) {
        return 1;
    }

    public static int minPermille() {
        return (int) Math.round(SizeRayItem.minSize() * 1000);
    }

    public static int maxPermille() {
        return (int) Math.round(SizeRayItem.maxSize() * 1000);
    }

    public int target() {
        return Mth.clamp(target, minPermille(), maxPermille());
    }

    public void setTarget(int permille) {
        target = Mth.clamp(permille, minPermille(), maxPermille());
        resizeTicks = 0;
        setChanged();
    }

    public int status() {
        return status;
    }

    public int resizeTicks() {
        return resizeTicks;
    }

    public static int resizeDuration() {
        return CapsuleConfig.RESIZE_SECONDS.get() * 20;
    }

    public @Nullable CapturedMob mob() {
        return MobCapsuleItem.captured(inventory.stack(0));
    }

    @Override
    public void serverTick(ServerLevel level) {
        ItemStack capsule = inventory.stack(0);
        CapturedMob mob = MobCapsuleItem.captured(capsule);
        if (mob == null) {
            status = ST_EMPTY;
            resizeTicks = 0;
            setActive(false);
            return;
        }
        double size = CapsuleOps.scaleOf(mob);
        double wanted = target() / 1000.0;
        if (Math.abs(size - wanted) > 0.0005) {
            int perTick = Math.max(0, CapsuleConfig.RESIZE_FE.get() / resizeDuration());
            if (!pay(perTick)) {
                status = ST_NO_POWER;
                setActive(false);
                return;
            }
            status = ST_RESIZING;
            setActive(true);
            if (++resizeTicks >= resizeDuration()) {
                resizeTicks = 0;
                CapturedMob resized = CapsuleOps.withScale(level, mob, wanted);
                MobCapsuleItem.fill(capsule, resized);
                inventory.changed();
                level.playSound(null, worldPosition, SoundEvents.PLAYER_TELEPORT, SoundSource.BLOCKS, 0.6f, wanted < size ? 1.8f : 0.6f);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                        worldPosition.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.05);
            } else if (resizeTicks % 10 == 0) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3f, 1.4f);
            }
            setChanged();
            return;
        }
        resizeTicks = 0;
        if (mob.health() < mob.maxHealth() - 1e-3) {
            float step = (float) Math.min(mob.maxHealth() - mob.health(), CapsuleConfig.HEAL_PER_SECOND.get() / 20.0);
            healCarry += step * CapsuleConfig.HEAL_FE_PER_HEALTH.get();
            int cost = (int) healCarry;
            if (!pay(cost)) {
                healCarry -= step * CapsuleConfig.HEAL_FE_PER_HEALTH.get();
                status = ST_NO_POWER;
                setActive(false);
                return;
            }
            healCarry -= cost;
            MobCapsuleItem.fill(capsule, CapsuleOps.withHealth(mob, mob.health() + step));
            inventory.changed();
            status = ST_HEALING;
            setActive(true);
            if (level.getGameTime() % 20 == 0) {
                level.sendParticles(ParticleTypes.HEART, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                        worldPosition.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0);
            }
            return;
        }
        status = ST_DONE;
        setActive(false);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SizeChamberMenu(id, inventory, this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        target = input.getIntOr("target", 1000);
        resizeTicks = input.getIntOr("resize", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("target", target);
        output.putInt("resize", resizeTicks);
    }
}
