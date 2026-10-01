package net.juli2kapo.factoryascent.outpost;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.juli2kapo.factoryascent.space.Orbit;
import net.juli2kapo.factoryascent.space.SpaceContent;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Players riding an Ascent Module up: a few seconds of powered climb out of the planet's sky
 * (flames, smoke, the roar of the booster), then arrival in Earth orbit above their launch site
 * ({@link Orbit#arrive}, with its starter deck and Return Pod).
 */
public final class OutpostLaunches {
    /** Player -> ticks since lift-off. */
    private static final Map<UUID, Integer> CLIMBING = new ConcurrentHashMap<>();

    private OutpostLaunches() {}

    public static boolean launching(ServerPlayer player) {
        return CLIMBING.containsKey(player.getUUID());
    }

    /** Lift-off from {@code pad}: the player is put on top of where the module stood and starts climbing. */
    public static void launch(ServerPlayer player, BlockPos pad) {
        player.stopRiding();
        player.teleportTo(pad.getX() + 0.5, pad.getY() + 0.1, pad.getZ() + 0.5);
        CLIMBING.put(player.getUUID(), 0);
        ServerLevel level = player.level();
        level.playSound(null, pad, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, pad, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2f, 0.4f);
        level.sendParticles(ParticleTypes.EXPLOSION, pad.getX() + 0.5, pad.getY() + 0.5, pad.getZ() + 0.5, 2, 0.5, 0.2, 0.5, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pad.getX() + 0.5, pad.getY() + 0.3, pad.getZ() + 0.5, 30, 1.2, 0.2, 1.2, 0.02);
        player.sendSystemMessage(Component.translatable("message.factoryascent.ascent_liftoff").withStyle(ChatFormatting.GOLD));
    }

    static void tick(MinecraftServer server) {
        if (CLIMBING.isEmpty()) return;
        int total = OutpostConfig.ascentTicks();
        for (Iterator<Map.Entry<UUID, Integer>> it = CLIMBING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null || player.isDeadOrDying()) {
                it.remove();
                continue;
            }
            int t = e.getValue() + 1;
            e.setValue(t);
            ServerLevel level = player.level();
            if (t >= total || player.getY() > level.getMaxY() + 40) {
                it.remove();
                reachOrbit(player);
                continue;
            }
            player.setDeltaMovement(new Vec3(0, Ascent.climbSpeed(t, total), 0));
            player.hurtMarked = true;
            player.resetFallDistance();
            double x = player.getX(), y = player.getY(), z = player.getZ();
            level.sendParticles(ParticleTypes.FLAME, true, true, x, y - 0.4, z, 10, 0.18, 0.3, 0.18, 0.03);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, x, y - 1.2, z, 4, 0.25, 0.6, 0.25, 0.02);
            if (t % 8 == 1) level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2f, 0.5f);
        }
    }

    /** Into Earth orbit above the player's launch site (or the world spawn); straight home if there is no orbit. */
    static void reachOrbit(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        GlobalPos back = player.hasData(SpaceContent.RETURN_POINT.get()) ? player.getData(SpaceContent.RETURN_POINT.get()) : null;
        ServerLevel home = back == null ? null : server.getLevel(back.dimension());
        BlockPos site;
        if (home == null || SpaceRules.isAirless(home)) {
            home = server.overworld();
            site = home.getRespawnData().pos();
        } else {
            site = back.pos();
        }
        if (!Orbit.arrive(player, home.dimension(), site)) {
            player.setData(SpaceContent.RETURN_POINT.get(), GlobalPos.of(home.dimension(), site));
            Orbit.reenter(player);
        }
        SpaceContent.award(player, "outpost_ascent");
    }

    static void forget(UUID player) {
        CLIMBING.remove(player);
    }

    static void clear() {
        CLIMBING.clear();
    }
}
