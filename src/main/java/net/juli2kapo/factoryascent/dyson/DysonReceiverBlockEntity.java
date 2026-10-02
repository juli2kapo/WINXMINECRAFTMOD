package net.juli2kapo.factoryascent.dyson;

import net.juli2kapo.factoryascent.util.SkyAccess;
import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Dyson Receiver: the centre of a 3×3 rectenna (eight Receiver Arrays around it) under a big
 * dish that tracks the sun. It turns the owner's team's Dyson swarm into FE: each tick the swarm
 * beams down {@code collectors × }{@link DysonConfig#FE_PER_COLLECTOR} FE times the receiver's
 * {@link #exposure sun exposure} (daylight and open sky on a planet, always in space), shared by all
 * of the team's receivers, each taking at most {@link DysonConfig#RECEIVER_MAX_OUTPUT} FE/t. The
 * energy leaves through the bottom of the centre block (and its top) into cables.
 */
public class DysonReceiverBlockEntity extends BlockEntity {
    private static final Direction[] OUTPUTS = {Direction.DOWN, Direction.UP};

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(bufferSize(), 0, Integer.MAX_VALUE) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private @Nullable UUID owner;
    /** Sun exposure in percent (last tick). */
    private int exposure;
    /** FE taken from the swarm last tick, and pushed out. */
    private int lastIn, lastOut;
    /** Whether the swarm shines on it: clients show the beam (synced when it flips). */
    private boolean active;
    private boolean awarded;
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<EnergyHandler, @Nullable Direction>[] outputs = new BlockCapabilityCache[OUTPUTS.length];

    public DysonReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(DysonContent.DYSON_RECEIVER_BE.get(), pos, state);
    }

    private static int bufferSize() {
        return (int) Math.min(Integer.MAX_VALUE, DysonConfig.RECEIVER_MAX_OUTPUT.get() * 20L);
    }

    // ---------------------------------------------------------------- state

    public EnergyHandler energyHandler() {
        return energy;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** FE the receiver took from the swarm last tick. */
    public int lastIn() {
        return lastIn;
    }

    public int lastOut() {
        return lastOut;
    }

    public boolean active() {
        return active;
    }

    /** All eight blocks around the centre are Receiver Arrays. */
    public boolean isFormed() {
        if (level == null) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !(level.getBlockState(worldPosition.offset(dx, 0, dz)).getBlock() instanceof DysonReceiverArrayBlock)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * How much of the swarm's light reaches a receiver here, 0..1: always all of it in space (the
     * sun never sets there); on a planet it needs open sky above and daylight, and rain and storms
     * scatter some of it. Dimensions without a sky (or with a ceiling) get nothing.
     */
    public static float exposure(Level level, BlockPos pos) {
        if (SpaceRules.isAirless(level) && level.dimensionType().hasSkyLight()) return 1f;
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) return 0f;
        if (!SkyAccess.canSeeSky(level, pos.above())) return 0f;
        if (level.isDarkOutside()) return 0f;
        if (level.isThundering()) return 0.3f;
        if (level.isRaining()) return 0.6f;
        return 1f;
    }

    // ---------------------------------------------------------------- tick

    public void serverTick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        exposure = Math.round(exposure(level, worldPosition) * 100);
        lastIn = 0;
        boolean lit = false;
        if (owner != null && isFormed() && exposure > 0) {
            String team = DysonService.teamOf(server, owner);
            DysonSwarm swarm = DysonSwarm.get(server);
            long budget = swarm.swarmPower(team) * exposure / 100;
            lit = budget > 0;
            long want = Math.min(DysonConfig.RECEIVER_MAX_OUTPUT.get(), energy.getCapacityAsLong() - energy.getAmountAsLong());
            long got = swarm.take(team, server.overworld().getGameTime(), budget, Math.min(budget, want));
            if (got > 0) {
                lastIn = (int) got;
                energy.set((int) Math.min(energy.getCapacityAsLong(), energy.getAmountAsLong() + got));
                if (!awarded) {
                    awarded = true;
                    DysonService.award(server, FactoryTeams.get(server).members(team), "dyson_receiver");
                }
            }
        }
        lastOut = push(level);
        boolean nowActive = lit; // the beam shows while the swarm shines on it, even with a full buffer
        if (nowActive != active) {
            active = nowActive;
            changed();
        }
        if (active && level.getGameTime() % 10 == 0) {
            double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.6, z = worldPosition.getZ() + 0.5;
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 2, 0.8, 0.2, 0.8, 0.01);
        }
    }

    /** Pushes the buffer into cables below and above the centre block; returns FE moved. */
    private int push(ServerLevel level) {
        int budget = Math.min(energy.getAmountAsInt(), DysonConfig.RECEIVER_MAX_OUTPUT.get());
        int moved = 0;
        for (int i = 0; i < OUTPUTS.length && moved < budget; i++) {
            if (outputs[i] == null) {
                outputs[i] = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level,
                        worldPosition.relative(OUTPUTS[i]), OUTPUTS[i].getOpposite());
            }
            EnergyHandler target = outputs[i].getCapability();
            if (target == null) continue;
            try (Transaction tx = Transaction.openRoot()) {
                moved += target.insert(budget - moved, tx);
                tx.commit();
            }
        }
        if (moved > 0) energy.set(energy.getAmountAsInt() - moved);
        return moved;
    }

    public Component statusLine(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (!isFormed()) return Component.translatable("message.factoryascent.dyson_receiver.incomplete").withStyle(ChatFormatting.RED);
        if (owner == null) return Component.translatable("message.factoryascent.dyson_receiver.unowned").withStyle(ChatFormatting.RED);
        long collectors = DysonSwarm.get(server).collectors(DysonService.teamOf(server, owner));
        if (collectors == 0) return Component.translatable("message.factoryascent.dyson_receiver.no_swarm").withStyle(ChatFormatting.GOLD);
        int exp = Math.round(exposure(level, worldPosition) * 100);
        if (exp == 0) {
            // Say exactly why: night, or the first block that shades the receiver (and where).
            BlockPos blocker = net.juli2kapo.factoryascent.util.SkyAccess.blocker(level, worldPosition.above());
            if (blocker != null) {
                return Component.translatable("message.factoryascent.dyson_receiver.blocked", collectors,
                        level.getBlockState(blocker).getBlock().getName(), blocker.getX(), blocker.getY(), blocker.getZ())
                        .withStyle(ChatFormatting.GOLD);
            }
            if (level.isDarkOutside()) {
                return Component.translatable("message.factoryascent.dyson_receiver.night", collectors).withStyle(ChatFormatting.GOLD);
            }
            return Component.translatable("message.factoryascent.dyson_receiver.no_sun", collectors).withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("message.factoryascent.dyson_receiver.status", EnergyUtil.format(lastIn), collectors, exp)
                .withStyle(ChatFormatting.AQUA);
    }

    // ---------------------------------------------------------------- save / sync

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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
        active = input.getBooleanOr("active", false);
        energy.set(Math.min(input.getIntOr("energy", 0), energy.getCapacityAsInt()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
        output.putBoolean("active", active);
        output.putInt("energy", energy.getAmountAsInt());
    }
}
