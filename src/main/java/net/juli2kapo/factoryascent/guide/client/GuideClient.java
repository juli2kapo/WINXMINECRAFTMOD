package net.juli2kapo.factoryascent.guide.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.guide.GuideBookItem;
import net.juli2kapo.factoryascent.guide.GuideEntries;
import net.juli2kapo.factoryascent.guide.GuidePayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the Manual: the book screen, the 3D viewer's renderer, the hologram projector
 * (drawing, auto-advance, keys, HUD line) and the little book button on machine screens that
 * opens the Manual at that machine's entry.
 */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class GuideClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "guide"));
    static final KeyMapping NEXT_LAYER = new KeyMapping("key.factoryascent.holo_next", InputConstants.Type.KEYSYM, InputConstants.KEY_RBRACKET, CATEGORY);
    static final KeyMapping PREV_LAYER = new KeyMapping("key.factoryascent.holo_prev", InputConstants.Type.KEYSYM, InputConstants.KEY_LBRACKET, CATEGORY);
    static final KeyMapping CLEAR = new KeyMapping("key.factoryascent.holo_clear", InputConstants.Type.KEYSYM, InputConstants.KEY_BACKSLASH, CATEGORY);

    private static final Set<String> UNLOCKED = new HashSet<>();
    private static final int BTN_W = 15, BTN_H = 13;

    public GuideClient(IEventBus modBus) {
        modBus.addListener((RegisterPictureInPictureRenderersEvent e) -> e.register(MultiblockPip.State.class, MultiblockPip::new));
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(GuidePayloads.Unlocks.TYPE, (payload, context) -> {
            UNLOCKED.clear();
            UNLOCKED.addAll(payload.done());
            if (Minecraft.getInstance().gui.screen() instanceof GuideScreen screen) screen.onUnlocks();
        }));
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(NEXT_LAYER);
            e.register(PREV_LAYER);
            e.register(CLEAR);
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "guide_hologram"), Hologram::hud));
        NeoForge.EVENT_BUS.addListener((RecipesReceivedEvent e) -> {
            GuideRecipes.onRecipesReceived(e);
            GuideRecipes.invalidate();
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (NEXT_LAYER.consumeClick()) Hologram.changeLayer(1);
            while (PREV_LAYER.consumeClick()) Hologram.changeLayer(-1);
            while (CLEAR.consumeClick()) Hologram.stop();
            Hologram.tick();
        });
        NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent e) -> Hologram.submit(e));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post e) -> renderBookButton(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (e.getMouseButtonEvent().button() == 0 && clickBookButton(e.getScreen(), e.getMouseX(), e.getMouseY())) e.setCanceled(true);
        });
        GuideBookItem.hooks = new GuideBookItem.ClientHooks() {
            @Override
            public boolean useOn(UseOnContext context) {
                if (!Hologram.active()) return false;
                Hologram.moveTo(context.getClickedPos().relative(context.getClickedFace()));
                return true;
            }

            @Override
            public void use(Player player, boolean sneaking) {
                if (sneaking && Hologram.active()) {
                    Hologram.rotate();
                    return;
                }
                if (player == Minecraft.getInstance().player) GuideScreen.open();
            }
        };
    }

    static Set<String> unlocked() {
        return UNLOCKED;
    }

    // ---------------------------------------------------------------- the book button on machine screens

    /** The item a container screen is about (from its title, "block.factoryascent.x"), if the Manual covers it. */
    private static @Nullable Item subject(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return null;
        if (!(screen.getTitle().getContents() instanceof TranslatableContents tc)) return null;
        String key = tc.getKey();
        String prefix = key.startsWith("block." + FactoryAscent.MOD_ID + ".") ? "block." + FactoryAscent.MOD_ID + "."
                : key.startsWith("item." + FactoryAscent.MOD_ID + ".") ? "item." + FactoryAscent.MOD_ID + "." : null;
        if (prefix == null) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, key.substring(prefix.length())))
                .orElse(Items.AIR);
        return item == Items.AIR ? null : item;
    }

    private static int[] buttonPos(AbstractContainerScreen<?> s) {
        return new int[]{s.getGuiLeft() + s.getXSize() - BTN_W - 4, s.getGuiTop() - BTN_H + 1};
    }

    private static void renderBookButton(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
        Item item = subject(screen);
        if (item == null) return;
        int[] p = buttonPos((AbstractContainerScreen<?>) screen);
        boolean hover = FactoryGui.inside(mx, my, p[0], p[1], BTN_W, BTN_H);
        // a tab on top of the panel with a little book on it
        g.fill(p[0], p[1], p[0] + BTN_W, p[1] + BTN_H, FactoryGui.OUTLINE);
        g.fill(p[0] + 1, p[1] + 1, p[0] + BTN_W - 1, p[1] + BTN_H, hover ? 0xFFD8D8D8 : FactoryGui.BG);
        g.fill(p[0] + 4, p[1] + 3, p[0] + 11, p[1] + 11, 0xFF6B4423);
        g.fill(p[0] + 5, p[1] + 4, p[0] + 10, p[1] + 10, 0xFFF4E9CD);
        g.fill(p[0] + 7, p[1] + 4, p[0] + 8, p[1] + 10, 0xFF6B4423);
        if (hover) {
            g.setComponentTooltipForNextFrame(Minecraft.getInstance().font, List.of(
                    Component.translatable("guide.factoryascent.open_manual"),
                    Component.translatable("guide.factoryascent.open_manual_tip").withStyle(net.minecraft.ChatFormatting.GRAY)), mx, my);
        }
    }

    private static boolean clickBookButton(Screen screen, double mx, double my) {
        Item item = subject(screen);
        if (item == null) return false;
        int[] p = buttonPos((AbstractContainerScreen<?>) screen);
        if (!FactoryGui.inside(mx, my, p[0], p[1], BTN_W, BTN_H)) return false;
        GuideScreen.openFor(item);
        return true;
    }

    /** Whether the Manual has anything about an item (used by GameTests-free code paths). */
    static boolean covers(Item item) {
        return GuideEntries.forItem(item) != null;
    }
}
