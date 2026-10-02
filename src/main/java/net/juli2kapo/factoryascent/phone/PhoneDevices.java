package net.juli2kapo.factoryascent.phone;

import net.juli2kapo.factoryascent.energy.EnergyNetwork;
import net.juli2kapo.factoryascent.energy.EnergyNetworkManager;
import net.juli2kapo.factoryascent.energy.PowerCableBlock;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.nuclear.ReactorControllerBlock;
import net.juli2kapo.factoryascent.nuclear.ReactorControllerBlockEntity;
import net.juli2kapo.factoryascent.orbital.OrbitalSignal;
import net.juli2kapo.factoryascent.power.PowerBlockEntity;
import net.juli2kapo.factoryascent.power.PowerConfig;
import net.juli2kapo.factoryascent.storagenet.StorageControllerBlockEntity;
import net.juli2kapo.factoryascent.storagenet.StorageNet;
import net.juli2kapo.factoryascent.storagenet.StorageTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Reads linked devices for the phone, read-only: what each kind of block can be linked as, whether
 * the player can reach it from where they are, and its status as a small tag for the client.
 *
 * <p>Reach: the device must be in the player's dimension and its chunk loaded (an Ender Anchor
 * keeps far bases loaded). Within {@link PhoneConfig#LOCAL_RANGE} blocks the phone talks to it by
 * short-range radio; farther away it needs the team's Uplink Satellite overhead.
 */
public final class PhoneDevices {
    /** Status levels shown as colours: fine, worth a look, needs you, can't tell. */
    public static final int OK = 0, WARN = 1, BAD = 2, OFFLINE = 3;

    private PhoneDevices() {}

    /**
     * What a block can be linked as: a {@link PhoneMemory} kind, or -1. Machines, generators and
     * reactors, and also any other Factory Ascent device that holds energy or fluid or reports a
     * status (tanks, Dyson Receivers and Monitors, Mass Drivers, the orbital consoles, cross-dimension
     * links, anchors…) are watched as machines; Ender Beacons go to the Recall app.
     */
    public static int linkKind(@Nullable BlockEntity be, BlockState state) {
        if (be instanceof StorageTerminalBlockEntity || be instanceof StorageControllerBlockEntity) return PhoneMemory.STORAGE;
        if (state.getBlock() instanceof PowerCableBlock) return PhoneMemory.POWER;
        if (be instanceof net.juli2kapo.factoryascent.ender.EnderBeaconBlockEntity) return PhoneMemory.BEACON;
        if (be instanceof AbstractMachineBlockEntity || be instanceof PowerBlockEntity || be instanceof ReactorControllerBlockEntity) {
            return PhoneMemory.MACHINE;
        }
        if (be != null && isDevice(be)) return PhoneMemory.MACHINE;
        return -1;
    }

    /** Status-reporting blocks that are neither machines nor generators. */
    private static boolean namedDevice(BlockEntity be) {
        return be instanceof net.juli2kapo.factoryascent.dyson.DysonReceiverBlockEntity
                || be instanceof net.juli2kapo.factoryascent.dyson.DysonMonitorBlockEntity
                || be instanceof net.juli2kapo.factoryascent.dyson.MassDriverBlockEntity
                || be instanceof net.juli2kapo.factoryascent.orbital.GroundStationBlockEntity
                || be instanceof net.juli2kapo.factoryascent.orbital.OrbitalRadarBlockEntity
                || be instanceof net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity
                || be instanceof net.juli2kapo.factoryascent.xdim.LinkBlockEntity
                || be instanceof net.juli2kapo.factoryascent.ender.EnderAnchorBlockEntity;
    }

    /** A Factory Ascent block entity (not a pipe, cable or storage-network part) worth watching. */
    private static boolean isDevice(BlockEntity be) {
        if (be instanceof net.juli2kapo.factoryascent.pipe.ItemPipeBlockEntity
                || be instanceof net.juli2kapo.factoryascent.storagenet.StorageNodeBlockEntity
                || be.getClass().getSimpleName().contains("Pipe")) return false;
        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        if (key == null || !key.getNamespace().equals(net.juli2kapo.factoryascent.FactoryAscent.MOD_ID)) return false;
        if (namedDevice(be)) return true;
        if (be.getLevel() == null) return false;
        return be.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, be.getBlockPos(),
                be.getBlockState(), be, null) != null
                || be.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, be.getBlockPos(),
                be.getBlockState(), be, null) != null;
    }

    /** Status of a device that isn't a machine, generator or reactor: energy, fluid and its own status line. */
    private static void device(ServerLevel level, BlockEntity be, CompoundTag tag) {
        tag.putString("status", "gui.factoryascent.phone.device.online");
        tag.putInt("level", OK);
        tag.putBoolean("benign", true);
        var energy = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, be.getBlockPos(),
                be.getBlockState(), be, null);
        if (energy != null && energy.getCapacityAsLong() > 0) {
            tag.putInt("energy", (int) Math.min(Integer.MAX_VALUE, energy.getAmountAsLong()));
            tag.putInt("capacity", (int) Math.min(Integer.MAX_VALUE, energy.getCapacityAsLong()));
        }
        var fluid = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, be.getBlockPos(),
                be.getBlockState(), be, null);
        if (fluid != null && energy == null) {
            long amount = 0, capacity = 0;
            for (int i = 0; i < fluid.size(); i++) {
                amount += fluid.getAmountAsLong(i);
                capacity += fluid.getCapacityAsLong(i, fluid.getResource(i));
            }
            tag.putString("status", "gui.factoryascent.phone.device.fluid");
            tag.putInt("arg", (int) Math.min(Integer.MAX_VALUE, amount));
            if (capacity > 0) {
                tag.putInt("energy", (int) Math.min(Integer.MAX_VALUE, amount));
                tag.putInt("capacity", (int) Math.min(Integer.MAX_VALUE, capacity));
                tag.putBoolean("fluid", true);
            }
        }
        Component line = null;
        if (be instanceof net.juli2kapo.factoryascent.dyson.DysonReceiverBlockEntity r) {
            line = r.statusLine(level);
            tag.putBoolean("working", r.active());
            tag.putInt("rate", r.lastOut());
            tag.putInt("level", r.active() ? OK : r.isFormed() ? WARN : BAD);
            tag.putBoolean("benign", r.isFormed());
            tag.putBoolean("dyson", true);
            tag.putInt("dysonIn", r.lastIn());
            tag.putInt("dysonOut", r.lastOut());
            tag.putBoolean("formed", r.isFormed());
        }
        if (line != null) {
            ComponentSerialization.CODEC.encodeStart(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), line)
                    .result().ifPresent(t -> tag.put("statusc", t));
        }
    }

    /** A storage controller links its network's first terminal; null if it has none. */
    public static @Nullable BlockPos terminalFor(ServerLevel level, BlockEntity be) {
        if (be instanceof StorageTerminalBlockEntity) return be.getBlockPos();
        if (be instanceof StorageControllerBlockEntity controller) {
            StorageNet net = controller.network();
            if (net == null) return null;
            for (BlockPos member : net.members()) {
                if (level.getBlockEntity(member) instanceof StorageTerminalBlockEntity) return member;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- reach

    /** Why the device can't be reached right now (a translation key), or null if it can. */
    public static @Nullable String unreachable(ServerPlayer player, GlobalPos pos, boolean signal) {
        if (!pos.dimension().equals(player.level().dimension())) return "gui.factoryascent.phone.other_dimension";
        int range = PhoneConfig.LOCAL_RANGE.get();
        boolean near = player.blockPosition().distSqr(pos.pos()) <= (double) range * range;
        if (!near && !signal) return "gui.factoryascent.phone.out_of_range";
        BlockPos p = pos.pos();
        if (player.level().getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) return "gui.factoryascent.phone.unloaded";
        return null;
    }

    public static boolean signal(ServerPlayer player) {
        return OrbitalSignal.hasCoverage(player);
    }

    // ---------------------------------------------------------------- machines

    /**
     * A machine's status as the Machines app shows it. Keys: {@code item} (icon), {@code name}
     * (block description id), {@code x/y/z}, {@code level} ({@link #OK}…), {@code status}
     * (translation key) with optional {@code arg}, {@code working}, {@code benign} (stopped for a
     * harmless reason: full buffer, night…), and when known {@code energy/capacity},
     * {@code progress} (permille), {@code rate} (FE/t), {@code temp/alarm/meltdown} (reactors).
     */
    public static CompoundTag machine(ServerPlayer player, PhoneMemory.Link link, boolean signal) {
        CompoundTag tag = base(link);
        String why = unreachable(player, link.pos(), signal);
        if (why != null) return offline(tag, why);
        ServerLevel level = player.level();
        BlockPos pos = link.pos().pos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || linkKind(be, be.getBlockState()) != PhoneMemory.MACHINE) {
            return gone(tag);
        }
        BlockState state = be.getBlockState();
        tag.putString("item", BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString());
        tag.putString("name", state.getBlock().getDescriptionId());
        if (be instanceof AbstractMachineBlockEntity m) {
            int st = m.status();
            String key = switch (st) {
                case AbstractMachineBlockEntity.STATUS_WORKING -> "working";
                case AbstractMachineBlockEntity.STATUS_NO_POWER -> "no_power";
                case AbstractMachineBlockEntity.STATUS_OUTPUT_FULL -> "output_full";
                case AbstractMachineBlockEntity.STATUS_TIER_TOO_LOW -> "tier_too_low";
                case AbstractMachineBlockEntity.STATUS_NO_FUEL -> "no_fuel";
                case AbstractMachineBlockEntity.STATUS_FULL -> "full";
                case AbstractMachineBlockEntity.STATUS_INCOMPLETE -> "incomplete";
                case AbstractMachineBlockEntity.STATUS_NEEDS_CRANK -> "needs_crank";
                case AbstractMachineBlockEntity.STATUS_LOADING -> "loading";
                default -> "idle";
            };
            tag.putString("status", st == AbstractMachineBlockEntity.STATUS_IDLE ? "gui.factoryascent.phone.idle_no_input"
                    : "status.factoryascent." + key);
            if (st == AbstractMachineBlockEntity.STATUS_INCOMPLETE) tag.putInt("arg", m.extraB());
            tag.putInt("level", switch (st) {
                case AbstractMachineBlockEntity.STATUS_WORKING, AbstractMachineBlockEntity.STATUS_FULL,
                     AbstractMachineBlockEntity.STATUS_LOADING -> OK;
                case AbstractMachineBlockEntity.STATUS_IDLE -> WARN;
                default -> BAD;
            });
            tag.putBoolean("working", st == AbstractMachineBlockEntity.STATUS_WORKING);
            tag.putBoolean("benign", st == AbstractMachineBlockEntity.STATUS_FULL || st == AbstractMachineBlockEntity.STATUS_LOADING);
            tag.putInt("energy", m.energy().energy());
            tag.putInt("capacity", m.energy().capacity());
            tag.putInt("progress", m.progressPermille());
            tag.putInt("rate", m.lastEnergyRate());
        } else if (be instanceof PowerBlockEntity p) {
            int st = p.status();
            String key = st >= 0 && st < PowerBlockEntity.STATUS_KEYS.length ? PowerBlockEntity.STATUS_KEYS[st] : "idle";
            tag.putString("status", "status.factoryascent.power." + key);
            boolean running = st == PowerBlockEntity.ST_RUNNING || st == PowerBlockEntity.ST_DIGESTING;
            boolean benign = st == PowerBlockEntity.ST_FULL || st == PowerBlockEntity.ST_NIGHT || st == PowerBlockEntity.ST_HEATING;
            tag.putInt("level", running || benign ? OK : st == PowerBlockEntity.ST_IDLE ? WARN : BAD);
            tag.putBoolean("working", running);
            tag.putBoolean("benign", benign);
            tag.putInt("energy", p.energy().energy());
            tag.putInt("capacity", p.energy().capacity());
            tag.putInt("rate", p.lastRate());
        } else if (be instanceof ReactorControllerBlockEntity r) {
            float temp = r.temperature();
            int alarm = PowerConfig.get(PowerConfig.ALARM_TEMPERATURE);
            int meltdown = PowerConfig.get(PowerConfig.MELTDOWN_TEMPERATURE);
            boolean active = state.hasProperty(ReactorControllerBlock.ACTIVE) && state.getValue(ReactorControllerBlock.ACTIVE);
            String key = r.meltedDown() ? "melted" : r.scrammed() ? "scram" : active ? "running" : "idle";
            tag.putString("status", "gui.factoryascent.phone.reactor." + key);
            tag.putInt("level", r.meltedDown() || temp >= alarm ? BAD : temp >= alarm * 0.75f || r.scrammed() ? WARN : OK);
            tag.putBoolean("working", active);
            tag.putBoolean("benign", r.scrammed());
            tag.putFloat("temp", temp);
            tag.putInt("alarm", alarm);
            tag.putInt("meltdown", meltdown);
            tag.putInt("energy", r.energy().energy());
            tag.putInt("capacity", r.energy().capacity());
            tag.putInt("rate", r.rate());
        } else {
            device(level, be, tag);
        }
        return tag;
    }

    private static CompoundTag base(PhoneMemory.Link link) {
        CompoundTag tag = new CompoundTag();
        BlockPos p = link.pos().pos();
        tag.putInt("x", p.getX());
        tag.putInt("y", p.getY());
        tag.putInt("z", p.getZ());
        tag.putString("dim", link.pos().dimension().identifier().toString());
        tag.putString("name", link.block());
        return tag;
    }

    private static CompoundTag gone(CompoundTag tag) {
        offline(tag, "gui.factoryascent.phone.gone");
        tag.putBoolean("gone", true);
        return tag;
    }

    private static CompoundTag offline(CompoundTag tag, String why) {
        tag.putInt("level", OFFLINE);
        tag.putString("status", why);
        tag.putBoolean("offline", true);
        return tag;
    }

    // ---------------------------------------------------------------- energy networks

    /**
     * An energy network's stats (Power app): {@code energy/capacity}, {@code in/out} (FE/t,
     * smoothed), {@code rate} (max FE/t), {@code cables}, {@code endpoints}; or offline.
     */
    public static CompoundTag power(ServerPlayer player, PhoneMemory.Link link, boolean signal) {
        CompoundTag tag = base(link);
        String why = unreachable(player, link.pos(), signal);
        if (why != null) return offline(tag, why);
        ServerLevel level = player.level();
        BlockPos pos = link.pos().pos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PowerCableBlock)) {
            return gone(tag);
        }
        tag.putString("item", BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString());
        tag.putString("name", state.getBlock().getDescriptionId());
        EnergyNetwork net = EnergyNetworkManager.get(level).networkAt(pos);
        if (net == null) return offline(tag, "gui.factoryascent.phone.unloaded");
        tag.putLong("energy", net.energy());
        tag.putLong("capacity", net.capacity());
        tag.putLong("in", net.averageIn());
        tag.putLong("out", net.averageOut());
        tag.putLong("rate", net.rate());
        tag.putInt("cables", net.cables().size());
        tag.putInt("level", net.averageIn() <= 0 && net.energy() <= 0 ? WARN : OK);
        return tag;
    }
}
