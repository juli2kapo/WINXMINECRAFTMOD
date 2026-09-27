package net.juli2kapo.factoryascent.ender;

import java.util.UUID;
import net.juli2kapo.factoryascent.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * Burns ender pearls to keep the chunks around it loaded (through {@link EnderContent#ANCHOR_TICKETS},
 * which NeoForge re-applies after a restart). The pearl slot accepts hoppers and pipes.
 */
public class EnderAnchorBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 20;

    private final ItemStacksResourceHandler pearls = new ItemStacksResourceHandler(1) {
        @Override
        public boolean isValid(int index, ItemResource resource) {
            return resource.is(Items.ENDER_PEARL);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };
    private int fuelTicks;
    private @Nullable UUID owner;
    /** The radius the loaded chunks were forced with, or -1 when nothing is forced. */
    private int forcedRadius = -1;

    public EnderAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(EnderContent.ENDER_ANCHOR_BE.get(), pos, state);
    }

    public ItemStacksResourceHandler pearls() {
        return pearls;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    private ItemStack pearlStack() {
        return pearls.getResource(0).toStack(pearls.getAmountAsInt(0));
    }

    /** Puts up to {@code count} pearls into the slot; returns how many it took. */
    public int addPearls(int count) {
        ItemStack current = pearlStack();
        int room = Items.ENDER_PEARL.getDefaultMaxStackSize() - current.getCount();
        int added = Math.max(0, Math.min(room, count));
        if (added > 0) {
            pearls.set(0, ItemResource.of(Items.ENDER_PEARL), current.getCount() + added);
            setChanged();
        }
        return added;
    }

    void serverTick(ServerLevel level, BlockState state) {
        if (level.getGameTime() % CHECK_INTERVAL != 0) return;
        boolean needsFuel = Config.ANCHORS_NEED_FUEL.get();
        if (needsFuel && fuelTicks <= 0) {
            ItemStack stack = pearlStack();
            if (!stack.isEmpty()) {
                pearls.set(0, ItemResource.of(Items.ENDER_PEARL), stack.getCount() - 1);
                fuelTicks += Config.ANCHOR_MINUTES_PER_PEARL.get() * 60 * 20;
                level.playSound(null, worldPosition, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 0.6f, 1.2f);
            }
        }
        boolean active = !needsFuel || fuelTicks > 0;
        if (active && needsFuel) fuelTicks = Math.max(0, fuelTicks - CHECK_INTERVAL);
        setForced(level, active ? Config.ANCHOR_RADIUS.get() : -1);
        if (state.getValue(EnderAnchorBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(EnderAnchorBlock.ACTIVE, active), 3);
            level.playSound(null, worldPosition, active ? SoundEvents.END_PORTAL_FRAME_FILL : SoundEvents.ENDER_EYE_DEATH,
                    SoundSource.BLOCKS, 0.8f, active ? 1.0f : 0.6f);
        }
        setChanged();
    }

    /** Forces (radius ≥ 0) or releases (radius -1) the chunks around the anchor, changing only on difference. */
    private void setForced(ServerLevel level, int radius) {
        if (radius == forcedRadius) return;
        ChunkPos center = ChunkPos.containing(worldPosition);
        if (forcedRadius >= 0) forceArea(level, center, forcedRadius, false);
        if (radius >= 0) forceArea(level, center, radius, true);
        forcedRadius = radius;
    }

    private void forceArea(ServerLevel level, ChunkPos center, int radius, boolean add) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                EnderContent.ANCHOR_TICKETS.forceChunk(level, worldPosition, center.x() + dx, center.z() + dz, add, true);
            }
        }
    }

    Component statusLine() {
        int side = Config.ANCHOR_RADIUS.get() * 2 + 1;
        int pearlsLeft = pearlStack().getCount();
        if (!Config.ANCHORS_NEED_FUEL.get()) {
            return Component.translatable("message.factoryascent.anchor_free", side, side);
        }
        if (fuelTicks <= 0 && pearlsLeft == 0) {
            return Component.translatable("message.factoryascent.anchor_asleep");
        }
        long totalMinutes = (fuelTicks + (long) pearlsLeft * Config.ANCHOR_MINUTES_PER_PEARL.get() * 1200) / 1200;
        return Component.translatable("message.factoryascent.anchor_status", side, side,
                totalMinutes / 60, totalMinutes % 60, pearlsLeft);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            setForced(server, -1);
            // Belt and braces: drop every ticket this position owns, whatever the radius was.
            AnchorLedger.get(server.getServer()).remove(GlobalPos.of(server.dimension(), pos));
            ItemStack stack = pearlStack();
            if (!stack.isEmpty()) Containers.dropContents(server, pos, NonNullList.of(ItemStack.EMPTY, stack));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pearls.deserialize(input.childOrEmpty("pearls"));
        fuelTicks = input.getIntOr("fuel", 0);
        forcedRadius = input.getIntOr("forced_radius", -1);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        pearls.serialize(output.child("pearls"));
        output.putInt("fuel", fuelTicks);
        output.putInt("forced_radius", forcedRadius);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
    }
}
