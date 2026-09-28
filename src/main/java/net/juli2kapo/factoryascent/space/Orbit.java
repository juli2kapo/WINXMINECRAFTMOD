package net.juli2kapo.factoryascent.space;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Getting to orbit and back.
 *
 * <p>Orbit ({@link SpaceRules#ORBIT}) is a void over the planet. Each launch site has its own spot
 * up there, straight above it (same x and z, at {@link #DECK_Y}); the first arrival finds a 5×5
 * steel starter deck with a Return Pod on it, later arrivals land on whatever was built there.
 * Gravity is a fraction of normal ({@link SpaceConfig#ORBIT_GRAVITY}) and falls hurt less.
 *
 * <p>Coming back ({@link #reenter}): the Return Pod drops the player back over their launch site
 * (or the world spawn) high in the sky, burning through the atmosphere, then drifting down under
 * a parachute (slow falling) until they land.
 */
public final class Orbit {
    public static final int DECK_Y = 100;
    private static final Identifier GRAVITY_ID = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbit_gravity");
    /** Players falling back from orbit: ticks since re-entry began. */
    private static final Map<UUID, Integer> REENTRY = new ConcurrentHashMap<>();

    private Orbit() {}

    public static @Nullable ServerLevel level(MinecraftServer server) {
        return server.getLevel(SpaceRules.ORBIT);
    }

    /** The spot in orbit above a launch site. */
    public static BlockPos deckCentre(BlockPos launchSite) {
        return new BlockPos(launchSite.getX(), DECK_Y, launchSite.getZ());
    }

    // ---------------------------------------------------------------- arriving

    /**
     * Puts a player who rode a rocket from {@code site} into orbit above it. Returns false (and
     * leaves the player alone) if the orbit dimension isn't loaded.
     */
    public static boolean arrive(ServerPlayer player, ResourceKey<Level> fromDimension, BlockPos site) {
        ServerLevel orbit = level(player.level().getServer());
        if (orbit == null) return false;
        BlockPos centre = deckCentre(site);
        orbit.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
        BlockPos spot = landingSpot(orbit, centre);
        player.setData(SpaceContent.RETURN_POINT.get(), GlobalPos.of(fromDimension, site));
        player.teleport(new TeleportTransition(orbit, Vec3.atBottomCenterOf(spot), Vec3.ZERO, 180f, 10f,
                TeleportTransition.DO_NOTHING));
        orbit.playSound(null, spot, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.6f);
        player.sendSystemMessage(Component.translatable("message.factoryascent.orbit_arrived").withStyle(ChatFormatting.AQUA));
        if (SpaceRules.breathing(player, true) == SpaceRules.Breath.NONE) {
            player.sendSystemMessage(Component.translatable("message.factoryascent.orbit_no_suit").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        SpaceContent.award(player, "space_orbit");
        return true;
    }

    /** Where an arrival stands: on the station built here, or on a fresh starter deck. */
    static BlockPos landingSpot(ServerLevel orbit, BlockPos centre) {
        for (int y = DECK_Y + 24; y >= DECK_Y - 24; y--) {
            BlockPos p = new BlockPos(centre.getX(), y, centre.getZ());
            if (!orbit.getBlockState(p).isAir() && orbit.getBlockState(p.above()).isAir() && orbit.getBlockState(p.above(2)).isAir()) {
                return p.above();
            }
        }
        buildDeck(orbit, centre);
        return centre.above();
    }

    /** The 5×5 steel starter deck, lit at the corners, with a Return Pod at its north edge. */
    public static void buildDeck(Level level, BlockPos centre) {
        BlockState steel = ModBlocks.SIMPLE.get("steel_block").get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                level.setBlock(centre.offset(dx, 0, dz), corner ? Blocks.SEA_LANTERN.defaultBlockState() : steel, 3);
            }
        }
        level.setBlock(centre.offset(0, 1, -2), SpaceContent.RETURN_POD.get().defaultBlockState(), 3);
    }

    // ---------------------------------------------------------------- coming back

    /** The Return Pod: back over the launch site (or the world spawn), with a fiery re-entry. */
    public static void reenter(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        GlobalPos back = player.hasData(SpaceContent.RETURN_POINT.get()) ? player.getData(SpaceContent.RETURN_POINT.get()) : null;
        ServerLevel target = back == null ? null : server.getLevel(back.dimension());
        BlockPos site;
        if (target == null || SpaceRules.isAirless(target)) {
            target = server.overworld();
            site = target.getRespawnData().pos();
        } else {
            site = back.pos();
        }
        target.getChunk(site.getX() >> 4, site.getZ() >> 4);
        int ground = target.getHeight(Heightmap.Types.MOTION_BLOCKING, site.getX(), site.getZ());
        double y = Math.min(ground + 90, target.getMaxY() - 4);
        player.stopRiding();
        player.teleport(new TeleportTransition(target, new Vec3(site.getX() + 0.5, y, site.getZ() + 0.5), Vec3.ZERO,
                player.getYRot(), 35f, TeleportTransition.DO_NOTHING));
        startReentry(player);
    }

    static void startReentry(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 120, 0, false, false, true));
        player.resetFallDistance();
        REENTRY.put(player.getUUID(), 0);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.5f);
        player.sendSystemMessage(Component.translatable("message.factoryascent.reentry").withStyle(ChatFormatting.GOLD));
        if (player.connection != null && player.connection.hasChannel(SpacePayloads.Reentry.TYPE)) {
            PacketDistributor.sendToPlayer(player, new SpacePayloads.Reentry(REENTRY_BURN));
        }
    }

    /** Ticks of burning through the atmosphere (flames, shaking screen). */
    public static final int REENTRY_BURN = 80;

    public static boolean reentering(ServerPlayer player) {
        return REENTRY.containsKey(player.getUUID());
    }

    static void tickReentry(ServerPlayer player) {
        Integer t = REENTRY.get(player.getUUID());
        if (t == null) return;
        t++;
        REENTRY.put(player.getUUID(), t);
        player.resetFallDistance();
        ServerLevel level = player.level();
        if (t < REENTRY_BURN) {
            double x = player.getX(), y = player.getY(), z = player.getZ();
            level.sendParticles(ParticleTypes.FLAME, true, true, x, y + 0.2, z, 12, 0.5, 0.4, 0.5, 0.06);
            level.sendParticles(ParticleTypes.LAVA, true, true, x, y, z, 2, 0.4, 0.2, 0.4, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, x, y + 1.5, z, 3, 0.4, 0.8, 0.4, 0.02);
            if (t % 10 == 1) level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2f, 0.6f);
        } else if (t % 4 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 2.4, player.getZ(), 1, 0.3, 0.05, 0.3, 0.0);
        }
        boolean landed = t > 5 && (player.onGround() || player.isInWater() || player.isInLava());
        if (landed || t > 20 * 150 || player.isDeadOrDying()) {
            REENTRY.remove(player.getUUID());
            player.removeEffect(MobEffects.SLOW_FALLING);
            if (landed) {
                player.sendSystemMessage(Component.translatable("message.factoryascent.reentry_landed").withStyle(ChatFormatting.GREEN));
                SpaceContent.award(player, "space_reentry");
            }
        }
    }

    static void forget(UUID player) {
        REENTRY.remove(player);
    }

    // ---------------------------------------------------------------- gravity

    /** Low gravity (and softer falls) while in orbit; normal elsewhere. */
    static void applyGravity(LivingEntity entity) {
        boolean inOrbit = entity.level().dimension() == SpaceRules.ORBIT;
        double amount = SpaceConfig.get(SpaceConfig.ORBIT_GRAVITY) - 1.0;
        modifier(entity.getAttribute(Attributes.GRAVITY), inOrbit, amount);
        modifier(entity.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), inOrbit, amount);
    }

    private static void modifier(@Nullable AttributeInstance attribute, boolean on, double amount) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(GRAVITY_ID);
        if (on) {
            if (current != null && current.amount() == amount) return;
            if (current != null) attribute.removeModifier(GRAVITY_ID);
            attribute.addTransientModifier(new AttributeModifier(GRAVITY_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (current != null) {
            attribute.removeModifier(GRAVITY_ID);
        }
    }
}
