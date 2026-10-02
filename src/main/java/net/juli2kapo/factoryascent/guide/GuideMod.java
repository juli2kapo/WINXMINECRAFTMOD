package net.juli2kapo.factoryascent.guide;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Factory Ascent Manual's own entry point (a further {@code @Mod} class, like the ships' and
 * the Dyson Cube's): the book item, its packets, the first-join gift and the crafting-recipe sync
 * the book's recipe pages read.
 */
@Mod(FactoryAscent.MOD_ID)
public final class GuideMod {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "tools"));
    private static final String GIVEN_TAG = "factoryascent_manual_given";

    public static final DeferredItem<GuideBookItem> MANUAL = ITEMS.registerItem("factory_manual",
            GuideBookItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    public GuideMod(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, GuideConfig.SPEC, FactoryAscent.MOD_ID + "-guide-server.toml");
        modBus.addListener(GuidePayloads::register);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(TOOLS_TAB)) e.accept(MANUAL.get());
        });
        // The book draws crafting grids: the client needs the crafting recipes too (26.x no longer sends them).
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> e.sendRecipes(RecipeType.CRAFTING));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) giveOnce(player);
        });
    }

    /** First join: one Manual (once per player, kept through death via the persisted tag). */
    static void giveOnce(ServerPlayer player) {
        if (!GuideConfig.giveOnFirstJoin()) return;
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        if (persisted.getBooleanOr(GIVEN_TAG, false)) return;
        persisted.putBoolean(GIVEN_TAG, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(MANUAL.get());
        if (!player.getInventory().add(book)) player.drop(book, false);
    }
}
