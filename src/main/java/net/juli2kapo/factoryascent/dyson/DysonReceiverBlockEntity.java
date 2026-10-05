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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
    // Only downwards: a cable on top would shade the receiver from the sun.
    private static final Direction[] OUTPUTS = {Direction.DOWN};

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
    /** Something stood in the beam on the last check: it shades the receiver and gets burned. */
    private boolean shaded;
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
        if (active && level.getGameTime() % 5 == 0) shaded = scorchBeam(level);
        else if (!active) shaded = false;
        MinecraftServer server = level.getServer();
        exposure = Math.round(exposure(level, worldPosition) * 100);
        lastIn = 0;
        boolean lit = false;
        if (owner != null && isFormed() && exposure > 0) {
            String team = DysonService.teamOf(server, owner);
            DysonSwarm swarm = DysonSwarm.get(server);
            long budget = swarm.swarmPower(team) * exposure / 100;
            if (shaded) budget /= 4; // a body in the beam blocks most of the light
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

    /** How far up the beam is dangerous, in blocks. */
    private static final double BEAM_LENGTH = 256;

    /**
     * The beam between the dish and the sun: anything alive standing in it catches fire and takes
     * damage that grows with the power coming down (2 to 12 per hit, every quarter second), and its
     * body shades the receiver. Returns whether anything was in the way.
     */
    private boolean scorchBeam(ServerLevel level) {
        Vec3 origin = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 10 / 16.0, worldPosition.getZ() + 0.5);
        Vec3 dir = beamDirection(level, origin);
        Vec3 end = origin.add(dir.scale(BEAM_LENGTH));
        AABB box = new AABB(origin, end).inflate(1.0);
        boolean hit = false;
        float damage = (float) Math.max(2.0, Math.min(12.0, lastIn / 2048.0));
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.isSpectator() || (entity instanceof Player p && p.isCreative())) continue;
            if (entity.getBoundingBox().inflate(0.25).clip(origin, end).isEmpty()) continue;
            hit = true;
            entity.igniteForSeconds(4f);
            entity.hurtServer(level, level.damageSources().inFire(), damage);
            level.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    6, 0.25, 0.4, 0.25, 0.02);
            if (entity instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.factoryascent.dyson_beam_burn").withStyle(ChatFormatting.GOLD));
            }
        }
        return hit;
    }

    /**
     * Where the beam points: the same tilt the dish shows (towards the sun, clamped to ±72° from
     * straight up, and straight up at night), around the north-south axis like the sun's path.
     */
    static Vec3 beamDirection(ServerLevel level, Vec3 at) {
        float sun = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, at);
        sun = ((sun % 360f) + 540f) % 360f - 180f; // (-180, 180], 0 = noon
        float tilt = Math.abs(sun) > 95f ? 0f : Math.max(-72f, Math.min(72f, sun));
        double r = Math.toRadians(tilt);
        return new Vec3(-Math.sin(r), Math.cos(r), 0);
    }

    /** Pushes the buffer into the cable below the centre block; returns FE moved. */
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
