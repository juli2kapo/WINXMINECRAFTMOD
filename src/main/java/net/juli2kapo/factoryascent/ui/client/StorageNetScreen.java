package net.juli2kapo.factoryascent.ui.client;

import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.storagenet.StorageFormat;
import net.juli2kapo.factoryascent.storagenet.StorageNet;
import net.juli2kapo.factoryascent.ui.ScreenPayloads.StorageView;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Storage network (from a cable, the controller or an interface): online status, the
 * controller's energy and drain, item and type usage, the device count, and the most stored items.
 */
public class StorageNetScreen extends NetScreen {
    private static final int W = 220, H = 184;
    private static final int GRID_X = 8, GRID_Y = 118, COLS = 11;

    private StorageView view;

    public StorageNetScreen(StorageView view, Component title) {
        super(title);
        this.view = view;
    }

    @Override
    BlockPos pos() {
        return view.pos();
    }

    public void update(StorageView view) {
        this.view = view;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private StorageNet.Status status() {
        StorageNet.Status[] all = StorageNet.Status.values();
        return view.status() >= 0 && view.status() < all.length ? all[view.status()] : StorageNet.Status.NO_CONTROLLER;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + W - 60, top() + H - 22, 52, 14).build());
    }

    private static float frac(long a, long b) {
        return b <= 0 ? 0 : Math.min(1f, a / (float) b);
    }

    private static int usageColor(float f) {
        return f < 0.75f ? 0xFF3FD13F : f < 1f ? 0xFFE0C040 : 0xFFD03030;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        FactoryGui.panel(g, x, y, W, H, FactoryGui.STORAGE);
        FactoryGui.display(g, x + 8, y + 18, W - 16, 16);
        FactoryGui.energyBar(g, x + 8, y + 48, W - 16, 8, frac(view.energy(), view.capacity()));
        float items = frac(view.used(), view.capacityItems()), types = frac(view.types(), view.typeCapacity());
        FactoryGui.bar(g, x + 8, y + 70, (W - 20) / 2, 7, items, usageColor(items));
        FactoryGui.bar(g, x + 12 + (W - 20) / 2, y + 70, (W - 20) / 2, 7, types, usageColor(types));
        for (int i = 0; i < 2 * COLS; i++) {
            FactoryGui.slot(g, x + GRID_X + 1 + (i % COLS) * 18, y + GRID_Y + (i / COLS) * 18);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = left(), y = top();
        g.text(font, title, x + 8, y + 7, FactoryGui.TEXT, false);
        StorageNet.Status status = status();
        boolean online = status == StorageNet.Status.ONLINE;
        int color = online ? FactoryGui.GOOD : FactoryGui.BAD;
        FactoryGui.lamp(g, x + 12, y + 22, color);
        g.text(font, Component.translatable(status.key()), x + 23, y + 22, color, false);
        Component devices = Component.translatable("gui.factoryascent.storage.net_devices", view.devices(), view.drives(),
                Math.max(0, view.members() - view.devices() - 1));
        g.text(font, FactoryGui.fit(font, devices, W - 110), x + W - 12 - Math.min(W - 110, font.width(devices)), y + 22,
                FactoryGui.DISPLAY_MUTED, false);
        FactoryGui.row(g, font, Component.translatable("gui.factoryascent.storage.net_energy"),
                Component.translatable("gui.factoryascent.storage.net_energy_value", EnergyUtil.format(view.energy()),
                        EnergyUtil.format(view.capacity()), view.drain()), x + 8, x + W - 8, y + 38, FactoryGui.TEXT, FactoryGui.TEXT);
        g.text(font, Component.translatable("gui.factoryascent.storage.net_items", StorageFormat.compact(view.used()),
                StorageFormat.compact(view.capacityItems())), x + 8, y + 60, FactoryGui.TEXT, false);
        g.text(font, Component.translatable("gui.factoryascent.storage.net_types", view.types(), view.typeCapacity()),
                x + 12 + (W - 20) / 2, y + 60, FactoryGui.TEXT, false);
        Component hint = online ? Component.translatable("gui.factoryascent.storage.net_hint")
                : Component.translatable("gui.factoryascent.storage.net_fix." + status.name().toLowerCase(java.util.Locale.ROOT));
        g.textWithWordWrap(font, hint, x + 8, y + 82, W - 16, online ? FactoryGui.MUTED : 0xFFA03030, false);
        g.text(font, Component.translatable("gui.factoryascent.storage.net_top"), x + 8, y + GRID_Y - 10, FactoryGui.TEXT, false);
        for (int i = 0; i < view.top().size() && i < 2 * COLS; i++) {
            int ix = x + GRID_X + 1 + (i % COLS) * 18, iy = y + GRID_Y + (i / COLS) * 18;
            ItemStack stack = view.top().get(i);
            g.item(stack, ix, iy);
            String count = StorageFormat.compact(view.counts().get(i));
            g.pose().pushMatrix();
            g.pose().translate(ix + 16, iy + 16);
            g.pose().scale(0.6f, 0.6f);
            g.text(font, count, -font.width(count), -8, 0xFFFFFFFF, true);
            g.pose().popMatrix();
        }
        if (view.top().isEmpty()) {
            g.text(font, Component.translatable("gui.factoryascent.storage.empty"), x + GRID_X + 4, y + GRID_Y + 4, FactoryGui.MUTED, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
        for (int i = 0; i < view.top().size() && i < 2 * COLS; i++) {
            int ix = x + GRID_X + 1 + (i % COLS) * 18, iy = y + GRID_Y + (i / COLS) * 18;
            if (FactoryGui.inside(mouseX, mouseY, ix, iy, 16, 16)) {
                g.setComponentTooltipForNextFrame(font, List.of(view.top().get(i).getHoverName(),
                        Component.translatable("gui.factoryascent.storage.stored", String.format("%,d", view.counts().get(i)))
                                .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            }
        }
    }
}
