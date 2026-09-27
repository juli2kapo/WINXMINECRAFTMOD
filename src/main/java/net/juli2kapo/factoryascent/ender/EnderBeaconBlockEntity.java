package net.juli2kapo.factoryascent.ender;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * An Ender Beacon works like a vanilla stasis chamber: a pearl hangs in its bubble column, and a
 * linked Recall Charm is the remote trigger. Recalling uses the pearl up; load another for the
 * next trip. Remembers who placed it (only they can link charms).
 */
public class EnderBeaconBlockEntity extends BlockEntity {
    private boolean hasPearl;
    private @Nullable UUID owner;

    public EnderBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(EnderContent.ENDER_BEACON_BE.get(), pos, state);
    }

    public boolean hasPearl() {
        return hasPearl;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** Anyone may link to an unowned beacon; otherwise only its owner. */
    boolean mayLink(UUID player) {
        return owner == null || owner.equals(player);
    }

    /** Loads a pearl; false if one is already inside. */
    public boolean insertPearl() {
        if (hasPearl) return false;
        setPearl(true);
        if (level instanceof ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.BLOCKS, 1f, 1f);
        }
        return true;
    }

    /** The recall triggers the pearl: it is used up. False if the chamber is empty. */
    boolean triggerPearl() {
        if (!hasPearl) return false;
        setPearl(false);
        return true;
    }

    private void setPearl(boolean pearl) {
        hasPearl = pearl;
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            if (state.getValue(EnderBeaconBlock.LOADED) != pearl) {
                level.setBlock(worldPosition, state.setValue(EnderBeaconBlock.LOADED, pearl), Block.UPDATE_ALL);
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    Component statusLine() {
        return Component.translatable(hasPearl ? "message.factoryascent.beacon_loaded" : "message.factoryascent.beacon_empty");
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
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("pearl", hasPearl);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
    }
}
