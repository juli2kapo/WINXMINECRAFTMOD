package net.juli2kapo.factoryascent.storagenet.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.juli2kapo.factoryascent.storagenet.StorageFormat;
import net.juli2kapo.factoryascent.storagenet.StorageNet;
import net.juli2kapo.factoryascent.storagenet.TerminalClickPayload;
import net.juli2kapo.factoryascent.storagenet.TerminalMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The storage terminal: a scrollable, searchable grid of everything in the network. The grid is
 * drawn from the list the server syncs into the menu; clicks are sent to the server, which does the
 * actual moving. Left-click takes a stack, right-click half a stack, shift-click moves a stack into
 * the inventory; clicking with an item on the cursor puts it in (right-click: just one).
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int COLS = TerminalMenu.COLS, ROWS = TerminalMenu.ROWS;
    private static final int GRID_X = TerminalMenu.GRID_X, GRID_Y = TerminalMenu.GRID_Y;
    private static final int GRID_W = COLS * 18, GRID_H = ROWS * 18;
    private static final int BAR_X = GRID_X + GRID_W + 4, BAR_W = 12;
    private static final int KNOB_H = 15;
    private static final int STATUS_Y = GRID_Y + GRID_H + 4;
    private static final int SEARCH_X = 92, SEARCH_Y = 5, SEARCH_W = 94;

    /** Remembered across openings, like the creative search. */
    private static String lastSearch = "";

    private EditBox search;
    private final List<Entry> visible = new ArrayList<>();
    private int builtRevision = -1;
    private String builtQuery = null;
    private int scroll;
    private boolean draggingBar;
    /** The last press was a grid click, so its release must not reach the container logic. */
    private boolean gridPressed;

    private record Entry(ItemResource resource, long count, ItemStack stack) {}

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TerminalMenu.WIDTH, TerminalMenu.HEIGHT);
        this.inventoryLabelY = TerminalMenu.PLAYER_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + SEARCH_X + 2, topPos + SEARCH_Y + 2, SEARCH_W - 4, 9,
                Component.translatable("gui.factoryascent.storage.search"));
        search.setMaxLength(50);
        search.setBordered(false);
        search.setTextColor(0xFFFFFFFF);
        search.setHint(Component.translatable("gui.factoryascent.storage.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(lastSearch);
        search.setResponder(s -> {
            lastSearch = s;
            scroll = 0;
        });
        addRenderableWidget(search);
    }

    // ---------------------------------------------------------------- list

    private void rebuild() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (menu.revision() == builtRevision && query.equals(builtQuery)) return;
        builtRevision = menu.revision();
        builtQuery = query;
        visible.clear();
        for (Map.Entry<ItemResource, Long> e : menu.clientItems().entrySet()) {
            ItemResource r = e.getKey();
            if (!query.isEmpty() && !matches(r, query)) continue;
            visible.add(new Entry(r, e.getValue(), r.toStack(1)));
        }
        visible.sort(Comparator.comparingLong(Entry::count).reversed()
                .thenComparing(en -> en.stack().getHoverName().getString()));
        scroll = Math.min(scroll, maxScroll());
    }

    private static boolean matches(ItemResource r, String query) {
        if (query.startsWith("@")) {
            return BuiltInRegistries.ITEM.getKey(r.getItem()).getNamespace().contains(query.substring(1));
        }
        return r.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private int totalRows() {
        return (visible.size() + COLS - 1) / COLS;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - ROWS);
    }

    private @Nullable Entry entryAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - GRID_X, ry = mouseY - topPos - GRID_Y;
        if (rx < 0 || ry < 0 || rx >= GRID_W || ry >= GRID_H) return null;
        int i = (scroll + (int) (ry / 18)) * COLS + (int) (rx / 18);
        return i < visible.size() ? visible.get(i) : null;
    }

    private boolean inGrid(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - GRID_X, ry = mouseY - topPos - GRID_Y;
        return rx >= 0 && ry >= 0 && rx < GRID_W && ry < GRID_H;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        rebuild();
        int x = leftPos, y = topPos;
        StorageGui.panel(g, x, y, imageWidth, imageHeight);

        // search field
        g.fill(x + SEARCH_X, y + SEARCH_Y, x + SEARCH_X + SEARCH_W, y + SEARCH_Y + 12, StorageGui.SLOT_DARK);
        g.fill(x + SEARCH_X + 1, y + SEARCH_Y + 1, x + SEARCH_X + SEARCH_W - 1, y + SEARCH_Y + 11,
                search != null && search.isFocused() ? 0xFF101010 : 0xFF202020);

        // grid
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) StorageGui.slot(g, x + GRID_X + c * 18 + 1, y + GRID_Y + r * 18 + 1);
        }
        Entry hovered = entryAt(mouseX, mouseY);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int i = (scroll + r) * COLS + c;
                if (i >= visible.size()) break;
                Entry e = visible.get(i);
                int ix = x + GRID_X + c * 18 + 1, iy = y + GRID_Y + r * 18 + 1;
                if (e == hovered) g.fill(ix, iy, ix + 16, iy + 16, 0x80FFFFFF);
                g.item(e.stack(), ix, iy);
                StorageGui.count(g, font, StorageFormat.compact(e.count()), ix, iy);
            }
        }

        // scroll bar
        int bx = x + BAR_X, by = y + GRID_Y;
        g.fill(bx, by, bx + BAR_W, by + GRID_H, StorageGui.SLOT_DARK);
        g.fill(bx + 1, by + 1, bx + BAR_W - 1, by + GRID_H - 1, 0xFF6B6B6B);
        int knobY = knobY();
        boolean active = maxScroll() > 0;
        g.fill(bx + 1, knobY, bx + BAR_W - 1, knobY + KNOB_H, active ? 0xFFE0E0E0 : 0xFF9A9A9A);
        g.fill(bx + 2, knobY + 1, bx + BAR_W - 2, knobY + KNOB_H - 1, active ? StorageGui.ACCENT : 0xFF8B8B8B);

        StorageGui.playerInventory(g, x, y, TerminalMenu.PLAYER_INV_Y);
    }

    private int knobY() {
        int top = topPos + GRID_Y + 1, range = GRID_H - 2 - KNOB_H;
        int max = maxScroll();
        return max == 0 ? top : top + Math.round(range * (float) scroll / max);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 7, StorageGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, StorageGui.TEXT, false);
        StorageNet.Status status = menu.clientStatus();
        Component line;
        if (status != StorageNet.Status.ONLINE) {
            line = Component.translatable(status.key()).withStyle(ChatFormatting.DARK_RED);
        } else {
            line = Component.translatable("gui.factoryascent.storage.usage",
                    StorageFormat.compact(menu.clientUsed()), StorageFormat.compact(menu.clientCapacity()),
                    menu.clientTypes(), menu.clientMaxTypes());
        }
        g.text(font, line, 8, STATUS_Y, StorageGui.TEXT, false);
        if (status == StorageNet.Status.ONLINE && menu.clientItems().isEmpty()) {
            Component empty = Component.translatable("gui.factoryascent.storage.empty");
            g.text(font, empty, GRID_X + (GRID_W - font.width(empty)) / 2, GRID_Y + GRID_H / 2 - 4, 0xFFE0E0E0, true);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) return;
        Entry e = entryAt(mouseX, mouseY);
        if (e == null) return;
        List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(e.stack()));
        lines.add(Component.translatable("gui.factoryascent.storage.stored", String.format(Locale.ROOT, "%,d", e.count()))
                .withStyle(ChatFormatting.GOLD));
        g.setTooltipForNextFrame(font, lines, e.stack().getTooltipImage(), e.stack(), mouseX, mouseY);
    }

    // ---------------------------------------------------------------- input

    private void send(ItemResource resource, int action) {
        ClientPacketDistributor.sendToServer(new TerminalClickPayload(menu.containerId, resource, action));
    }

    private boolean onBar(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - BAR_X, ry = mouseY - topPos - GRID_Y;
        return rx >= 0 && ry >= 0 && rx < BAR_W && ry < GRID_H;
    }

    private void dragBar(double mouseY) {
        int max = maxScroll();
        if (max == 0) return;
        double f = (mouseY - topPos - GRID_Y - 1 - KNOB_H / 2.0) / (GRID_H - 2 - KNOB_H);
        scroll = Math.max(0, Math.min(max, (int) Math.round(f * max)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        gridPressed = false;
        if (inGrid(event.x(), event.y()) && (event.button() == 0 || event.button() == 1)) {
            if (search != null) search.setFocused(false);
            setFocused(null);
            gridPressed = true;
            boolean left = event.button() == 0;
            if (!menu.getCarried().isEmpty()) {
                send(ItemResource.EMPTY, left ? TerminalClickPayload.INSERT_CARRIED : TerminalClickPayload.INSERT_ONE);
            } else {
                Entry e = entryAt(event.x(), event.y());
                if (e != null) {
                    int action = Minecraft.getInstance().hasShiftDown() ? TerminalClickPayload.TAKE_TO_INVENTORY
                            : left ? TerminalClickPayload.TAKE_STACK : TerminalClickPayload.TAKE_HALF;
                    send(e.resource(), action);
                }
            }
            return true;
        }
        if (event.button() == 0 && onBar(event.x(), event.y())) {
            draggingBar = true;
            dragBar(event.y());
            return true;
        }
        if (search != null && event.button() == 1 && search.isMouseOver(event.x(), event.y())) {
            search.setValue("");
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingBar) {
            dragBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingBar) {
            draggingBar = false;
            return true;
        }
        if (gridPressed) {
            // The grid click was handled on press; don't let the container treat the release as a slot click.
            gridPressed = false;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inGrid(x, y) || onBar(x, y)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused()) {
            if (event.isEscape()) return super.keyPressed(event);
            search.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
}
