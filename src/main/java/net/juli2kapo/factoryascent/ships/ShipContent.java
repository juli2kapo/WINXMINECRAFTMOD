package net.juli2kapo.factoryascent.ships;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * Ships: the Bronze Cog (Bronze age, wind), the Motor Ship (Electric age, FE / fuel) and the
 * Orbital Shuttle (Orbital age, Rocket Fuel, reaches {@code factoryascent:orbit}). Registers the
 * entities, items, the ship-fuel component, the helm/cockpit menu, payloads, capabilities (hold as
 * an item handler, the motor ship's battery as an energy handler), the ships' own config file, and
 * the events: no sneaking out of a shuttle in flight, and a sealed cabin for its passengers.
 */
public final class ShipContent {
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BronzeCog>> BRONZE_COG = ENTITIES.registerEntityType(
            "bronze_cog", BronzeCog::new, MobCategory.MISC,
            b -> b.sized(3.0f, 0.6f).clientTrackingRange(10).updateInterval(1).setShouldReceiveVelocityUpdates(true));
    public static final DeferredHolder<EntityType<?>, EntityType<MotorShip>> MOTOR_SHIP = ENTITIES.registerEntityType(
            "motor_ship", MotorShip::new, MobCategory.MISC,
            b -> b.sized(3.5f, 0.8f).clientTrackingRange(10).updateInterval(1).setShouldReceiveVelocityUpdates(true));
    public static final DeferredHolder<EntityType<?>, EntityType<Shuttle>> SHUTTLE = ENTITIES.registerEntityType(
            "shuttle", Shuttle::new, MobCategory.MISC,
            b -> b.sized(3.0f, 2.0f).clientTrackingRange(16).updateInterval(1).setShouldReceiveVelocityUpdates(true).fireImmune());

    public static final DeferredItem<ShipItem> BRONZE_COG_ITEM = ITEMS.registerItem("bronze_cog",
            p -> new ShipItem(BRONZE_COG, true, p), p -> p.stacksTo(1));
    public static final DeferredItem<ShipItem> MOTOR_SHIP_ITEM = ITEMS.registerItem("motor_ship",
            p -> new ShipItem(MOTOR_SHIP, true, p), p -> p.stacksTo(1));
    public static final DeferredItem<ShipItem> SHUTTLE_ITEM = ITEMS.registerItem("shuttle",
            p -> new ShipItem(SHUTTLE, false, p), p -> p.stacksTo(1).fireResistant());

    /** Fuel (tank units) or energy (FE) carried by a ship item. The hold uses vanilla's container component. */
    public static final Supplier<DataComponentType<Integer>> SHIP_FUEL = COMPONENTS.registerComponentType(
            "ship_fuel", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final Supplier<MenuType<ShipMenu>> SHIP_MENU = MENUS.register("ship",
            () -> IMenuTypeExtension.create(ShipMenu::fromNetwork));

    private ShipContent() {}

    public static void register(IEventBus modBus, ModContainer container) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ShipConfig.SPEC, "factoryascent-ships-server.toml");
        modBus.addListener(ShipPayloads::register);
        modBus.addListener(ShipContent::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(ShipContent::onMount);
        NeoForge.EVENT_BUS.addListener(ShipContent::onDamage);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> OrbitTransfer.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> OrbitTransfer.clear());
    }

    /** Items in the order of the ages (shown in the Tools tab). */
    public static List<DeferredItem<? extends Item>> creativeItems() {
        return List.of(BRONZE_COG_ITEM, MOTOR_SHIP_ITEM, SHUTTLE_ITEM);
    }

    public static Item itemFor(EntityType<?> type) {
        if (type == MOTOR_SHIP.get()) return MOTOR_SHIP_ITEM.get();
        if (type == SHUTTLE.get()) return SHUTTLE_ITEM.get();
        return BRONZE_COG_ITEM.get();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var type : List.of(BRONZE_COG.get(), MOTOR_SHIP.get(), SHUTTLE.get())) {
            event.registerEntity(Capabilities.Item.ENTITY, type, (ship, ctx) -> VanillaContainerWrapper.of(ship));
        }
        event.registerEntity(Capabilities.Energy.ENTITY, MOTOR_SHIP.get(), (ship, side) -> ship.energyHandler());
    }

    /** Sneaking out of a shuttle only works once it has landed: in flight Shift is "descend". */
    private static void onMount(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide()) return;
        if (!(event.getEntityBeingMounted() instanceof AbstractShip ship) || ship.allowDismount || ship.isRemoved()) return;
        if (!(event.getEntityMounting() instanceof Player player) || !player.isAlive() || player.isRemoved()) return;
        if (player instanceof ServerPlayer sp && sp.hasDisconnected()) return;
        if (!player.isShiftKeyDown() || ship.canDismountBySneaking()) return;
        event.setCanceled(true);
        if (ship.tickCount % 20 == 0) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.shuttle.shift_blocked",
                    Component.keybind("key.factoryascent.ship_hatch")));
        }
    }

    /**
     * The shuttle's cabin is sealed and heat-shielded: its passengers take no heat (re-entry),
     * suffocation, drowning, freezing, fall or vacuum damage (any damage type whose id mentions
     * vacuum, oxygen, suffocation, decompression or space, so the space content's own types are
     * covered too).
     */
    private static void onDamage(LivingIncomingDamageEvent event) {
        if (!Shuttle.isSealed(event.getEntity())) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FREEZING)
                || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FLY_INTO_WALL)
                || isVacuum(source)) {
            event.setCanceled(true);
        }
    }

    private static boolean isVacuum(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> {
            String path = k.identifier().getPath();
            return path.contains("vacuum") || path.contains("oxygen") || path.contains("suffocat")
                    || path.contains("decompress") || path.contains("space") || path.contains("asphyx");
        }).orElse(false);
    }
}
