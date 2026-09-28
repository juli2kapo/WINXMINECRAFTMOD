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
public class EnderBeaconBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {
    public static final int MAX_NAME = 32;

    private boolean hasPearl;
    private @Nullable UUID owner;
    /** Player-given name shown by linked Recall Charms; empty means the default name. */
    private String name = "";

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

    public @Nullable UUID owner() {
        return owner;
    }

    /** The custom name, or "" when it has none. */
    public String name() {
        return name;
    }

    /** What charms and the screen call this beacon. */
    public Component displayName() {
        return name.isEmpty() ? Component.translatable("block.factoryascent.ender_beacon") : Component.literal(name);
    }

    /** Sets the custom name (control characters removed, cut to {@link #MAX_NAME}); blank clears it. */
    public void setName(String newName) {
        String clean = net.minecraft.util.StringUtil.filterText(newName == null ? "" : newName).strip();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME);
        if (clean.equals(name)) return;
        name = clean;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The beacon screen's rename. Only the owner (or anyone, for an unowned beacon), within reach.
     * Returns whether the name was applied (for GameTests).
     */
    public static boolean handleRename(net.minecraft.server.level.ServerPlayer player, BlockPos pos, String newName) {
        if (!player.level().isLoaded(pos) || !player.isWithinBlockInteractionRange(pos, 4.0)) return false;
        if (!(player.level().getBlockEntity(pos) instanceof EnderBeaconBlockEntity beacon)) return false;
        if (!beacon.mayLink(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.beacon_not_yours")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        beacon.setName(newName);
        // Charms in the owner's inventory pick the new name up straight away.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            RecallCharmItem.refreshName(player.getInventory().getItem(i), player.level().dimension(), pos, beacon);
        }
        return true;
    }

    @Override
    public Component getDisplayName() {
        return displayName();
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
                                                                         net.minecraft.world.entity.player.Player player) {
        return new EnderBeaconMenu(id, inventory, this);
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
        name = input.getStringOr("name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("pearl", hasPearl);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
        if (!name.isEmpty()) output.putString("name", name);
    }
}
