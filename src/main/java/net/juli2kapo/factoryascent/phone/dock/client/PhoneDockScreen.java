package net.juli2kapo.factoryascent.phone.dock.client;

import java.util.List;
import net.juli2kapo.factoryascent.capsule.client.DeviceScreen;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.juli2kapo.factoryascent.phone.dock.PhoneDockBlockEntity;
import net.juli2kapo.factoryascent.phone.dock.PhoneDockMenu;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Phone Dock: the phone cradle (with its charge), the card reader and the blank-card tray on the
 * left; on the right the phone's links, each with a remove button, and what the last card did.
 */
public class PhoneDockScreen extends DeviceScreen<PhoneDockMenu> {
    private static final int ACCENT = 0xFF3FA7B5;
    private static final int LX = 46, LY = 16, LW = 142, LH = 84, ROW = 11, ROWS = 6;
    private static final int[] KIND_COLOR = {0xFF3FA7B5, 0xFF4CD04C, 0xFFE0C040, 0xFFB070F0};
    private static final String[] KIND_LETTER = {"S", "M", "P", "B"};

    private int scroll;

    public PhoneDockScreen(PhoneDockMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PhoneDockMenu.WIDTH, PhoneDockMenu.HEIGHT, PhoneDockMenu.INV_X, PhoneDockMenu.INV_Y, ACCENT);
    }

    private ItemStack phone() {
        return menu.getSlot(PhoneDockBlockEntity.PHONE).getItem();
    }

    private List<PhoneMemory.Link> links() {
        ItemStack phone = phone();
        return phone.isEmpty() ? List.of() : PhoneMemory.of(phone).links();
    }

    private int maxScroll() {
        return Math.max(0, links().size() - ROWS);
    }

    @Override
    protected void layoutButtons() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        List<PhoneMemory.Link> links = links();
        for (int r = 0; r < ROWS && scroll + r < links.size(); r++) {
            buttons.add(new Btn(LX + LW - 13, LY + 3 + r * ROW, 10, 10, Component.literal("×"), PhoneDockMenu.B_REMOVE + scroll + r,
                    Component.translatable("gui.factoryascent.phone_dock.remove")));
        }
    }

    @Override
    protected void background(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        // cradle outline around the phone slot, a card reader slit, a tray
        g.fill(x + 13, y + 19, x + 39, y + 53, 0xFF2A3340);
        g.fill(x + 15, y + 21, x + 37, y + 51, 0xFF3A4656);
        FactoryGui.slot(g, x + 18, y + 30);
        int charge = menu.get(PhoneDockMenu.D_CHARGE);
        FactoryGui.bar(g, x + 15, y + 47, 22, 4, Math.max(0, charge) / 1000f, menu.get(PhoneDockMenu.D_CHARGING) != 0 ? 0xFF4CD04C : 0xFF3FA7B5);
        g.fill(x + 14, y + 61, x + 38, y + 62, 0xFF20262E);
        FactoryGui.display(g, x + LX, y + LY, LW, LH);
        float e = menu.capacity() <= 0 ? 0 : menu.energy() / (float) menu.capacity();
        // energy along the bottom of the list
        FactoryGui.energyBar(g, x + LX, y + LY + LH + 1, LW, 5, e);
        // down arrow between reader and tray
        g.fill(x + 25, y + 82, x + 29, y + 85, 0xFF6A7480);
    }

    @Override
    protected void labels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tx = LX + 4, w = LW - 20;
        List<PhoneMemory.Link> links = links();
        if (phone().isEmpty()) {
            int ly = LY + 4;
            String[] keys = {"no_phone", "how1", "how2", "how3"};
            for (int i = 0; i < keys.length; i++) {
                for (var line : font.split(Component.translatable("gui.factoryascent.phone_dock." + keys[i]), LW - 8)) {
                    if (ly > LY + LH - 10) break;
                    g.text(font, line, tx, ly, i == 0 ? FactoryGui.DISPLAY_TEXT : FactoryGui.DISPLAY_MUTED, false);
                    ly += 10;
                }
                if (i == 0) ly += 4;
            }
        } else if (links.isEmpty()) {
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.phone_dock.no_links"), LW - 8), tx, LY + 4,
                    FactoryGui.DISPLAY_MUTED, false);
        }
        for (int r = 0; r < ROWS && scroll + r < links.size(); r++) {
            PhoneMemory.Link l = links.get(scroll + r);
            int ry = LY + 4 + r * ROW;
            int kind = Math.max(0, Math.min(3, l.kind()));
            g.text(font, KIND_LETTER[kind], tx, ry, KIND_COLOR[kind], false);
            BlockPos p = l.pos().pos();
            String where = p.getX() + "," + p.getY() + "," + p.getZ();
            int ww = font.width(where);
            g.text(font, FactoryGui.fit(font, Component.translatable(l.block()), w - ww - 14), tx + 8, ry, FactoryGui.DISPLAY_TEXT, false);
            g.text(font, where, LX + LW - 16 - ww, ry, FactoryGui.DISPLAY_MUTED, false);
        }
        if (maxScroll() > 0) {
            g.text(font, (scroll + 1) + "-" + Math.min(links.size(), scroll + ROWS) + "/" + links.size(), tx, LY + LH - 20,
                    FactoryGui.DISPLAY_MUTED, false);
        }
        int result = menu.get(PhoneDockMenu.D_RESULT);
        if (result != PhoneDockBlockEntity.R_NONE) {
            int color = result == PhoneDockBlockEntity.R_ADDED ? FactoryGui.GOOD : FactoryGui.WARN;
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.phone_dock.result." + result), LW - 8), tx,
                    LY + LH - 10, color, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (FactoryGui.inside(mouseX - leftPos, mouseY - topPos, LX, LY, LW, LH)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (FactoryGui.inside(mx, my, LX, LY + LH + 1, LW, 5)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.literal(EnergyUtil.format(menu.energy()) + " / "
                    + EnergyUtil.format(menu.capacity()) + " FE")), mouseX, mouseY);
            return;
        }
        String[] keys = {"phone", "card_in", "card_out"};
        for (int i = 0; i < 3; i++) {
            int[] s = PhoneDockMenu.SLOTS[i];
            if (FactoryGui.inside(mx, my, s[0], s[1], 16, 16) && menu.getSlot(i).getItem().isEmpty() && menu.getCarried().isEmpty()) {
                g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.factoryascent.phone_dock.slot." + keys[i])),
                        mouseX, mouseY);
            }
        }
    }
}
