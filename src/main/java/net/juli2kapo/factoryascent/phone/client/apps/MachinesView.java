package net.juli2kapo.factoryascent.phone.client.apps;

import java.util.List;
import net.juli2kapo.factoryascent.phone.client.PhoneAppView;
import net.juli2kapo.factoryascent.phone.client.PhoneClient;
import net.juli2kapo.factoryascent.phone.client.PhoneUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Machines app: one card per watched machine with its status, progress/energy and reactor temperature. */
public final class MachinesView extends PhoneAppView {
    private static final int CARD_H = 36, GAP = 3;
    private int scroll;

    public MachinesView(String app) {
        super(app);
    }

    private List<CompoundTag> machines() {
        return PhoneClient.compounds(data, "machines");
    }

    @Override
    public Component subtitle() {
        return loaded ? Component.literal(machines().size() + "/" + data.getIntOr("max", 8)) : Component.empty();
    }

    @Override
    protected void dataChanged() {
        scroll = Math.min(scroll, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, machines().size() * (CARD_H + GAP) + 4 - H);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float partial) {
        List<CompoundTag> list = machines();
        if (list.isEmpty()) {
            PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.machines.none"), W / 2, 40, W - 8, PhoneUi.TEXT);
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.machines.how", data.getIntOr("max", 8),
                    data.getIntOr("range", 48)), 8, 56, W - 16, 10, PhoneUi.MUTED);
            return;
        }
        int y = 3 - scroll;
        for (CompoundTag m : list) {
            if (y + CARD_H >= 0 && y <= H) drawCard(g, m, 3, y, W - 6, mx, my);
            y += CARD_H + GAP;
        }
    }

    static void drawCard(GuiGraphicsExtractor g, CompoundTag m, int x, int y, int w, int mx, int my) {
        int level = m.getIntOr("level", 3);
        PhoneUi.card(g, x, y, w, CARD_H, PhoneUi.inside(mx, my, x, y, w, CARD_H));
        g.fill(x, y + 1, x + 2, y + CARD_H - 1, PhoneUi.levelColor(level));
        String item = m.getStringOr("item", "");
        if (!item.isEmpty()) {
            g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item))), x + 4, y + 3);
        } else {
            PhoneUi.round(g, x + 5, y + 4, 14, 14, 0xFF2A3346);
        }
        PhoneUi.text(g, Component.translatable(m.getStringOr("name", "")), x + 23, y + 3, w - 26, PhoneUi.TEXT);
        Component status = m.contains("arg") ? Component.translatable(m.getStringOr("status", ""), m.getIntOr("arg", 0))
                : Component.translatable(m.getStringOr("status", ""));
        if (m.contains("statusc")) status = decodeStatus(m, status);
        PhoneUi.dot(g, x + 23, y + 14, PhoneUi.levelColor(level));
        PhoneUi.text(g, status, x + 31, y + 13, w - 34, PhoneUi.levelColor(level) == PhoneUi.OFF ? PhoneUi.MUTED : PhoneUi.levelColor(level));
        int by = y + 25;
        if (m.contains("temp")) {
            float temp = m.getFloatOr("temp", 20f);
            int meltdown = Math.max(1, m.getIntOr("meltdown", 1000));
            int alarm = m.getIntOr("alarm", meltdown);
            float frac = temp / meltdown;
            int c = temp >= alarm ? PhoneUi.BAD : temp >= alarm * 0.75f ? PhoneUi.WARN : 0xFF40B0F0;
            PhoneUi.bar(g, x + 4, by, w - 50, 6, frac, c);
            PhoneUi.textRight(g, Component.literal(Math.round(temp) + "°C"), x + w - 3, by - 1, c);
        } else if (m.getIntOr("capacity", 0) > 0) {
            float frac = m.getIntOr("energy", 0) / (float) m.getIntOr("capacity", 1);
            int progress = m.getIntOr("progress", 0);
            if (progress > 0) {
                PhoneUi.bar(g, x + 4, by, (w - 50) / 2 - 1, 6, progress / 1000f, PhoneUi.GOOD);
                PhoneUi.bar(g, x + 4 + (w - 50) / 2 + 1, by, (w - 50) / 2 - 1, 6, frac, 0xFFE0A030);
            } else {
                PhoneUi.bar(g, x + 4, by, w - 50, 6, frac, m.getBooleanOr("fluid", false) ? 0xFF3F7FD0 : 0xFFE0A030);
            }
            if (m.getBooleanOr("fluid", false)) {
                PhoneUi.textRight(g, Component.literal(PhoneUi.compact(m.getIntOr("energy", 0)) + " mB"), x + w - 3, by - 1, PhoneUi.MUTED);
            } else {
                int rate = m.getIntOr("rate", 0);
                PhoneUi.textRight(g, Component.literal(PhoneUi.compact(rate) + " FE/t"), x + w - 3, by - 1, PhoneUi.MUTED);
            }
        } else {
            Component where = Component.translatable("gui.factoryascent.phone.at", m.getIntOr("x", 0), m.getIntOr("y", 0), m.getIntOr("z", 0));
            PhoneUi.text(g, where, x + 4, by - 1, w - 8, PhoneUi.FAINT);
        }
    }

    /** A device's own status line, sent as a serialized component. */
    static Component decodeStatus(CompoundTag m, Component fallback) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return fallback;
        return net.minecraft.network.chat.ComponentSerialization.CODEC
                .parse(mc.level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), m.get("statusc"))
                .result().map(c -> (Component) c).orElse(fallback);
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(amount) * 13));
        return true;
    }
}
