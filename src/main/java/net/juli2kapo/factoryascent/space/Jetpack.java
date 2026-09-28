package net.juli2kapo.factoryascent.space;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Jetpacks, worn in the chest slot: hold jump to thrust (FE per tick), or switch on hover mode
 * (sneak-use the jetpack, or the hover key) to sink slowly instead of falling.
 *
 * <p>Movement is predicted by the client (players move themselves), but the server decides:
 * the client only reports whether jump is held ({@link SpacePayloads.JetpackInput}); the server
 * burns the energy, spawns the exhaust, clears fall distance and keeps the anti-flying check quiet
 * only while the jetpack really is firing. The client stops as soon as the synced charge runs out.
 */
public final class Jetpack {
    private Jetpack() {}

    /**
     * @param capacity   FE stored
     * @param thrustCost FE per tick of thrust
     * @param hoverCost  FE per tick of hover
     * @param accel      upward acceleration per tick while thrusting
     * @param maxUp      top climbing speed (blocks per tick)
     * @param hoverFall  sink speed in hover mode (negative)
     * @param airAccel   extra horizontal push towards where the player is moving, per tick
     */
    public enum Tier {
        ELECTRIC(60_000, 40, 16, 0.14, 0.5, -0.16, 0.012),
        ADVANCED(200_000, 60, 20, 0.18, 0.8, -0.035, 0.03);

        public final int capacity, thrustCost, hoverCost;
        public final double accel, maxUp, hoverFall, airAccel;

        Tier(int capacity, int thrustCost, int hoverCost, double accel, double maxUp, double hoverFall, double airAccel) {
            this.capacity = capacity;
            this.thrustCost = thrustCost;
            this.hoverCost = hoverCost;
            this.accel = accel;
            this.maxUp = maxUp;
            this.hoverFall = hoverFall;
            this.airAccel = airAccel;
        }

        public int thrustCost() {
            return (int) Math.ceil(thrustCost * SpaceConfig.get(SpaceConfig.JETPACK_ENERGY));
        }

        public int hoverCost() {
            return (int) Math.ceil(hoverCost * SpaceConfig.get(SpaceConfig.JETPACK_ENERGY));
        }
    }

    /** Something worn in the chest slot that flies. */
    public interface Gear {
        Tier jetTier();
    }

    public enum Mode { OFF, THRUST, HOVER }

    /** Whether each player holds jump (server side, from {@link SpacePayloads.JetpackInput}). */
    private static final Map<UUID, Boolean> INPUT = new ConcurrentHashMap<>();

