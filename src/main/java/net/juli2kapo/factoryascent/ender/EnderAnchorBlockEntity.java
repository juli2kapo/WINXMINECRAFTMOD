package net.juli2kapo.factoryascent.ender;

import java.util.UUID;
import net.juli2kapo.factoryascent.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A stasis chamber holding one ender pearl. With the pearl in, it keeps the chunks around it
 * loaded for as long as it stands (through {@link EnderContent#ANCHOR_TICKETS}, which NeoForge
 * re-applies after a restart). Breaking the chamber releases the chunks and the pearl is lost.
 */
public class EnderAnchorBlockEntity extends BlockEntity {
    private boolean hasPearl;
    private @Nullable UUID owner;
    /** The radius the loaded chunks were forced with, or -1 when nothing is forced. */
    private int forcedRadius = -1;

    public EnderAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(EnderContent.ENDER_ANCHOR_BE.get(), pos, state);
    }

    public boolean hasPearl() {
        return hasPearl;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** Drops a pearl into the chamber; false if it already has one. */
    public boolean insertPearl() {
        if (hasPearl) return false;
        hasPearl = true;
        setChanged();
        if (level instanceof ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.BLOCKS, 1f, 1f);
            server.playSound(null, worldPosition, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.8f, 1.1f);
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            serverTick(server, getBlockState());
        }
        return true;
    }

    void serverTick(ServerLevel level, BlockState state) {
        // Cheap: only acts when something differs (first tick after loading, a config change, a new pearl).
        int radius = hasPearl ? Config.ANCHOR_RADIUS.get() : -1;
        setForced(level, radius);
        if (state.getValue(EnderAnchorBlock.ACTIVE) != hasPearl) {
            level.setBlock(worldPosition, state.setValue(EnderAnchorBlock.ACTIVE, hasPearl), Block.UPDATE_ALL);
        }
    }

    /** Forces (radius ≥ 0) or releases (radius -1) the chunks around the anchor, changing only on difference. */
    private void setForced(ServerLevel level, int radius) {
        if (radius == forcedRadius) return;
        ChunkPos center = ChunkPos.containing(worldPosition);
        if (forcedRadius >= 0) forceArea(level, center, forcedRadius, false);
        if (radius >= 0) forceArea(level, center, radius, true);
        forcedRadius = radius;
        setChanged();
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
        return hasPearl
                ? Component.translatable("message.factoryascent.anchor_active", side, side)
                : Component.translatable("message.factoryascent.anchor_empty");
    }

    /** Breaking the chamber frees the chunks; the pearl inside is lost with a puff. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            setForced(server, -1);
            AnchorLedger.get(server.getServer()).remove(GlobalPos.of(server.dimension(), pos));
            if (hasPearl) {
                server.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.6);
                server.playSound(null, pos, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 1f, 0.8f);
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hasPearl = input.getBooleanOr("pearl", false);
        forcedRadius = input.getIntOr("forced_radius", -1);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("pearl", hasPearl);
        output.putInt("forced_radius", forcedRadius);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
    }
}
