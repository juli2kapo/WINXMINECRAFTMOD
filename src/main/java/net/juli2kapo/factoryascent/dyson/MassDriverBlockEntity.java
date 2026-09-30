package net.juli2kapo.factoryascent.dyson;

import java.util.UUID;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The Mass Driver's breech: an electromagnetic launcher that fires Solar Collectors straight up
 * into solar orbit, where they join the owner's team's Dyson swarm ({@link DysonService}).
 *
 * <p>The structure is the breech with {@link #RAILS} Mass Driver Rails stacked on top and open
 * sky above them (any sky in space). It holds up to {@link #MAX_STORED} collectors (by hand,
 * hopper or pipe), charges its coils from cables on any side of the breech
 * ({@link DysonConfig#LAUNCH_ENERGY} FE a shot) and fires one collector at most every
 * {@link DysonConfig#LAUNCH_TICKS} ticks. The coils light up rail by rail as they charge and the
 * shot leaves a streak into the sky (drawn by the client from the synced {@link #lastShot}).
 */
public class MassDriverBlockEntity extends BlockEntity {
    public static final int RAILS = 4;
    public static final int MAX_STORED = 64;
    public static final int MAX_INSERT = 65_536;
    /** Ticks the shot's streak lasts on clients. */
    public static final int SHOT_TICKS = 24;

    public static final int STATUS_READY = 0, STATUS_CHARGING = 1, STATUS_INCOMPLETE = 2, STATUS_EMPTY = 3,
            STATUS_BLOCKED = 4, STATUS_COMPLETE = 5, STATUS_UNOWNED = 6;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(capacity(), MAX_INSERT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private @Nullable UUID owner;
    private int stored;
    private int cooldown;
    /** Game time of the last shot (synced for the renderer), or -1. */
    private long lastShot = -1;
    /** Charge level the clients last saw (0..RAILS), to re-sync only when it changes. */
    private int shownCharge = -1;
    private int status = STATUS_EMPTY;

    public MassDriverBlockEntity(BlockPos pos, BlockState state) {
        super(DysonContent.MASS_DRIVER_BE.get(), pos, state);
    }

    private static int capacity() {
        return Math.max(1, DysonConfig.LAUNCH_ENERGY.get() * 2);
    }

    // ---------------------------------------------------------------- state

    public EnergyHandler energyHandler() {
        return energy;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    /** Fills the coils (creative/testing helper). */
    public void fill() {
        energy.set(energy.getCapacityAsInt());
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public int stored() {
        return stored;
    }

    public long lastShot() {
        return lastShot;
    }

    public int status() {
        return status;
    }

    /** 0..1: how far the coils are charged towards the next shot. */
    public float charge() {
        int need = DysonConfig.LAUNCH_ENERGY.get();
        return need <= 0 ? 1f : Math.min(1f, energy.getAmountAsInt() / (float) need);
    }

    /** Charge in whole rails (what the clients see). */
    public int chargeRails() {
        return shownCharge < 0 ? 0 : shownCharge;
    }

    /** The breech with {@link #RAILS} rails stacked on top. */
    public boolean isFormed() {
        if (level == null) return false;
        for (int i = 1; i <= RAILS; i++) {
            if (!(level.getBlockState(worldPosition.above(i)).getBlock() instanceof MassDriverRailBlock)) return false;
        }
        return true;
    }

    /** Open sky above the muzzle (always open in space). */
    public boolean skyClear() {
        if (level == null) return false;
        if (SpaceRules.isAirless(level)) return true;
        BlockPos muzzle = worldPosition.above(RAILS + 1);
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling() && level.canSeeSky(muzzle);
    }

    // ---------------------------------------------------------------- collectors in

    public static boolean isCollector(ItemStack stack) {
        return stack.is(DysonContent.DYSON_COLLECTOR.get());
    }

    /** Takes as many collectors from the stack as fit; returns how many. The caller shrinks the stack. */
    public int insert(ItemStack stack) {
        if (!isCollector(stack)) return 0;
        int n = Math.min(stack.getCount(), MAX_STORED - stored);
        if (n > 0) {
            stored += n;
            changed();
        }
        return n;
    }

    /** Takes collectors back out (sneak-use with an empty hand). */
    public ItemStack takeOut() {
        if (stored <= 0) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(DysonContent.DYSON_COLLECTOR.get(), stored);
        stored = 0;
        changed();
        return out;
    }

    // ---------------------------------------------------------------- the launch

    private int computeStatus(ServerLevel level) {
        if (!isFormed()) return STATUS_INCOMPLETE;
        if (owner == null) return STATUS_UNOWNED;
        if (DysonSwarm.get(level.getServer()).isComplete(DysonService.teamOf(level.getServer(), owner))) return STATUS_COMPLETE;
        if (stored <= 0) return STATUS_EMPTY;
        if (!skyClear()) return STATUS_BLOCKED;
        if (cooldown > 0 || energy.getAmountAsInt() < DysonConfig.LAUNCH_ENERGY.get()) return STATUS_CHARGING;
        return STATUS_READY;
    }

    public void serverTick(ServerLevel level) {
        if (cooldown > 0) cooldown--;
        status = computeStatus(level);
        int rails = Math.min(RAILS, (int) (charge() * RAILS + 1e-4));
        if (rails != shownCharge) {
            shownCharge = rails;
            changed();
        }
        if (status == STATUS_CHARGING && level.getGameTime() % 30 == 0 && charge() > 0.2f) {
            level.playSound(null, worldPosition.above(2), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6f, 0.6f + charge());
        }
        if (status == STATUS_READY) fire(level);
    }

    /** Fires one collector (the structure, sky, energy and cooldown were checked). */
    private void fire(ServerLevel level) {
        MinecraftServer server = level.getServer();
        String team = DysonService.teamOf(server, owner);
        energy.set(Math.max(0, energy.getAmountAsInt() - DysonConfig.LAUNCH_ENERGY.get()));
        stored--;
        cooldown = DysonConfig.LAUNCH_TICKS.get();
        lastShot = level.getGameTime();
        shownCharge = 0;
        changed();
        double x = worldPosition.getX() + 0.5, z = worldPosition.getZ() + 0.5, top = worldPosition.getY() + RAILS + 1.0;
        level.playSound(null, worldPosition.above(RAILS), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 2.5f, 1.6f);
        level.playSound(null, worldPosition.above(RAILS), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 1.5f, 1.8f);
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2f, 1.6f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, x, top, z, 40, 0.3, 0.2, 0.3, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, true, true, x, top + 2, z, 30, 0.05, 3.0, 0.05, 0.02);
        level.sendParticles(ParticleTypes.CLOUD, true, false, x, top, z, 12, 0.4, 0.1, 0.4, 0.05);
        for (int i = 0; i < RAILS; i++) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, worldPosition.getY() + 1.5 + i, z, 6, 0.45, 0.3, 0.45, 0.2);
        }
        DysonService.addCollectors(server, team, 1);
    }

    /** One line of status for the overlay (right-click with an empty hand). */
    public Component statusLine() {
        String key = switch (status) {
            case STATUS_READY -> "ready";
            case STATUS_CHARGING -> "charging";
            case STATUS_INCOMPLETE -> "incomplete";
            case STATUS_BLOCKED -> "blocked";
            case STATUS_COMPLETE -> "complete";
            case STATUS_UNOWNED -> "unowned";
            default -> "empty";
        };
        ChatFormatting color = switch (status) {
            case STATUS_READY, STATUS_CHARGING -> ChatFormatting.AQUA;
            case STATUS_COMPLETE -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.RED;
        };
        return Component.translatable("message.factoryascent.mass_driver", stored, MAX_STORED, Math.round(charge() * 100),
                Component.translatable("message.factoryascent.mass_driver." + key, RAILS)).withStyle(color);
    }

    // ---------------------------------------------------------------- automation

    private final Journal journal = new Journal();
    private final Items items = new Items();

    /** What hoppers and pipes see (any side): one insert-only slot for Solar Collectors. */
    public ResourceHandler<ItemResource> itemHandler() {
        return items;
    }

    private final class Journal extends SnapshotJournal<Integer> {
        @Override
        protected Integer createSnapshot() {
            return stored;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            stored = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            changed();
        }
    }

    private final class Items implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return stored > 0 ? ItemResource.of(DysonContent.DYSON_COLLECTOR.get()) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return stored;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return MAX_STORED;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return isCollector(resource.toStack(1));
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || !isValid(index, resource)) return 0;
            int fits = Math.min(amount, MAX_STORED - stored);
            if (fits <= 0) return 0;
            journal.updateSnapshots(tx);
            stored += fits;
            return fits;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- save / sync

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The stored collectors drop when the breech is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide() && stored > 0) {
            ItemStack out = takeOut();
            while (!out.isEmpty()) Block.popResource(level, pos, out.split(out.getMaxStackSize()));
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
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        stored = input.getIntOr("stored", 0);
        cooldown = input.getIntOr("cooldown", 0);
        lastShot = input.getLongOr("last_shot", -1L);
        shownCharge = input.getIntOr("charge_rails", 0);
        energy.set(Math.min(input.getIntOr("energy", 0), energy.getCapacityAsInt()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
        output.putInt("stored", stored);
        output.putInt("cooldown", cooldown);
        output.putLong("last_shot", lastShot);
        output.putInt("charge_rails", Math.max(0, shownCharge));
        output.putInt("energy", energy.getAmountAsInt());
    }

    /** For the renderer: whether the level is a client level and the shot is still visible. */
    public float shotAge(Level level, float partialTicks) {
        return lastShot < 0 ? -1 : level.getGameTime() - lastShot + partialTicks;
    }
}
