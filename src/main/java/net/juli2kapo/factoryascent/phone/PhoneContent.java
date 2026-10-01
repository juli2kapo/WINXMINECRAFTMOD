package net.juli2kapo.factoryascent.phone;

import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.phone.apps.DysonApp;
import net.juli2kapo.factoryascent.phone.apps.MachinesApp;
import net.juli2kapo.factoryascent.phone.apps.MapApp;
import net.juli2kapo.factoryascent.phone.apps.PowerApp;
import net.juli2kapo.factoryascent.phone.apps.RecallApp;
import net.juli2kapo.factoryascent.phone.apps.SettingsApp;
import net.juli2kapo.factoryascent.phone.apps.StorageApp;
import net.juli2kapo.factoryascent.phone.apps.TeamApp;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.storagenet.StorageTerminalBlockEntity;
import net.juli2kapo.factoryascent.storagenet.TerminalMenu;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;

/**
 * The Factory Phone (Automation age): the item, its memory component, config, packets, the
 * built-in apps, and the server events behind sessions, alerts and the phone-started recall.
 */
public final class PhoneContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);
    private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "tools"));

    public static final DeferredItem<FactoryPhoneItem> FACTORY_PHONE = ITEMS.registerItem("factory_phone",
            FactoryPhoneItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Linked devices and settings of a phone. */
    public static final Supplier<DataComponentType<PhoneMemory>> MEMORY = COMPONENTS.registerComponentType("phone",
            b -> b.persistent(PhoneMemory.CODEC).networkSynchronized(PhoneMemory.STREAM_CODEC));

    private PhoneContent() {}

    /** The built-in apps, in home-screen order. */
    private static void registerApps() {
        PhoneApps.register(new MapApp());
        PhoneApps.register(new StorageApp());
        PhoneApps.register(new TeamApp());
        PhoneApps.register(new MachinesApp());
        PhoneApps.register(new PowerApp());
        PhoneApps.register(new RecallApp());
        PhoneApps.register(new DysonApp());
        PhoneApps.register(new SettingsApp());
    }

    static void register(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, PhoneConfig.SPEC, FactoryAscent.MOD_ID + "-phone-server.toml");
        registerApps();
        modBus.addListener(PhonePayloads::register);
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.ENERGY.get(), FactoryPhoneItem.CAPACITY,
                        FactoryPhoneItem.CAPACITY / 50, 0), FACTORY_PHONE.get()));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (!e.getTabKey().equals(TOOLS_TAB)) return;
            e.accept(FACTORY_PHONE.get());
            ItemStack charged = new ItemStack(FACTORY_PHONE.get());
            charged.set(ModComponents.ENERGY.get(), FactoryPhoneItem.CAPACITY);
            e.accept(charged);
        });
        // Remote storage sessions: chain the phone's rule onto the storage network's hook (after
        // every mod constructor ran, so the Wireless Terminal's rule is already in place).
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            var previous = TerminalMenu.remoteAccess;
            java.util.function.BiPredicate<Player, StorageTerminalBlockEntity> phone = StorageApp::allowsRemote;
            TerminalMenu.remoteAccess = previous.or(phone);
        }));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            PhoneService.tick(e.getServer());
            RecallApp.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post e) -> RecallApp.onDamaged(e));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            PhoneService.clear();
            RecallApp.clear();
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            PhoneService.forget(e.getEntity().getUUID());
            RecallApp.forget(e.getEntity().getUUID());
            TeamApp.forget(e.getEntity().getUUID());
        });
    }

    /** Grants a code-triggered advancement (criterion "done") once. */
    public static void award(ServerPlayer player, String key) {
        var server = player.level().getServer();
        if (server == null) return;
        AdvancementHolder holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, key));
        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            player.getAdvancements().award(holder, "done");
        }
    }
}
