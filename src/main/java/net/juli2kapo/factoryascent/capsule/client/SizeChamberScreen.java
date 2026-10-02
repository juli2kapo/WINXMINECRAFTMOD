package net.juli2kapo.factoryascent.capsule.client;

import java.util.Locale;
import net.juli2kapo.factoryascent.capsule.SizeChamberBlockEntity;
import net.juli2kapo.factoryascent.capsule.SizeChamberMenu;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.mobs.CapturedMob;
import net.juli2kapo.factoryascent.mobs.MobCapsuleItem;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Size Chamber: a glass tube with the capsule slot and a silhouette of the mob at its stored size,
 * the target size with ÷2 / − / 1× / + / ×2 buttons, the mob's health, status and energy.
 */
public class SizeChamberScreen extends DeviceScreen<SizeChamberMenu> {
    private static final int ACCENT = 0xFF35B8C8;
    private static final int DX = 52, DY = 16, DW = 116, DH = 56;

    public SizeChamberScreen(SizeChamberMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SizeChamberMenu.WIDTH, SizeChamberMenu.HEIGHT, SizeChamberMenu.INV_Y, ACCENT);
    }

    private static String pct(int permille) {
        return permille % 10 == 0 ? (permille / 10) + "%" : String.format(Locale.ROOT, "%.1f%%", permille / 10f);
    }

    @Override
    protected void layoutButtons() {
        String[] labels = {"÷2", "−", "1×", "+", "×2"};
        String[] tips = {"half", "less", "reset", "more", "double"};
        for (int i = 0; i < 5; i++) {
            buttons.add(new Btn(DX + i * 23 + 1, 80, 21, 14, Component.literal(labels[i]), i,
                    Component.translatable("gui.factoryascent.size_chamber.btn." + tips[i])));
        }
    }

    @Override
    protected boolean enabled(Btn b) {
        int t = menu.get(SizeChamberMenu.D_TARGET);
        return SizeChamberMenu.stepped(t, b.id(), menu.get(SizeChamberMenu.D_MIN), menu.get(SizeChamberMenu.D_MAX)) != t;
    }

    private CapturedMob mob() {
        return MobCapsuleItem.captured(menu.getSlot(0).getItem());
    }

    @Override
    protected void background(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        // the tube: glass walls, a base and a cap, the mob's silhouette at its stored size
        int tx = x + 10, ty = y + 16, tw = 38, th = 80;
        g.fill(tx, ty, tx + tw, ty + th, 0xFF1A2A30);
        g.fill(tx + 2, ty + 2, tx + tw - 2, ty + th - 2, 0xFF22404A);
        g.fill(tx + 3, ty + 3, tx + 5, ty + th - 3, 0x5590E0F0);
        g.fill(tx, ty, tx + tw, ty + 4, 0xFF5A6A70);
        g.fill(tx, ty + th - 6, tx + tw, ty + th, 0xFF5A6A70);
        FactoryGui.slot(g, x + SizeChamberMenu.SLOT_X, y + SizeChamberMenu.SLOT_Y);
        CapturedMob mob = mob();
        if (mob != null) {
            int size = menu.get(SizeChamberMenu.D_SIZE);
            int target = menu.get(SizeChamberMenu.D_TARGET);
            int status = menu.get(SizeChamberMenu.D_STATUS);
            float shown = size / 1000f;
            if (status == SizeChamberBlockEntity.ST_RESIZING) {
                float p = menu.get(SizeChamberMenu.D_PROGRESS) / 1000f;
                shown = shown + (target / 1000f - shown) * p;
            }
            // silhouette: 24 px tall at normal size, log-scaled so ×8 and ÷8 both fit
            float h = (float) (24 * Math.pow(2, Math.log(shown) / Math.log(2) * 0.45));
            int hh = Math.max(3, Math.min(40, Math.round(h))), ww = Math.max(2, hh / 2);
            int cx = tx + tw / 2, base = ty + th - 6;
            int color = status == SizeChamberBlockEntity.ST_HEALING ? 0xFFE05080 : 0xFF0A1418;
            g.fill(cx - ww / 2, base - hh, cx + ww / 2 + 1, base, color);
            g.fill(cx - ww / 3, base - hh - Math.max(2, hh / 4), cx + ww / 3 + 1, base - hh, color);
        }
        FactoryGui.display(g, x + DX, y + DY, DW, DH);
        float e = menu.capacity() <= 0 ? 0 : menu.energy() / (float) menu.capacity();
        FactoryGui.energyBar(g, x + DX, y + 73, DW, 5, e);
    }

    @Override
    protected void labels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tx = DX + 4, w = DW - 8, ly = DY + 4;
        CapturedMob mob = mob();
        if (mob == null) {
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.size_chamber.empty"), w), tx, ly, FactoryGui.DISPLAY_MUTED, false);
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.size_chamber.target", pct(menu.get(SizeChamberMenu.D_TARGET))), w),
                    tx, ly + 12, FactoryGui.DISPLAY_TEXT, false);
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.size_chamber.limits",
                    pct(menu.get(SizeChamberMenu.D_MIN)), pct(menu.get(SizeChamberMenu.D_MAX))), w), tx, ly + 24, FactoryGui.DISPLAY_MUTED, false);
            return;
        }
        g.text(font, FactoryGui.fit(font, mob.displayName(), w), tx, ly, 0xFFE0D0FF, false);
        int size = menu.get(SizeChamberMenu.D_SIZE), target = menu.get(SizeChamberMenu.D_TARGET);
        Component sz = size == target ? Component.translatable("gui.factoryascent.size_chamber.size", pct(size))
                : Component.translatable("gui.factoryascent.size_chamber.size_to", pct(size), pct(target));
        g.text(font, FactoryGui.fit(font, sz, w), tx, ly + 11, FactoryGui.DISPLAY_TEXT, false);
        float hp = menu.get(SizeChamberMenu.D_HEALTH) / 10f, max = Math.max(0.1f, menu.get(SizeChamberMenu.D_MAX_HEALTH) / 10f);
        FactoryGui.bar(g, tx, ly + 22, w, 6, hp / max, 0xFFD03050);
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.size_chamber.health",
                String.format(Locale.ROOT, "%.1f", hp), String.format(Locale.ROOT, "%.0f", max)), w), tx, ly + 30, FactoryGui.DISPLAY_MUTED, false);
        int status = menu.get(SizeChamberMenu.D_STATUS);
        int color = switch (status) {
            case SizeChamberBlockEntity.ST_RESIZING, SizeChamberBlockEntity.ST_HEALING -> FactoryGui.GOOD;
            case SizeChamberBlockEntity.ST_NO_POWER -> FactoryGui.BAD;
            default -> FactoryGui.DISPLAY_TEXT;
        };
        Component st = Component.translatable("gui.factoryascent.size_chamber.status." + status);
        if (status == SizeChamberBlockEntity.ST_RESIZING) {
            st = Component.translatable("gui.factoryascent.size_chamber.status.1_pct", menu.get(SizeChamberMenu.D_PROGRESS) / 10);
        }
        FactoryGui.lamp(g, tx, ly + 42, color);
        g.text(font, FactoryGui.fit(font, st, w - 10), tx + 10, ly + 42, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (FactoryGui.inside(mouseX - leftPos, mouseY - topPos, DX, 73, DW, 5)) {
            g.setComponentTooltipForNextFrame(font, java.util.List.of(Component.literal(EnergyUtil.format(menu.energy()) + " / "
                    + EnergyUtil.format(menu.capacity()) + " FE")), mouseX, mouseY);
        }
    }
}
