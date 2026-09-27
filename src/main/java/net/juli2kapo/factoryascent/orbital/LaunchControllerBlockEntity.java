package net.juli2kapo.factoryascent.orbital;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Launch Pad's controller: the mounted satellite (and who mounted it), the fuel tank, and the
 * launch sequence.
 *
 * <p>Launch sequence ({@link #SEQUENCE} ticks): a countdown with smoke and flames at the base,
 * liftoff at {@link #LIFTOFF}, then the rocket climbs ever faster (drawn by the client renderer
 * from the synced start time; the server puts the exhaust trail at the same height). At the end
 * the satellite is added to the launcher's team in {@link OrbitRegistry} and the team is told.
 */
public class LaunchControllerBlockEntity extends BlockEntity {
    public static final int FUEL_PER_LAUNCH = 4;
    public static final int FUEL_MAX = 8;
    public static final int LIFTOFF = 60;
    public static final int SEQUENCE = 100;
    /** Rocket height above the pad after liftoff: {@code ACCEL * t²} blocks, t in ticks since liftoff. */
    public static final float ACCEL = 0.075f;

    private ItemStack satellite = ItemStack.EMPTY;
    private @Nullable UUID launcher;
    private int fuel;
    /** Ticks into the launch sequence, or -1 when idle. */
    private int launchTick = -1;
    /** Game time the sequence started (synced for the renderer), or -1. */
    private long launchStart = -1;

    public LaunchControllerBlockEntity(BlockPos pos, BlockState state) {
        super(OrbitalContent.LAUNCH_CONTROLLER_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- state

    public ItemStack satellite() {
        return satellite;
    }

    public @Nullable SatelliteType satelliteType() {
        return satellite.getItem() instanceof SatelliteItem item ? item.type() : null;
    }

    public int fuel() {
        return fuel;
    }

    public boolean launching() {
        return launchTick >= 0;
    }

    public long launchStart() {
        return launchStart;
    }

    /** Fuel units an item is worth: Blaze Powder 1, Rocket Fuel 4. */
    public static int fuelValue(ItemStack stack) {
        if (stack.is(Items.BLAZE_POWDER)) return 1;
        if (stack.is(OrbitalContent.ROCKET_FUEL.get())) return FUEL_PER_LAUNCH;
        return 0;
    }

    /** All eight plates around the controller are Launch Pad blocks. */
    public boolean isFormed() {
        if (level == null) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !(level.getBlockState(worldPosition.offset(dx, 0, dz)).getBlock() instanceof LaunchPadBlock)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- actions

    /** Mounts one satellite from the stack; null on success, else why not. The caller shrinks the stack. */
    public @Nullable Component mount(ItemStack stack, UUID player) {
        if (launching()) return Component.translatable("message.factoryascent.pad_busy").withStyle(ChatFormatting.RED);
        if (!satellite.isEmpty()) return Component.translatable("message.factoryascent.pad_occupied").withStyle(ChatFormatting.RED);
        if (!isFormed()) return Component.translatable("message.factoryascent.pad_incomplete").withStyle(ChatFormatting.RED);
        satellite = stack.copyWithCount(1);
        launcher = player;
        changed();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.7f, 0.8f);
        }
        return null;
    }

    /** Takes the satellite back off the pad (not during a launch). */
    public ItemStack dismount() {
        if (launching() || satellite.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = satellite;
        satellite = ItemStack.EMPTY;
        launcher = null;
        changed();
        return out;
    }

    /** Adds fuel units if the whole amount fits. */
    public boolean addFuel(int units) {
        if (fuel + units > FUEL_MAX) return false;
        fuel += units;
        changed();
        return true;
    }

    /** Starts the launch sequence; null on success, else why not. */
    public @Nullable Component tryLaunch() {
        if (level == null || level.isClientSide()) return null;
        if (launching()) return Component.translatable("message.factoryascent.pad_busy").withStyle(ChatFormatting.RED);
        if (!isFormed()) return Component.translatable("message.factoryascent.pad_incomplete").withStyle(ChatFormatting.RED);
        if (satellite.isEmpty()) return Component.translatable("message.factoryascent.pad_empty").withStyle(ChatFormatting.RED);
        if (fuel < FUEL_PER_LAUNCH) {
            return Component.translatable("message.factoryascent.pad_no_fuel", fuel, FUEL_PER_LAUNCH).withStyle(ChatFormatting.RED);
        }
        for (int y = 1; y <= 4; y++) {
            if (!level.getBlockState(worldPosition.above(y)).getCollisionShape(level, worldPosition.above(y)).isEmpty()) {
                return Component.translatable("message.factoryascent.pad_blocked").withStyle(ChatFormatting.RED);
            }
        }
        fuel -= FUEL_PER_LAUNCH;
        launchTick = 0;
        launchStart = level.getGameTime();
        changed();
        level.playSound(null, worldPosition, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f, 0.8f);
        return null;
    }

    /** Tells the player who mounted the satellite (if online and near): used for redstone launches. */
    void tellLauncher(Component message) {
        if (launcher != null && level instanceof ServerLevel server) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(launcher);
            if (p != null && p.level() == level && p.blockPosition().closerThan(worldPosition, 32)) p.sendOverlayMessage(message);
        }
    }

    public Component statusLine() {
        Component payload = satellite.isEmpty() ? Component.translatable("message.factoryascent.pad_status_none")
                : satellite.getHoverName();
        String key = isFormed() ? "message.factoryascent.pad_status" : "message.factoryascent.pad_incomplete";
        return Component.translatable(key, payload, fuel, FUEL_PER_LAUNCH);
    }

    // ---------------------------------------------------------------- the launch

    public void serverTick(ServerLevel level) {
        if (launchTick < 0) return;
        if (satellite.isEmpty()) { // e.g. the data was edited; abort quietly
            launchTick = -1;
            launchStart = -1;
            changed();
            return;
        }
        int t = launchTick++;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.25, z = worldPosition.getZ() + 0.5;
        if (t < LIFTOFF) {
            if (t % 20 == 0) {
                int seconds = (LIFTOFF - t) / 20;
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
                for (ServerPlayer p : level.players()) {
                    if (p.blockPosition().closerThan(worldPosition, 32)) {
                        p.sendOverlayMessage(Component.translatable("message.factoryascent.countdown", seconds).withStyle(ChatFormatting.GOLD));
                    }
                }
            }
            // Venting smoke, thicker as liftoff nears, and a flicker of flame under the engine.
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, x, y + 0.1, z, 1 + t / 20, 0.8, 0.05, 0.8, 0.01);
            if (t > LIFTOFF / 2) level.sendParticles(ParticleTypes.FLAME, x, y + 0.05, z, 3, 0.12, 0.02, 0.12, 0.02);
            if (t % 10 == 5) level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1f, 0.6f);
        } else if (t == LIFTOFF) {
            level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.5f, 0.5f);
            level.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 3f, 0.5f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, false, x, y + 0.3, z, 60, 1.5, 0.2, 1.5, 0.06);
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, false, x, y + 0.3, z, 20, 1.2, 0.2, 1.2, 0.02);
            level.sendParticles(ParticleTypes.FLAME, true, false, x, y, z, 40, 0.6, 0.1, 0.6, 0.12);
        } else if (t < SEQUENCE) {
            // Exhaust trail at the rocket's current height (same curve the renderer draws).
            float dt = t - LIFTOFF;
            double h = y + ACCEL * dt * dt;
            level.sendParticles(ParticleTypes.FLAME, true, true, x, h, z, 6, 0.1, 0.4, 0.1, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, x, h - 0.5, z, 4, 0.2, 0.6, 0.2, 0.01);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, x, y + 0.2, z, 2, 1.2, 0.05, 1.2, 0.01);
            if (t % 8 == 0) level.playSound(null, x, h, z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 2f, 0.4f);
        } else {
            reachOrbit(level);
        }
    }

    /** The satellite joins the launcher's team's orbit over this dimension. */
    private void reachOrbit(ServerLevel level) {
        MinecraftServer server = level.getServer();
        SatelliteType type = satelliteType();
        UUID who = launcher != null ? launcher : new UUID(0, 0);
        if (type != null) {
            FactoryTeams teams = FactoryTeams.get(server);
            String team = teams.teamOf(who);
            OrbitRegistry orbit = OrbitRegistry.get(server);
            Component custom = satellite.get(DataComponents.CUSTOM_NAME);
            String name = custom != null ? custom.getString()
                    : type.shortName().getString() + "-" + (orbit.count(team, type) + 1);
            Satellite sat = new Satellite(type, level.dimension(), server.overworld().getGameTime(), name, who);
            orbit.add(team, sat);
            OrbitalText.tellTeam(server, team, Component.translatable("message.factoryascent.reached_orbit",
                    type.displayName(), name, OrbitalText.dimensionName(level.dimension())).withStyle(ChatFormatting.AQUA));
            OrbitalContent.award(server, teams.members(team), "orbital_launch");
            if (type == SatelliteType.UPLINK) OrbitalContent.award(server, teams.members(team), "orbital_uplink");
        }
        satellite = ItemStack.EMPTY;
        launcher = null;
        launchTick = -1;
        launchStart = -1;
        changed();
    }

    // ---------------------------------------------------------------- save / sync

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Drops the mounted satellite (and nothing else: fuel burns away) when the controller is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !satellite.isEmpty() && !launching()) Block.popResource(level, pos, satellite.copy());
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
        satellite = input.read("satellite", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        launcher = input.read("launcher", UUIDUtil.CODEC).orElse(null);
        fuel = input.getIntOr("fuel", 0);
        launchTick = input.getIntOr("launch_tick", -1);
        launchStart = input.getLongOr("launch_start", -1L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!satellite.isEmpty()) output.store("satellite", ItemStack.OPTIONAL_CODEC, satellite);
        if (launcher != null) output.store("launcher", UUIDUtil.CODEC, launcher);
        output.putInt("fuel", fuel);
        output.putInt("launch_tick", launchTick);
        output.putLong("launch_start", launchStart);
    }
}
