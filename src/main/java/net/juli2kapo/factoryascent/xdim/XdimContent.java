package net.juli2kapo.factoryascent.xdim;

import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Interdimensional links: the Ender Link (Automation age: items and fluids, one dimension or
 * Overworld↔Nether) and the Quantum Entangler (Quantum age: items, fluids and energy between any
 * two dimensions, orbit and planets included, paid in FE). Endpoints tuned to the same channel
 * share what their sending faces take in; channels belong to a team ({@link XdimNetwork}).
 */
public final class XdimContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final ResourceKey<CreativeModeTab> LOGISTICS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "logistics"));

    private static BlockBehaviour.Properties props(MapColor color, int idle, int active) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(4.0f, 1200f).requiresCorrectToolForDrops()
                .sound(SoundType.NETHERITE_BLOCK).noOcclusion().pushReaction(PushReaction.BLOCK)
                .lightLevel(s -> s.getValue(LinkBlock.ACTIVE) ? active : idle);
    }

    public static final DeferredBlock<LinkBlock> ENDER_LINK = BLOCKS.registerBlock("ender_link",
            p -> new LinkBlock(LinkTier.ENDER, p), () -> props(MapColor.COLOR_PURPLE, 4, 10));
    public static final DeferredBlock<LinkBlock> QUANTUM_ENTANGLER = BLOCKS.registerBlock("quantum_entangler",
            p -> new LinkBlock(LinkTier.QUANTUM, p), () -> props(MapColor.COLOR_CYAN, 6, 14));

    public static final DeferredItem<BlockItem> ENDER_LINK_ITEM = ITEMS.registerItem("ender_link",
            p -> new FactoryBlockItem(ENDER_LINK.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> QUANTUM_ENTANGLER_ITEM = ITEMS.registerItem("quantum_entangler",
            p -> new FactoryBlockItem(QUANTUM_ENTANGLER.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.EPIC));

    public static final Supplier<BlockEntityType<LinkBlockEntity>> LINK_BE = BLOCK_ENTITIES.register("xdim_link",
            () -> new BlockEntityType<>(LinkBlockEntity::new, ENDER_LINK.get(), QUANTUM_ENTANGLER.get()));

    /**
     * Owns the chunk every tuned endpoint keeps loaded. On world load, tickets of endpoints the
     * network no longer lists as anchored are dropped.
     */
    public static final TicketController TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "xdim_link"),
            (level, helper) -> {
                XdimNetwork net = XdimNetwork.get(level.getServer());
                helper.getBlockTickets().keySet().stream()
                        .filter(pos -> !net.isAnchored(GlobalPos.of(level.dimension(), pos)))
                        .toList()
                        .forEach(helper::removeAllTickets);
            });

    private XdimContent() {}

    static void register(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, XdimConfig.SPEC, FactoryAscent.MOD_ID + "-xdim-server.toml");
        modBus.addListener(XdimPayloads::register);
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(TICKETS));
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.Item.BLOCK, LINK_BE.get(), LinkBlockEntity::itemHandler);
            e.registerBlockEntity(Capabilities.Fluid.BLOCK, LINK_BE.get(), LinkBlockEntity::fluidHandler);
            e.registerBlockEntity(Capabilities.Energy.BLOCK, LINK_BE.get(), (be, side) ->
                    be.tier() == LinkTier.QUANTUM ? be.energyHandler(side) : null);
        });
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(LOGISTICS_TAB)) creativeItems().forEach(i -> e.accept(i.get()));
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> XdimService.tick(e.getServer()));
    }

    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(ENDER_LINK_ITEM, QUANTUM_ENTANGLER_ITEM);
    }
}