    public static ItemStack worn(LivingEntity entity) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        return chest.getItem() instanceof Gear ? chest : ItemStack.EMPTY;
    }

    public static Tier tier(ItemStack stack) {
        return stack.getItem() instanceof Gear gear ? gear.jetTier() : Tier.ELECTRIC;
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    public static boolean hover(ItemStack stack) {
        return stack.getOrDefault(SpaceContent.JETPACK_HOVER.get(), false);
    }

    public static void setInput(Player player, boolean thrust) {
        if (thrust) {
            INPUT.put(player.getUUID(), true);
        } else {
            INPUT.remove(player.getUUID());
        }
    }

    static void forget(UUID player) {
        INPUT.remove(player);
    }

    /** What the jetpack does this tick, given whether jump is held. */
    public static Mode mode(Player player, ItemStack pack, boolean thrustKey) {
        if (pack.isEmpty() || player.isSpectator() || player.getAbilities().flying || player.isPassenger()
                || player.isFallFlying() || player.isDeadOrDying()) {
            return Mode.OFF;
        }
        Tier tier = tier(pack);
        int energy = energy(pack);
        if (thrustKey && energy >= tier.thrustCost()) return Mode.THRUST;
        if (hover(pack) && !player.onGround() && !player.isInWater() && energy >= tier.hoverCost()) return Mode.HOVER;
        return Mode.OFF;
    }

    /**
     * The jetpack's push, on either side (the client predicts it for its own player; the server's
     * copy only matters for players the server moves itself, such as test players).
     *
     * @param moveX, moveZ the horizontal direction the player is steering in (zero if unknown)
     */
    public static void push(Player player, Tier tier, Mode mode, double moveX, double moveZ) {
        if (mode == Mode.OFF) return;
        Vec3 v = player.getDeltaMovement();
        double vy = v.y;
        if (mode == Mode.THRUST) {
            vy = Math.min(Math.max(vy, -0.2) + tier.accel, Math.max(vy, tier.maxUp));
        } else {
            double sink = player.isShiftKeyDown() ? -0.35 : tier.hoverFall;
            vy = Math.max(vy, sink);
        }
        double len = Math.sqrt(moveX * moveX + moveZ * moveZ);
        double ax = len > 1e-4 ? moveX / len * tier.airAccel : 0, az = len > 1e-4 ? moveZ / len * tier.airAccel : 0;
        player.setDeltaMovement(v.x + ax, vy, v.z + az);
    }

    /** Server tick of a player's jetpack: energy, exhaust, fall distance and the flying check. */
    public static Mode serverTick(ServerPlayer player) {
        ItemStack pack = worn(player);
        if (pack.isEmpty()) return Mode.OFF;
        boolean key = INPUT.getOrDefault(player.getUUID(), false);
        Mode mode = mode(player, pack, key);
        if (mode == Mode.OFF) return mode;
        Tier tier = tier(pack);
        pack.set(ModComponents.ENERGY.get(), Math.max(0, energy(pack) - (mode == Mode.THRUST ? tier.thrustCost() : tier.hoverCost())));
        push(player, tier, mode, 0, 0);
        player.resetFallDistance();
        if (player.connection != null) player.connection.resetFlyingTicks();
        exhaust(player, mode);
        if (mode == Mode.THRUST && !player.onGround()) SpaceContent.award(player, "jetpack_flight");
        return mode;
    }

    /** Flames and smoke from both nozzles (seen by everyone), and the roar. */
    private static void exhaust(ServerPlayer player, Mode mode) {
        ServerLevel level = player.level();
        float yaw = player.yBodyRot * ((float) Math.PI / 180f);
        double bx = Math.sin(yaw) * 0.32, bz = -Math.cos(yaw) * 0.32; // behind the back
        double sx = Math.cos(yaw) * 0.14, sz = Math.sin(yaw) * 0.14;  // left/right nozzle
        double y = player.getY() + (player.isShiftKeyDown() ? 0.45 : 0.62);
        boolean thrust = mode == Mode.THRUST;
        for (int side = -1; side <= 1; side += 2) {
            double x = player.getX() + bx + side * sx, z = player.getZ() + bz + side * sz;
            level.sendParticles(ParticleTypes.FLAME, x, y, z, thrust ? 3 : 1, 0.03, 0.05, 0.03, 0.01);
            if (thrust || player.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, x, y - 0.25, z, thrust ? 2 : 1, 0.05, 0.08, 0.05, 0.01);
            }
        }
        if (player.tickCount % (thrust ? 4 : 10) == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                    thrust ? 0.25f : 0.1f, thrust ? 1.8f : 2.0f);
        }
    }

    /** Flips hover mode on the worn jetpack (the hover key). */
    public static void toggleHover(ServerPlayer player) {
        ItemStack pack = worn(player);
        if (pack.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.jetpack_none").withStyle(ChatFormatting.GRAY));
            return;
        }
        toggleHover(player, pack);
    }

    static void toggleHover(Player player, ItemStack pack) {
        boolean on = !hover(pack);
        pack.set(SpaceContent.JETPACK_HOVER.get(), on);
        player.sendOverlayMessage(Component.translatable(on ? "message.factoryascent.jetpack_hover_on"
                : "message.factoryascent.jetpack_hover_off").withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }
}
