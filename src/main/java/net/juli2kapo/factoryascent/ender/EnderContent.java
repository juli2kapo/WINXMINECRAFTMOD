package net.juli2kapo.factoryascent.ender;

import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ender tech: the Ender Anchor (a pearl-fuelled chunk loader) and the recall pair (Ender Beacon
 * at home + Recall Charm in your pocket), plus Ender Dust, the shared ingredient.
 */
public final class EnderContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, FactoryAscent.MOD_ID);
    /** Bubbles rising inside an Ender Anchor (see StasisBubbleParticle). */
    public static final Supplier<net.minecraft.core.particles.SimpleParticleType> STASIS_BUBBLE =
            PARTICLES.register("stasis_bubble", () -> new net.minecraft.core.particles.SimpleParticleType(false));
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);

    public static final DeferredBlock<EnderAnchorBlock> ENDER_ANCHOR = BLOCKS.registerBlock("ender_anchor",
            EnderAnchorBlock::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0f, 1200f).sound(SoundType.GLASS).noOcclusion().requiresCorrectToolForDrops()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                    .lightLevel(s -> s.getValue(EnderAnchorBlock.ACTIVE) ? 9 : 3)
                    .pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<EnderBeaconBlock> ENDER_BEACON = BLOCKS.registerBlock("ender_beacon",
            EnderBeaconBlock::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0f, 1200f).sound(SoundType.GLASS).noOcclusion().requiresCorrectToolForDrops()
                    .isSuffocating((st, l, p) -> false).isViewBlocking((st, l, p) -> false)
                    .lightLevel(st -> st.getValue(EnderBeaconBlock.LOADED) ? 7 : 2)
                    .pushReaction(PushReaction.BLOCK));

    public static final DeferredItem<BlockItem> ENDER_ANCHOR_ITEM = ITEMS.registerItem("ender_anchor",
            p -> new FactoryBlockItem(ENDER_ANCHOR.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> ENDER_BEACON_ITEM = ITEMS.registerItem("ender_beacon",
            p -> new FactoryBlockItem(ENDER_BEACON.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<Item> ENDER_DUST = ITEMS.registerSimpleItem("ender_dust");
    public static final DeferredItem<RecallCharmItem> RECALL_CHARM = ITEMS.registerItem("recall_charm",
            RecallCharmItem::new, p -> p.stacksTo(1));

    public static final Supplier<BlockEntityType<EnderAnchorBlockEntity>> ENDER_ANCHOR_BE = BLOCK_ENTITIES.register(
            "ender_anchor", () -> new BlockEntityType<>(EnderAnchorBlockEntity::new, ENDER_ANCHOR.get()));
    public static final Supplier<BlockEntityType<EnderBeaconBlockEntity>> ENDER_BEACON_BE = BLOCK_ENTITIES.register(
            "ender_beacon", () -> new BlockEntityType<>(EnderBeaconBlockEntity::new, ENDER_BEACON.get()));

    public static final Supplier<net.minecraft.world.inventory.MenuType<EnderAnchorMenu>> ENDER_ANCHOR_MENU = MENUS.register(
            "ender_anchor", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(EnderAnchorMenu::fromNetwork));
    public static final Supplier<net.minecraft.world.inventory.MenuType<EnderBeaconMenu>> ENDER_BEACON_MENU = MENUS.register(
            "ender_beacon", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(EnderBeaconMenu::fromNetwork));

    /** The name of the linked Ender Beacon, remembered by the charm (refreshed whenever the beacon is looked up). */
    public static final Supplier<DataComponentType<String>> LINKED_BEACON_NAME = COMPONENTS.registerComponentType(
            "linked_beacon_name", b -> b.persistent(com.mojang.serialization.Codec.string(0, EnderBeaconBlockEntity.MAX_NAME))
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.stringUtf8(EnderBeaconBlockEntity.MAX_NAME * 4)));

    /** The Ender Beacon a Recall Charm takes you to. */
    public static final Supplier<DataComponentType<GlobalPos>> LINKED_BEACON = COMPONENTS.registerComponentType(
            "linked_beacon", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /**
     * Set on a Recall Charm whose last recall found its Ender Beacon destroyed: the link is kept (so
     * the charm can say where it was, and works again if a beacon is rebuilt there) but shown broken.
     */
    public static final Supplier<DataComponentType<Boolean>> BEACON_BROKEN = COMPONENTS.registerComponentType(
            "beacon_broken", b -> b.persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL));

    /**
     * Owns every chunk an Ender Anchor keeps loaded. On world load, tickets of anchors that are no
     * longer in the ledger are dropped, so a chunk can never stay loaded forever by accident.
     */
    public static final TicketController ANCHOR_TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "ender_anchor"),
            (level, helper) -> {
                AnchorLedger ledger = AnchorLedger.get(level.getServer());
                helper.getBlockTickets().keySet().stream()
                        .filter(pos -> !ledger.contains(GlobalPos.of(level.dimension(), pos)))
                        .toList()
                        .forEach(helper::removeAllTickets);
            });

    private EnderContent() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        PARTICLES.register(modBus);
        MENUS.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RecallCharmItem::onDamaged);
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(ANCHOR_TICKETS));
    }

    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(ENDER_ANCHOR_ITEM, ENDER_BEACON_ITEM, RECALL_CHARM, ENDER_DUST);
    }
}
