package net.juli2kapo.factoryascent.space;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server side of being in space, every tick: gravity, the jetpack, re-entry and breathing.
 * Without air a player freezes over (the powder-snow frost creeps in) and takes
 * {@link SpaceConfig#VACUUM_DAMAGE} every second; a suit on its own air loses a second of it
 * every second.
 */
public final class SpaceEvents {
    /** Damage type of suffocating in vacuum ({@code data/factoryascent/damage_type/vacuum.json}). */
    public static final ResourceKey<DamageType> VACUUM =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "vacuum"));

    private SpaceEvents() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tickPlayer(player);
        });
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Pre e) -> {
            if (!(e.getEntity() instanceof Player) && e.getEntity() instanceof LivingEntity living
                    && !living.level().isClientSide() && living.tickCount % 10 == 0
                    && (Orbit.gravityFor(living.level().dimension()) != 1.0 || living.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY) != living.getAttributeBaseValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY))) {
                Orbit.applyGravity(living);
            }
        });
        // Blocks built in orbit stay where they are put: sand and gravel don't fall in zero-g.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.world.entity.item.FallingBlockEntity falling && !e.getLevel().isClientSide()
                    && e.getLevel().dimension() == SpaceRules.ORBIT && !e.loadedFromDisk()) {
                net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(falling.position());
                if (e.getLevel().getBlockState(pos).isAir()) {
                    e.setCanceled(true);
                    e.getLevel().setBlock(pos, falling.getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                            | net.minecraft.world.level.block.Block.UPDATE_SKIP_ON_PLACE);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(SpaceEvents::onMount);
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && e.getSource().is(VACUUM)) {
                SpaceContent.award(player, "space_houston");
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            Jetpack.forget(e.getEntity().getUUID());
            Orbit.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> SpaceRules.clearSealers());
    }

    static void tickPlayer(ServerPlayer player) {
        Orbit.applyGravity(player);
        Jetpack.serverTick(player);
        Orbit.tickReentry(player);
        Orbit.fallOutOfOrbit(player);
        boolean airless = SpaceRules.isAirless(player.level());
        SpaceRules.Breath breath = tickBreathing(player, airless);
        net.juli2kapo.factoryascent.space.planet.PlanetHazards.tickPlayer(player, breath);
        if (airless && player.tickCount % 10 == 0 && player.connection != null
                && player.connection.hasChannel(SpacePayloads.Breathing.TYPE)) {
            PacketDistributor.sendToPlayer(player, new SpacePayloads.Breathing(breath.ordinal()));
        }
    }

    /**
     * One tick of breathing: drains the suit (a second of air per second) or hurts a player with no
     * air. Creative and spectator players are left alone. Returns where the air came from.
     */
    public static SpaceRules.Breath tickBreathing(LivingEntity entity, boolean airless) {
        SpaceRules.Breath breath = SpaceRules.breathing(entity, airless);
        if (breath == SpaceRules.Breath.AIR) return breath;
        if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) return breath;
        boolean second = entity.tickCount % 20 == 0;
        if (breath == SpaceRules.Breath.SUIT) {
            if (second) {
                ItemStack tank = SpaceRules.suitTank(entity);
                net.juli2kapo.factoryascent.space.planet.Planet planet = net.juli2kapo.factoryascent.space.planet.Planet.of(entity.level());
                int drain = planet == null ? 20 : (int) Math.round(20 * planet.suitDrain());
                int left = SuitItems.oxygen(tank) - drain;
                SuitItems.setOxygen(tank, left);
                if (entity instanceof ServerPlayer sp && left > 0 && left <= 60 * 20 && left % (15 * 20) < drain) {
                    sp.sendOverlayMessage(Component.translatable("message.factoryascent.oxygen_low", left / 20).withStyle(ChatFormatting.GOLD));
                }
            }
        } else if (breath == SpaceRules.Breath.NONE) {
            // Frost creeps over the screen, stopping just short of vanilla freezing (the vacuum does the damage).
            entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze() - 1, entity.getTicksFrozen() + 6));
            if (second && entity.level() instanceof ServerLevel level) {
                entity.hurtServer(level, vacuum(level), (float) SpaceConfig.get(SpaceConfig.VACUUM_DAMAGE));
                if (entity instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(Component.translatable("message.factoryascent.vacuum").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            }
        }
        return breath;
    }

    public static DamageSource vacuum(Level level) {
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(VACUUM)
                .map(DamageSource::new).orElseGet(() -> level.damageSources().freeze());
    }

    /** Astronauts can climb out of the capsule before liftoff, not after. */
    private static void onMount(EntityMountEvent e) {
        if (!e.isDismounting() || !(e.getEntityBeingMounted() instanceof RocketSeatEntity seat)) return;
        Entity rider = e.getEntityMounting();
        if (seat.releasing() || !rider.isAlive() || rider.isRemoved()) return;
        if (rider instanceof ServerPlayer sp && sp.hasDisconnected()) return;
        if (seat.strappedIn()) e.setCanceled(true);
    }
}
