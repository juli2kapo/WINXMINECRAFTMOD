package net.juli2kapo.factoryascent.guide.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.client.ClientRecipes;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.guide.GuideEntries;
import net.juli2kapo.factoryascent.guide.GuideEntries.Chapter;
import net.juli2kapo.factoryascent.guide.GuideEntries.Entry;
import net.juli2kapo.factoryascent.guide.GuideMultiblocks;
import net.juli2kapo.factoryascent.guide.GuidePayloads;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The Factory Ascent Manual: a two-page book. Home (chapters and search), chapter pages (entries,
 * "???" while locked), entry pages (formatted text with item links on the left; on the right the
 * entry's recipes, or for multiblocks a 3D viewer with layers, materials and the hologram
 * projector) and item pages generated from the item's own descriptions and recipes.
 */
public class GuideScreen extends Screen {
    // ---------------------------------------------------------------- colours
    private static final int COVER = 0xFF6B4423, COVER_DARK = 0xFF3E2512, COVER_LIGHT = 0xFF8A5A30;
    private static final int PAGE = 0xFFF4E9CD, PAGE_SHADE = 0xFFE6D7B3, PAGE_EDGE = 0xFFCDB98E;
    private static final int INK = 0xFF3A2C1C, MUTED = 0xFF7F6E57, LINK = 0xFF1E5AA8, LOCKED = 0xFFA89A84;

    private enum Kind { HOME, CHAPTER, ENTRY, ITEM }

    private record Page(Kind kind, @Nullable Chapter chapter, @Nullable String entry, @Nullable Item item) {
        static Page home() {
            return new Page(Kind.HOME, null, null, null);
        }
    }

    private enum Tab { STRUCTURE, MATERIALS, RECIPES }

    /** A clickable area of the last frame. */
    private record Hit(int x, int y, int w, int h, @Nullable Runnable left, @Nullable Runnable right,
                       @Nullable List<Component> tooltip, @Nullable ItemStack stack) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // remembered between openings, like a bookmark
    private static Page lastPage = Page.home();
    private static final Deque<Page> HISTORY = new ArrayDeque<>();
    private static String lastQuery = "";

    private Page page;
    private int bx, by, bw, bh;
    private int leftScroll, rightScroll, leftMax, rightMax;
    private final List<Hit> hits = new ArrayList<>();
    private @Nullable EditBox search;

    // entry/item page state
    private Tab tab = Tab.STRUCTURE;
    private int variant, layer = -1, recipeItem, recipeIndex;
    private boolean showUses;
    private float yaw = 225, pitch = 30, zoom = 1f;
    private boolean autoRotate = true, dragging, shiftClick;
    private long lastFrame = System.nanoTime();
    private int vx, vy, vw, vh; // the 3D viewport (last frame)

    public GuideScreen() {
        this(null);
    }

    private GuideScreen(@Nullable Page start) {
        super(Component.translatable("item.factoryascent.factory_manual"));
        page = start != null ? start : lastPage;
    }

    /** Opens the book where it was left (or home). */
    public static void open() {
        Minecraft.getInstance().gui.setScreen(new GuideScreen());
    }

    /** Opens the book at an item: its entry if it has one (and it is unlocked), else its item page. */
    public static void openFor(Item item) {
        Entry e = GuideEntries.forItem(item);
        Page p = e != null && unlocked(e) ? new Page(Kind.ENTRY, e.chapter(), e.id(), null) : new Page(Kind.ITEM, null, null, item);
        if (!(lastPage.equals(p))) HISTORY.push(lastPage);
        lastPage = p;
        Minecraft.getInstance().gui.setScreen(new GuideScreen(p));
    }

