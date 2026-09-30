package net.juli2kapo.factoryascent.space;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.space.station.AirVolume;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * The Oxygen Sealer (and its little brother the Air Vent): while powered it floods the sealed room
 * around it with breathable air, so a station crew can take off their helmets. Once a second it
 * re-measures the room ({@link AirVolume}): closed and no bigger than its limit
 * ({@link SpaceConfig#AIR_VOLUME_LIMIT}, a quarter for a vent) means sealed; open to space means
 * no air. When a sealed room is breached the air leaks out for a few seconds
 * ({@link SpaceConfig#AIR_LEAK_SECONDS}): a warning, a hiss and puffs of air streaming out of each
 * opening, then the room is in vacuum until it is closed again.
 */
public class OxygenSealerBlockEntity extends BlockEntity {
    public static final int CAPACITY = 64_000;
    public static final int MAX_INSERT = 1_000;
    /** Ticks between re-measuring the room. */
    public static final int SCAN_INTERVAL = 20;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INSERT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private boolean running;
    /** The room currently full of air (null: none). */
    private @Nullable LongOpenHashSet room;
    /** Ticks of leaking left after a breach (the old room still has some air). */
    private int leakTicks;
    private List<BlockPos[]> breaches = List.of();
    private int scanIn;
    private boolean everSealed;
    /** Game tests: a smaller room limit than the config's (0: use the config). */
    private int limitOverride;

    public OxygenSealerBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContent.OXYGEN_SEALER_BE.get(), pos, state);
    }

    public EnergyHandler energyHandler() {
        return energy;
    }

    public int energy() {
        return energy.getAmountAsInt();
    }

    public void fillEnergy() {
        energy.set(CAPACITY);
    }

    public boolean running() {
        return running;
    }

    public boolean vent() {
        return getBlockState().getBlock() instanceof OxygenMachineBlock b && b.kind() == OxygenMachineBlock.Kind.VENT;
    }

    /** Largest room this block can fill. */
    public int limit() {
        if (limitOverride > 0) return limitOverride;
        int limit = SpaceConfig.airVolumeLimit();
        return vent() ? Math.max(8, limit / 4) : limit;
    }

    public int energyPerTick() {
        int cost = SpaceConfig.get(SpaceConfig.SEALER_ENERGY);
        return vent() ? cost / 4 : cost;
    }

    /** Sealed and full of air (not leaking). */
    public boolean sealed() {
        return running && room != null && leakTicks == 0;
    }

    public boolean leaking() {
        return running && leakTicks > 0;
    }

    /** Blocks of air in the room (0 if not sealed). */
    public int volume() {
        return room == null ? 0 : room.size();
    }

    /** Game tests: caps the room size (the test arena itself is a closed box a few hundred blocks big). */
    public void setLimitForTest(int limit) {
        limitOverride = limit;
    }

    /** Forces the next tick to re-measure the room (tests, block changes). */
    public void rescanSoon() {
        scanIn = 0;
    }

    public void serverTick(ServerLevel level) {
        int cost = energyPerTick();
        boolean now = energy.getAmountAsInt() >= cost;
        if (now) energy.set(energy.getAmountAsInt() - cost);
        if (!now) {
            if (room != null) SpaceRules.setAirZone(level, worldPosition, null);
            room = null;
            leakTicks = 0;
        } else {
            if (--scanIn <= 0) {
                scanIn = SCAN_INTERVAL;
                measure(level);
            }
            if (leakTicks > 0) leak(level);
            if (level.getGameTime() % 20 == 0 && room != null) {
                level.sendParticles(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                        worldPosition.getZ() + 0.5, 2, 0.25, 0.05, 0.25, 0.01);
            }
        }
        if (now != running) {
            running = now;
            BlockState state = getBlockState();
            if (state.hasProperty(OxygenMachineBlock.LIT)) level.setBlock(worldPosition, state.setValue(OxygenMachineBlock.LIT, now), Block.UPDATE_CLIENTS);
        }
    }

    private void measure(ServerLevel level) {
        AirVolume.Result result = AirVolume.fromSource(level, worldPosition, limit());
        if (result.sealed()) {
            boolean fresh = room == null || leakTicks > 0;
            room = result.cells();
            leakTicks = 0;
            breaches = List.of();
            SpaceRules.setAirZone(level, worldPosition, room);
            if (fresh && SpaceRules.isAirless(level)) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.4f);
                for (ServerPlayer p : playersInside(level)) {
                    SpaceContent.award(p, "space_sealed");
                    if (!everSealed) {
                        p.sendOverlayMessage(Component.translatable("message.factoryascent.air_sealed", room.size()).withStyle(ChatFormatting.AQUA));
                    }
                }
                everSealed = true;
            }
        } else if (room != null && leakTicks == 0) {
            // just breached: the air streams out of every opening for a few seconds
            breaches = AirVolume.breaches(room, p -> AirVolume.classify(level, p), 8);
            leakTicks = Math.max(1, SpaceConfig.get(SpaceConfig.AIR_LEAK_SECONDS) * 20);
            if (SpaceRules.isAirless(level)) {
                level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.2f, 0.5f);
                for (ServerPlayer p : playersInside(level)) {
                    p.sendOverlayMessage(Component.translatable("message.factoryascent.air_breach").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            }
        }
    }

    private void leak(ServerLevel level) {
        leakTicks--;
        if (leakTicks % 3 == 0) {
            for (BlockPos[] b : breaches) {
                Vec3 in = Vec3.atCenterOf(b[0]), out = Vec3.atCenterOf(b[1]);
                Vec3 dir = out.subtract(in).normalize();
                level.sendParticles(ParticleTypes.CLOUD, out.x, out.y, out.z, 0, dir.x, dir.y, dir.z, 0.35);
                level.sendParticles(ParticleTypes.POOF, in.x, in.y, in.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
        }
        if (leakTicks % 20 == 10) level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 0.4f);
        if (leakTicks == 0) {
            room = null;
            SpaceRules.setAirZone(level, worldPosition, null);
            scanIn = 0;
        }
    }

    /** Players whose head is in this block's room. */
    public List<ServerPlayer> playersInside(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        if (room == null) return out;
        for (ServerPlayer p : level.players()) {
            if (room.contains(BlockPos.containing(p.getEyePosition()).asLong())) out.add(p);
        }
        return out;
    }

    public Component status() {
        if (!running) {
            return Component.translatable("message.factoryascent.sealer_off", energyPerTick()).withStyle(ChatFormatting.RED);
        }
        if (leaking()) return Component.translatable("message.factoryascent.sealer_leaking").withStyle(ChatFormatting.GOLD);
        if (room != null) {
            return Component.translatable("message.factoryascent.sealer_on", room.size(), EnergyUtil.format(energy())).withStyle(ChatFormatting.AQUA);
        }
        return Component.translatable("message.factoryascent.sealer_open", limit()).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) SpaceRules.setAirZone(level, worldPosition, null);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        everSealed = input.getBooleanOr("ever_sealed", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putBoolean("ever_sealed", everSealed);
    }
}