    static boolean unlocked(Entry e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.advancement() == null) return true;
        if (mc.player != null && mc.player.isCreative()) return true;
        return GuideClient.unlocked().contains(e.advancement());
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        BlockQuads.clear();
        bw = Math.min(380, width - 8);
        bh = Math.min(228, height - 8);
        bx = (width - bw) / 2;
        by = (height - bh) / 2;
        ClientPacketDistributor.sendToServer(new GuidePayloads.Request());
        search = new EditBox(font, lx() + 2, by + bh - 40, pw() - 4, 14, Component.translatable("guide.factoryascent.search"));
        search.setHint(Component.translatable("guide.factoryascent.search").withStyle(ChatFormatting.GRAY));
        search.setValue(lastQuery);
        search.setResponder(s -> {
            lastQuery = s;
            rightScroll = 0;
        });
        search.visible = page.kind == Kind.HOME;
        addRenderableWidget(search);
    }

    private int lx() {
        return bx + 16;
    }

    private int rx() {
        return bx + bw / 2 + 9;
    }

    private int pw() {
        return bw / 2 - 25;
    }

    private int top() {
        return by + 12;
    }

    private int ph() {
        return bh - 36;
    }

    private void go(Page p) {
        if (p.equals(page)) return;
        HISTORY.push(page);
        while (HISTORY.size() > 64) HISTORY.removeLast();
        show(p);
    }

    private void show(Page p) {
        page = p;
        lastPage = p;
        leftScroll = rightScroll = 0;
        tab = Tab.STRUCTURE;
        variant = 0;
        layer = -1;
        recipeItem = recipeIndex = 0;
        showUses = false;
        zoom = 1f;
        if (search != null) search.visible = p.kind == Kind.HOME;
        Entry e = entry();
        if (e != null && e.multiblock() == null) tab = Tab.RECIPES;
    }

    private void back() {
        if (!HISTORY.isEmpty()) show(HISTORY.pop());
        else show(Page.home());
    }

    private @Nullable Entry entry() {
        return page.entry == null ? null : GuideEntries.get(page.entry);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        // cover
        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, COVER_DARK);
        g.fill(bx, by, bx + bw, by + bh, COVER);
        g.fill(bx, by, bx + bw, by + 2, COVER_LIGHT);
        // pages
        int mid = bx + bw / 2;
        g.fill(bx + 6, by + 5, mid - 1, by + bh - 6, PAGE_EDGE);
        g.fill(mid + 1, by + 5, bx + bw - 6, by + bh - 6, PAGE_EDGE);
        g.fill(bx + 7, by + 6, mid - 2, by + bh - 8, PAGE);
        g.fill(mid + 2, by + 6, bx + bw - 7, by + bh - 8, PAGE);
        g.fill(mid - 8, by + 6, mid - 2, by + bh - 8, PAGE_SHADE);
        g.fill(mid + 2, by + 6, mid + 8, by + bh - 8, PAGE_SHADE);
        g.fill(mid - 1, by + 4, mid + 1, by + bh - 5, COVER_DARK);
        // a ribbon in the chapter's colour
        int accent = accent();
        g.fill(mid + 14, by + bh - 8, mid + 20, by + bh + 6, accent);
        g.fill(mid + 15, by + bh + 6, mid + 19, by + bh + 8, accent);
    }

    private int accent() {
        Chapter c = page.chapter;
        if (c == null && entry() != null) c = entry().chapter();
        return c == null ? 0xFFB03A2E : c.color;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (autoRotate && !dragging) yaw = (yaw + dt * 24f) % 360f;
        hits.clear();
        switch (page.kind) {
            case HOME -> home(g, mouseX, mouseY);
            case CHAPTER -> chapter(g, mouseX, mouseY);
            case ENTRY -> entryPage(g, mouseX, mouseY);
            case ITEM -> itemPage(g, mouseX, mouseY);
        }
        nav(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partial);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(mouseX, mouseY)) continue;
            if (h.stack != null) {
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, h.stack));
                lines.add(Component.translatable(ModList.get().isLoaded("jei") ? "guide.factoryascent.link_hint_jei"
                        : "guide.factoryascent.link_hint").withStyle(ChatFormatting.DARK_GRAY));
                g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            } else if (h.tooltip != null) {
                g.setComponentTooltipForNextFrame(font, h.tooltip, mouseX, mouseY);
            }
            break;
        }
    }

    private void nav(GuiGraphicsExtractor g, int mx, int my) {
        int y = by + bh - 21;
        if (page.kind != Kind.HOME) {
            button(g, mx, my, lx(), y, 34, 13, Component.translatable("guide.factoryascent.back"), false, this::back, null);
            button(g, mx, my, lx() + 37, y, 34, 13, Component.translatable("guide.factoryascent.home"), false, () -> go(Page.home()), null);
        }
        if (Hologram.active()) {
            Component stop = Component.translatable("guide.factoryascent.holo.stop");
            int w = font.width(stop) + 8;
            button(g, mx, my, rx() + pw() - w, y, w, 13, stop, false, Hologram::stop, null);
        }
    }

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, Component label, boolean selected,
                        @Nullable Runnable action, @Nullable List<Component> tip) {
        boolean hover = FactoryGui.inside(mx, my, x, y, w, h);
        FactoryGui.button(g, x, y, w, h, hover, selected, accent());
        FormattedCharSequence text = FactoryGui.fit(font, label, w - 4);
        int tw = font.width(text);
        g.text(font, text, x + (w - tw) / 2 + (selected ? 1 : 0), y + (h - 8) / 2 + (selected ? 1 : 0),
                selected ? 0xFFFFFFFF : 0xFF202020, false);
        hits.add(new Hit(x, y, w, h, action, null, tip, null));
    }

    private void heading(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color) {
        g.text(font, FactoryGui.fit(font, text.copy().withStyle(ChatFormatting.BOLD), w), x, y, color, false);
        g.fill(x, y + 10, x + w, y + 11, PAGE_EDGE);
    }

    /** An item slot that links to the item's entry/page (right click: JEI). */
    private void itemSlot(GuiGraphicsExtractor g, ItemStack stack, int x, int y, boolean frame) {
        if (frame) FactoryGui.slot(g, x, y);
        if (stack.isEmpty()) return;
        g.item(stack, x, y);
        g.itemDecorations(font, stack, x, y);
        Item item = stack.getItem();
        hits.add(new Hit(x, y, 16, 16, () -> openItem(item), () -> jei(stack, false), null, stack));
    }

    private void openItem(Item item) {
        Entry e = GuideEntries.forItem(item);
        if (e != null && unlocked(e) && !e.id().equals(page.entry)) go(new Page(Kind.ENTRY, e.chapter(), e.id(), null));
        else if (e == null || !e.id().equals(page.entry)) go(new Page(Kind.ITEM, null, null, item));
    }

    private void jei(ItemStack stack, boolean uses) {
        if (ModList.get().isLoaded("jei")) {
            boolean shift = shiftClick;
            net.juli2kapo.factoryascent.guide.compat.GuideJeiPlugin.show(stack, uses || shift);
        } else {
            openItem(stack.getItem());
        }
    }

    // ---------------------------------------------------------------- text

    /** Draws markup text in a scrolled box; returns its full height. */
    private int text(GuiGraphicsExtractor g, String markup, int x, int y, int w, int clipTop, int clipBottom, int mx, int my) {
        GuideText.Laid laid = GuideText.layout(font, markup, w, INK, LINK, darkerAccent());
        for (GuideText.Word word : laid.words()) {
            int wy = y + word.y();
            if (wy < clipTop - 9 || wy > clipBottom) continue;
            boolean hover = word.link() != null && FactoryGui.inside(mx, my, x + word.x(), wy - 1, word.width(), 10)
                    && my >= clipTop && my < clipBottom;
            Style st = word.style();
            int color = hover ? 0xFF3C8CE0 : word.color();
            g.text(font, Component.literal(word.text()).withStyle(st), x + word.x(), wy, color, false);
            if (word.link() != null && wy >= clipTop && wy + 9 <= clipBottom) {
                GuideText.Link link = word.link();
                List<Component> tip = null;
                Runnable click;
                if (link.item() != null) {
                    Item item = link.item();
                    click = () -> openItem(item);
                    hits.add(new Hit(x + word.x(), wy - 1, word.width(), 10, click, () -> jei(new ItemStack(item), false), null, new ItemStack(item)));
                } else {
                    Entry target = GuideEntries.get(link.entry());
                    click = target == null ? null : () -> {
                        if (unlocked(target)) go(new Page(Kind.ENTRY, target.chapter(), target.id(), null));
                    };
                    tip = List.of(Component.translatable("guide.factoryascent.open_entry"));
                    hits.add(new Hit(x + word.x(), wy - 1, word.width(), 10, click, null, tip, null));
                }
            }
        }
        return laid.height();
    }

    private int darkerAccent() {
        return FactoryGui.darker(accent()) | 0xFF000000;
    }

    private static String tr(String key) {
        return Language.getInstance().has(key) ? Language.getInstance().getOrDefault(key) : "";
    }

    // ---------------------------------------------------------------- home

    private void home(GuiGraphicsExtractor g, int mx, int my) {
        int x = lx(), y = top(), w = pw();
        g.item(new ItemStack(GuideEntries.item("factory_manual")), x, y - 2);
        heading(g, Component.translatable("guide.factoryascent.title"), x + 20, y + 2, w - 20, darkerAccent());
        int clipBottom = by + bh - 44;
        g.enableScissor(x, y + 16, x + w, clipBottom);
        int h = text(g, tr("guide.factoryascent.home.text"), x, y + 18 - leftScroll, w, y + 16, clipBottom, mx, my);
        g.disableScissor();
        leftMax = Math.max(0, h - (clipBottom - y - 18));
        if (search != null) {
            search.setX(x + 2);
            search.setY(by + bh - 40);
        }
        // right page: chapters, or search results
        int r = rx();
        String q = lastQuery.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            searchResults(g, q, r, y, mx, my);
            return;
        }
        heading(g, Component.translatable("guide.factoryascent.chapters"), r, y, pw(), darkerAccent());
        Chapter[] chapters = Chapter.values();
        int colW = pw() / 2, rows = (chapters.length + 1) / 2;
        int rowH = Math.min(18, (ph() - 16) / rows);
        for (int i = 0; i < chapters.length; i++) {
            Chapter c = chapters[i];
            int cx = r + (i / rows) * colW, cy = y + 14 + (i % rows) * rowH;
            boolean hover = FactoryGui.inside(mx, my, cx, cy, colW - 2, rowH);
            if (hover) g.fill(cx, cy, cx + colW - 2, cy + rowH, 0x30000000);
            g.fill(cx, cy + 2, cx + 2, cy + rowH - 2, c.color);
            g.item(new ItemStack(GuideEntries.item(c.icon)), cx + 4, cy + (rowH - 16) / 2);
            g.text(font, FactoryGui.fit(font, Component.translatable(c.key()), colW - 26), cx + 22, cy + (rowH - 8) / 2, INK, false);
            hits.add(new Hit(cx, cy, colW - 2, rowH, () -> go(new Page(Kind.CHAPTER, c, null, null)), null, null, null));
        }
    }

    private void searchResults(GuiGraphicsExtractor g, String q, int r, int y, int mx, int my) {
        heading(g, Component.translatable("guide.factoryascent.results"), r, y, pw(), darkerAccent());
        List<Runnable> actions = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        List<Boolean> locked = new ArrayList<>();
        for (Entry e : GuideEntries.all()) {
            Component title = Component.translatable(e.titleKey());
            if (!title.getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            boolean open = unlocked(e);
            labels.add(open ? title : Component.literal("???"));
            icons.add(new ItemStack(GuideEntries.item(e.icon())));
            locked.add(!open);
            actions.add(open ? () -> go(new Page(Kind.ENTRY, e.chapter(), e.id(), null)) : null);
        }
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(FactoryAscent.MOD_ID)) continue;
            Component name = item.getName();
            if (!name.getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            labels.add(name);
            icons.add(new ItemStack(item));
            locked.add(false);
            actions.add(() -> go(new Page(Kind.ITEM, null, null, item)));
        }
        list(g, r, y + 14, pw(), ph() - 14, labels, icons, locked, actions, mx, my);
    }

    /** A scrolling list of icon + label rows on the right page. */
    private void list(GuiGraphicsExtractor g, int x, int y, int w, int h, List<Component> labels, List<ItemStack> icons,
                      List<Boolean> locked, List<Runnable> actions, int mx, int my) {
        int rowH = 18;
        rightMax = Math.max(0, labels.size() * rowH - h);
        rightScroll = Math.min(rightScroll, rightMax);
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < labels.size(); i++) {
            int ry = y + i * rowH - rightScroll;
            if (ry + rowH < y || ry > y + h) continue;
            boolean isLocked = locked.get(i);
            boolean hover = !isLocked && FactoryGui.inside(mx, my, x, ry, w, rowH) && my >= y && my < y + h;
            if (hover) g.fill(x, ry, x + w, ry + rowH, 0x30000000);
            if (isLocked) {
                g.fill(x + 1, ry + 1, x + 17, ry + 17, 0xFFB8A888);
                g.text(font, "?", x + 7, ry + 5, 0xFF6E5E48, false);
            } else {
                g.item(icons.get(i), x + 1, ry + 1);
            }
            g.text(font, FactoryGui.fit(font, labels.get(i), w - 24), x + 21, ry + 5, isLocked ? LOCKED : INK, false);
            if (ry >= y && ry + rowH <= y + h) {
                hits.add(new Hit(x, ry, w, rowH, actions.get(i), null, null, null));
            }
        }
        g.disableScissor();
    }

    // ---------------------------------------------------------------- chapter

    private void chapter(GuiGraphicsExtractor g, int mx, int my) {
        Chapter c = page.chapter;
        if (c == null) return;
        int x = lx(), y = top(), w = pw();
        g.item(new ItemStack(GuideEntries.item(c.icon)), x, y - 2);
        heading(g, Component.translatable(c.key()), x + 20, y + 2, w - 20, darkerAccent());
        int clipBottom = by + bh - 24;
        g.enableScissor(x, y + 16, x + w, clipBottom);
        int h = text(g, tr(c.key() + ".text"), x, y + 18 - leftScroll, w, y + 16, clipBottom, mx, my);
        g.disableScissor();
        leftMax = Math.max(0, h - (clipBottom - y - 18));
        int r = rx();
        heading(g, Component.translatable("guide.factoryascent.entries"), r, y, pw(), darkerAccent());
        List<Component> labels = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        List<Boolean> locked = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        List<Entry> entries = GuideEntries.of(c);
        for (Entry e : entries) {
            boolean open = unlocked(e);
            labels.add(open ? Component.translatable(e.titleKey()) : Component.literal("???"));
            icons.add(new ItemStack(GuideEntries.item(e.icon())));
            locked.add(!open);
            actions.add(open ? () -> go(new Page(Kind.ENTRY, c, e.id(), null)) : null);
        }
        list(g, r, y + 14, pw(), ph() - 30, labels, icons, locked, actions, mx, my);
        // locked rows: tell which advancement opens them
        for (int i = 0; i < entries.size(); i++) {
            if (!locked.get(i)) continue;
            int ry = y + 14 + i * 18 - rightScroll;
            if (ry < y + 14 || ry + 18 > y + 14 + ph() - 30) continue;
            hits.add(new Hit(r, ry, pw(), 18, null, null, List.of(
                    Component.translatable("guide.factoryascent.locked").withStyle(ChatFormatting.GRAY),
                    advancementTitle(entries.get(i).advancement()).copy().withStyle(ChatFormatting.GOLD)), null));
        }
    }

    private static Component advancementTitle(@Nullable String path) {
        if (path == null) return Component.empty();
        return Component.translatable("advancements." + FactoryAscent.MOD_ID + "." + path + ".title");
    }

    // ---------------------------------------------------------------- entry

    private void entryPage(GuiGraphicsExtractor g, int mx, int my) {
        Entry e = entry();
        if (e == null) {
            show(Page.home());
            return;
        }
        int x = lx(), y = top(), w = pw();
        g.item(new ItemStack(GuideEntries.item(e.icon())), x, y - 2);
        heading(g, Component.translatable(e.titleKey()), x + 20, y + 2, w - 20, darkerAccent());
        StringBuilder body = new StringBuilder(tr(e.textKey()));
        List<GuideMultiblocks.Layout> layouts = e.multiblock() == null ? List.of() : GuideMultiblocks.variants(e.multiblock());
        if (!layouts.isEmpty()) {
            GuideMultiblocks.Layout l = layouts.get(Math.min(variant, layouts.size() - 1));
            if (!l.notes.isEmpty()) {
                body.append("\n\n# ").append(tr("guide.factoryascent.notes"));
                for (String n : l.notes) body.append("\n- ").append(tr(n));
            }
        }
        int clipBottom = by + bh - 24;
        g.enableScissor(x, y + 16, x + w, clipBottom);
        int h = text(g, body.toString(), x, y + 18 - leftScroll, w, y + 16, clipBottom, mx, my);
        g.disableScissor();
        leftMax = Math.max(0, h - (clipBottom - y - 18));
        scrollBar(g, x + w + 2, y + 16, clipBottom - y - 16, leftScroll, leftMax);
        // right page
        int r = rx();
        if (!layouts.isEmpty()) {
            int tw = (pw() - 4) / 3;
            button(g, mx, my, r, y - 2, tw, 12, Component.translatable("guide.factoryascent.tab.structure"), tab == Tab.STRUCTURE, () -> tab = Tab.STRUCTURE, null);
            button(g, mx, my, r + tw + 2, y - 2, tw, 12, Component.translatable("guide.factoryascent.tab.materials"), tab == Tab.MATERIALS, () -> tab = Tab.MATERIALS, null);
            button(g, mx, my, r + 2 * tw + 4, y - 2, tw, 12, Component.translatable("guide.factoryascent.tab.recipes"), tab == Tab.RECIPES, () -> tab = Tab.RECIPES, null);
            int ty = y + 13;
            GuideMultiblocks.Layout l = layouts.get(Math.min(variant, layouts.size() - 1));
            if (layouts.size() > 1) {
                int vw0 = (pw() - 2 * (layouts.size() - 1)) / layouts.size();
                for (int i = 0; i < layouts.size(); i++) {
                    int idx = i;
                    button(g, mx, my, r + i * (vw0 + 2), ty, vw0, 12, Component.translatable(layouts.get(i).nameKey + ".short"), i == variant, () -> {
                        variant = idx;
                        layer = -1;
                    }, List.of(Component.translatable(layouts.get(i).nameKey)));
                }
                ty += 14;
            }
            switch (tab) {
                case STRUCTURE -> viewer(g, l, r, ty, pw(), by + bh - 24 - ty, mx, my);
                case MATERIALS -> materials(g, l, r, ty, pw(), by + bh - 24 - ty, mx, my);
                case RECIPES -> recipes(g, e.items(), r, ty, pw(), mx, my);
            }
        } else {
            recipes(g, e.items(), r, y, pw(), mx, my);
        }
    }

    private void scrollBar(GuiGraphicsExtractor g, int x, int y, int h, int scroll, int max) {
        if (max <= 0) return;
        g.fill(x, y, x + 2, y + h, PAGE_SHADE);
        int knob = Math.max(10, h * h / (h + max));
        int ky = y + (h - knob) * scroll / max;
        g.fill(x, ky, x + 2, ky + knob, MUTED);
    }

    // ---------------------------------------------------------------- 3D viewer

    private void viewer(GuiGraphicsExtractor g, GuideMultiblocks.Layout l, int x, int y, int w, int h, int mx, int my) {
        int controlsH = 14;
        vx = x;
        vy = y;
        vw = w;
        vh = h - controlsH - 2;
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF2B2F36);
        g.fill(vx + 1, vy + 1, vx + vw - 1, vy + vh - 1, 0xFF3A4048);
        float diag = (float) Math.sqrt(l.width * l.width + l.height * l.height + l.depth * l.depth);
        float scale = Math.min(vw, vh) / diag * 0.95f * zoom;
        int[] hover = pick(l, scale, mx, my);
        g.submitPictureInPictureRenderState(new MultiblockPip.State(l, yaw, pitch, scale, layer,
                hover == null ? -99 : hover[0], hover == null ? -99 : hover[1], hover == null ? -99 : hover[2],
                vx + 1, vy + 1, vx + vw - 1, vy + vh - 1, null));
        // layer read-out in the corner
        Component layerText = layer < 0 ? Component.translatable("guide.factoryascent.all_layers", l.height)
                : Component.translatable("guide.factoryascent.layer", layer + 1, l.height);
        g.text(font, layerText, vx + 4, vy + 4, 0xFFE4E8EE, true);
        g.text(font, Component.translatable("guide.factoryascent.size", l.width, l.height, l.depth), vx + 4, vy + vh - 12, 0xFF9AA4B0, true);
        if (hover != null) {
            BlockState s = l.at(hover[0], hover[1], hover[2]);
            if (s != null) {
                hits.add(new Hit(mx, my, 1, 1, null, null, List.of(s.getBlock().getName(),
                        Component.translatable("guide.factoryascent.layer", hover[1] + 1, l.height).withStyle(ChatFormatting.GRAY)), null));
            }
        }
        // controls
        int cy = vy + vh + 2, bwid = 14;
        int cx = x;
        button(g, mx, my, cx, cy, bwid, 13, Component.literal("<"), false, () -> layer = layer < 0 ? l.height - 1 : Math.max(0, layer - 1),
                List.of(Component.translatable("guide.factoryascent.layer_down")));
        cx += bwid + 1;
        button(g, mx, my, cx, cy, bwid, 13, Component.literal(">"), false, () -> layer = layer < 0 ? 0 : layer + 1 >= l.height ? -1 : layer + 1,
                List.of(Component.translatable("guide.factoryascent.layer_up")));
        cx += bwid + 1;
        int allW = font.width(Component.translatable("guide.factoryascent.all")) + 8;
        button(g, mx, my, cx, cy, allW, 13, Component.translatable("guide.factoryascent.all"), layer < 0, () -> layer = -1, null);
        cx += allW + 1;
        button(g, mx, my, cx, cy, bwid, 13, Component.literal("⟳"), autoRotate, () -> autoRotate = !autoRotate,
                List.of(Component.translatable("guide.factoryascent.auto_rotate")));
        cx += bwid + 1;
        Component project = Component.translatable("guide.factoryascent.project");
        int pwid = x + w - cx;
        button(g, mx, my, cx, cy, pwid, 13, project, false, () -> project(l),
                List.of(Component.translatable("guide.factoryascent.project_tip")));
        // drag & zoom hint
        if (FactoryGui.inside(mx, my, vx, vy, vw, vh) && hover == null) {
            hits.add(new Hit(vx, vy, vw, vh, null, null, List.of(Component.translatable("guide.factoryascent.viewer_hint").withStyle(ChatFormatting.GRAY)), null));
        }
    }

    /** The block under the mouse in the viewer: {x, y, z} or null (ray cast through the inverse view). */
    private int @Nullable [] pick(GuideMultiblocks.Layout l, float scale, int mx, int my) {
        if (!FactoryGui.inside(mx, my, vx + 1, vy + 1, vw - 2, vh - 2) || dragging) return null;
        Matrix4f m = MultiblockPip.guiMatrix(l, yaw, pitch, scale, vx + 1, vy + 1, vx + vw - 1, vy + vh - 1);
        Matrix4f inv = new Matrix4f(m).invert();
        Vector3f a = inv.transformPosition(mx + 0.5f, my + 0.5f, 0, new Vector3f());
        Vector3f b = inv.transformPosition(mx + 0.5f, my + 0.5f, 1, new Vector3f());
        Vector3f d = b.sub(a, new Vector3f());
        // towards the viewer = the direction in which the top of the structure lies (we look from above)
        float topZ = m.transformPosition(0, l.height, 0, new Vector3f()).z, bottomZ = m.transformPosition(0, 0, 0, new Vector3f()).z;
        boolean nearIsLarger = pitch >= 0 ? topZ > bottomZ : topZ < bottomZ;
        int[] best = null;
        float bestT = 0;
        for (GuideMultiblocks.Cell c : l.cells()) {
            if (layer >= 0 && c.y() != layer) continue;
            float t0 = -Float.MAX_VALUE, t1 = Float.MAX_VALUE;
            float[] o = {a.x, a.y, a.z}, dir = {d.x, d.y, d.z}, lo = {c.x(), c.y(), c.z()};
            boolean miss = false;
            for (int k = 0; k < 3; k++) {
                if (Math.abs(dir[k]) < 1e-9f) {
                    if (o[k] < lo[k] || o[k] > lo[k] + 1) miss = true;
                    continue;
                }
                float ta = (lo[k] - o[k]) / dir[k], tb = (lo[k] + 1 - o[k]) / dir[k];
                t0 = Math.max(t0, Math.min(ta, tb));
                t1 = Math.min(t1, Math.max(ta, tb));
            }
            if (miss || t0 > t1) continue;
            float near = nearIsLarger ? t1 : -t0;
            if (best == null || near > bestT) {
                best = new int[]{c.x(), c.y(), c.z()};
                bestT = near;
            }
        }
        return best;
    }

    private void project(GuideMultiblocks.Layout l) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos at;
        HitResult hit = mc.player.pick(20, 1f, false);
        Direction look = mc.player.getDirection();
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            at = bhr.getBlockPos().relative(bhr.getDirection());
        } else {
            at = mc.player.blockPosition().relative(look, 2);
        }
        BlockQuads.clear();
        Hologram.start(l, at, look.getOpposite());
        onClose();
    }

    // ---------------------------------------------------------------- materials

    private void materials(GuiGraphicsExtractor g, GuideMultiblocks.Layout l, int x, int y, int w, int h, int mx, int my) {
        Map<Item, Integer> bom = l.materials();
        Minecraft mc = Minecraft.getInstance();
        g.text(font, Component.translatable("guide.factoryascent.bom", l.blockCount()).withStyle(ChatFormatting.BOLD), x, y + 2, darkerAccent(), false);
        int rowH = 19, ry = y + 14;
        rightMax = Math.max(0, bom.size() * rowH - (h - 14));
        rightScroll = Math.min(rightScroll, rightMax);
        g.enableScissor(x, y + 13, x + w, y + h);
        int i = 0;
        for (var en : bom.entrySet()) {
            int yy = ry + i * rowH - rightScroll;
            i++;
            if (yy + rowH < y + 13 || yy > y + h) continue;
            ItemStack stack = new ItemStack(en.getKey());
            int have = 0;
            if (mc.player != null) {
                for (int s = 0; s < mc.player.getInventory().getContainerSize(); s++) {
                    ItemStack st = mc.player.getInventory().getItem(s);
                    if (st.is(en.getKey())) have += st.getCount();
                }
            }
            if (yy >= y + 13 && yy + 18 <= y + h) itemSlot(g, stack, x + 1, yy + 1, true);
            else {
                FactoryGui.slot(g, x + 1, yy + 1);
                g.item(stack, x + 1, yy + 1);
            }
            g.text(font, FactoryGui.fit(font, stack.getHoverName(), w - 70), x + 22, yy + 1, INK, false);
            g.text(font, Component.literal("× " + en.getValue()).withStyle(ChatFormatting.BOLD), x + 22, yy + 10, INK, false);
            Component hv = Component.translatable("guide.factoryascent.have", have);
            g.text(font, hv, x + w - font.width(hv) - 2, yy + 10, have >= en.getValue() ? 0xFF2E8B3A : 0xFFB03030, false);
        }
        g.disableScissor();
    }

    // ---------------------------------------------------------------- recipes

    private void recipes(GuiGraphicsExtractor g, List<String> itemIds, int x, int y, int w, int mx, int my) {
        List<Item> items = new ArrayList<>();
        for (String id : itemIds) {
            Item it = GuideEntries.item(id);
            if (it != Items.AIR) items.add(it);
        }
        if (items.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("guide.factoryascent.no_items"), x, y + 4, w, MUTED, false);
            return;
        }
        recipeItem = Math.min(recipeItem, items.size() - 1);
        // strip of the entry's items
        int per = Math.max(1, w / 18);
        for (int i = 0; i < items.size(); i++) {
            int sx = x + (i % per) * 18, sy = y + (i / per) * 18;
            if (i == recipeItem) g.fill(sx - 1, sy - 1, sx + 17, sy + 17, accent());
            ItemStack st = new ItemStack(items.get(i));
            g.item(st, sx, sy);
            int idx = i;
            hits.add(new Hit(sx, sy, 16, 16, () -> {
                recipeItem = idx;
                recipeIndex = 0;
            }, () -> jei(st, false), null, st));
        }
        int ry = y + ((items.size() - 1) / per + 1) * 18 + 4;
        recipeBox(g, items.get(recipeItem), x, ry, w, by + bh - 24 - ry, mx, my);
    }

    /** One item's recipes, one at a time with arrows; "uses" toggles what it is used in. */
    private void recipeBox(GuiGraphicsExtractor g, Item item, int x, int y, int w, int h, int mx, int my) {
        List<GuideRecipes.View> views = showUses ? GuideRecipes.using(item) : GuideRecipes.producing(item);
        Component label = Component.translatable(showUses ? "guide.factoryascent.used_in" : "guide.factoryascent.made_by",
                new ItemStack(item).getHoverName());
        g.text(font, FactoryGui.fit(font, label.copy().withStyle(ChatFormatting.BOLD), w - 40), x, y, darkerAccent(), false);
        Component toggle = Component.translatable(showUses ? "guide.factoryascent.show_recipes" : "guide.factoryascent.show_uses");
        int tw = font.width(toggle) + 6;
        button(g, mx, my, x + w - tw, y - 2, tw, 12, toggle, showUses, () -> {
            showUses = !showUses;
            recipeIndex = 0;
        }, null);
        int top = y + 13;
        if (views.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable(ClientRecipes.ready() ? "guide.factoryascent.no_recipe" : "guide.factoryascent.no_recipes_synced"),
                    x, top + 2, w, MUTED, false);
            return;
        }
        recipeIndex = Math.floorMod(recipeIndex, views.size());
        GuideRecipes.View v = views.get(recipeIndex);
        drawRecipe(g, v, x, top + 2, w);
        if (views.size() > 1) {
            int ay = Math.min(top + 84, y + h - 14);
            button(g, mx, my, x, ay, 14, 13, Component.literal("<"), false, () -> recipeIndex--, null);
            Component n = Component.literal((recipeIndex + 1) + " / " + views.size());
            g.text(font, n, x + w / 2 - font.width(n) / 2, ay + 3, MUTED, false);
            button(g, mx, my, x + w - 14, ay, 14, 13, Component.literal(">"), false, () -> recipeIndex++, null);
        }
    }

    private void drawRecipe(GuiGraphicsExtractor g, GuideRecipes.View v, int x, int y, int w) {
        long tick = System.currentTimeMillis() / 1000;
        // what makes it: the table, the furnace or the machines
        int mx0 = x;
        for (int i = 0; i < Math.min(5, v.machines().size()); i++) {
            itemSlot(g, v.machines().get(i), mx0, y, false);
            mx0 += 17;
        }
        if (!v.note().isEmpty()) g.text(font, v.note(), x + w - font.width(v.note()), y + 4, MUTED, false);
        int gy = y + 20;
        int cols = v.kind() == GuideRecipes.Kind.CRAFTING ? Math.max(1, v.width()) : Math.min(4, v.inputs().size());
        cols = Math.max(cols, 1);
        for (int i = 0; i < v.inputs().size(); i++) {
            List<ItemStack> alts = v.inputs().get(i);
            int sx = x + 1 + (i % cols) * 18, sy = gy + (i / cols) * 18;
            ItemStack shown = alts.isEmpty() ? ItemStack.EMPTY : alts.get((int) (tick % alts.size()));
            itemSlot(g, shown, sx, sy, true);
        }
        if (v.kind() == GuideRecipes.Kind.CRAFTING) {
            for (int i = v.inputs().size(); i < v.width() * v.height(); i++) FactoryGui.slot(g, x + 1 + (i % cols) * 18, gy + (i / cols) * 18);
        }
        int rows = Math.max(1, (v.inputs().size() + cols - 1) / cols);
        int ax = x + 1 + cols * 18 + 6, ay = gy + rows * 9 - 4;
        // arrow
        g.fill(ax, ay + 3, ax + 14, ay + 5, MUTED);
        for (int i = 0; i < 4; i++) g.fill(ax + 10 + i, ay + i, ax + 11 + i, ay + 8 - i, MUTED);
        int ox = ax + 22;
        itemSlot(g, v.output(), ox, ay - 4, true);
        for (int i = 0; i < v.extras().size() && i < 3; i++) itemSlot(g, v.extras().get(i), ox + 20 + i * 18, ay - 4, true);
    }

    // ---------------------------------------------------------------- item page

    private void itemPage(GuiGraphicsExtractor g, int mx, int my) {
        Item item = page.item;
        if (item == null) return;
        ItemStack stack = new ItemStack(item);
        int x = lx(), y = top(), w = pw();
        g.item(stack, x, y - 2);
        heading(g, stack.getHoverName(), x + 20, y + 2, w - 20, darkerAccent());
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        StringBuilder body = new StringBuilder();
        for (String k : List.of("desc." + id.getNamespace() + "." + id.getPath(), "tooltip." + id.getNamespace() + "." + id.getPath(),
                "tooltip." + id.getNamespace() + ".multiblock." + id.getPath(), "tooltip." + id.getNamespace() + ".fluid." + id.getPath())) {
            String t = tr(k);
            if (!t.isEmpty() && !t.contains("%")) body.append(t).append("\n");
        }
        if (body.isEmpty()) body.append(tr("guide.factoryascent.item.nothing")).append("\n");
        List<MachineType> used = ClientRecipes.usedIn(stack);
        if (!used.isEmpty()) {
            body.append("\n# ").append(tr("guide.factoryascent.item.machines")).append("\n");
            for (MachineType t : used) {
                Identifier mid = BuiltInRegistries.ITEM.getKey(net.juli2kapo.factoryascent.registry.ModBlocks.machine(t).get().asItem());
                body.append("- [[").append(mid).append("]]\n");
            }
        }
        List<String> mentions = new ArrayList<>();
        for (Entry e : GuideEntries.all()) {
            if (unlocked(e) && e.items().stream().anyMatch(s -> GuideEntries.item(s) == item)) mentions.add(e.id());
        }
        if (!mentions.isEmpty()) {
            body.append("\n# ").append(tr("guide.factoryascent.item.see")).append("\n");
            for (String m : mentions) body.append("- [[@").append(m).append("]]\n");
        }
        int clipBottom = by + bh - 24;
        g.enableScissor(x, y + 16, x + w, clipBottom);
        int h = text(g, body.toString(), x, y + 18 - leftScroll, w, y + 16, clipBottom, mx, my);
        g.disableScissor();
        leftMax = Math.max(0, h - (clipBottom - y - 18));
        recipeBox(g, item, rx(), y + 2, pw(), ph() - 4, mx, my);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        shiftClick = event.hasShiftDown();
        if (event.button() == 3) {
            back();
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(mx, my)) continue;
            Runnable r = event.button() == 1 ? h.right : event.button() == 0 ? h.left : null;
            if (r != null) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f));
                r.run();
                return true;
            }
        }
        if (page.kind == Kind.ENTRY && tab == Tab.STRUCTURE && FactoryGui.inside(mx, my, vx, vy, vw, vh) && event.button() == 0) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            yaw = (yaw + (float) dx * 1.2f) % 360f;
            pitch = Math.max(-89f, Math.min(89f, pitch + (float) dy * 1.2f));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (page.kind == Kind.ENTRY && tab == Tab.STRUCTURE && FactoryGui.inside(mx, my, vx, vy, vw, vh)) {
            zoom = (float) Math.max(0.4, Math.min(5.0, zoom * Math.pow(1.15, sy)));
            return true;
        }
        if (mx < bx + bw / 2.0) leftScroll = (int) Math.max(0, Math.min(leftMax, leftScroll - sy * 12));
        else rightScroll = (int) Math.max(0, Math.min(rightMax, rightScroll - sy * 12));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused() && event.key() != 256) return super.keyPressed(event);
        if (event.key() == 259) { // backspace
            back();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        lastPage = page;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Called when the server's answer about advancements arrives. */
    void onUnlocks() {
        // nothing to rebuild: the pages read the unlocked set every frame
    }
}
