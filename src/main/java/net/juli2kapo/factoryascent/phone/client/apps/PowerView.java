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

/** Power app: one card per linked energy network: stored energy, average in/out and the cable rate. */
public final class PowerView extends PhoneAppView {
    private static final int CARD_H = 48, GAP = 3;
    private int scroll;

    public PowerView(String app) {
        super(app);
    }

    private List<CompoundTag> networks() {
        return PhoneClient.compounds(data, "networks");
    }

    @Override
    public Component subtitle() {
        return loaded ? Component.literal(networks().size() + "/" + data.getIntOr("max", 4)) : Component.empty();
    }

    private int maxScroll() {
        return Math.max(0, networks().size() * (CARD_H + GAP) + 4 - H);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float partial) {
        List<CompoundTag> list = networks();
        if (list.isEmpty()) {
            PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.power.none"), W / 2, 40, W - 8, PhoneUi.TEXT);
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.power.how", data.getIntOr("max", 4)), 8, 56, W - 16, 10, PhoneUi.MUTED);
            return;
        }
        // totals over every network in reach
        long in = 0, out = 0;
        for (CompoundTag n : list) {
            in += n.getLongOr("in", 0);
            out += n.getLongOr("out", 0);
        }
        int y = 3 - scroll;
        for (CompoundTag n : list) {
            if (y + CARD_H >= 0 && y <= H) drawCard(g, n, 3, y, W - 6);
            y += CARD_H + GAP;
        }
        if (list.size() > 1 && maxScroll() == 0) {
            PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.power.total", PhoneUi.compact(in), PhoneUi.compact(out)),
                    4, H - 11, W - 8, PhoneUi.MUTED);
        }
    }

    private static void drawCard(GuiGraphicsExtractor g, CompoundTag n, int x, int y, int w) {
        int level = n.getIntOr("level", 3);
        PhoneUi.card(g, x, y, w, CARD_H, false);
        g.fill(x, y + 1, x + 2, y + CARD_H - 1, PhoneUi.levelColor(level));
        String item = n.getStringOr("item", "");
        if (!item.isEmpty()) g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item))), x + 4, y + 3);
        PhoneUi.text(g, Component.translatable(n.getStringOr("name", "")), x + 23, y + 3, w - 26, PhoneUi.TEXT);
        Component where = Component.translatable("gui.factoryascent.phone.at", n.getIntOr("x", 0), n.getIntOr("y", 0), n.getIntOr("z", 0));
        if (n.getBooleanOr("offline", false)) {
            PhoneUi.text(g, where, x + 23, y + 13, w - 26, PhoneUi.FAINT);
            PhoneUi.dot(g, x + 4, y + 27, PhoneUi.OFF);
            PhoneUi.text(g, Component.translatable(n.getStringOr("status", "")), x + 12, y + 26, w - 16, PhoneUi.WARN);
            return;
        }
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.power.cables", n.getIntOr("cables", 0),
                PhoneUi.compact(n.getLongOr("rate", 0))), x + 23, y + 13, w - 26, PhoneUi.FAINT);
        long energy = n.getLongOr("energy", 0), cap = Math.max(1, n.getLongOr("capacity", 1));
        PhoneUi.bar(g, x + 4, y + 24, w - 8, 6, energy / (float) cap, 0xFFE0B020);
        PhoneUi.text(g, Component.literal(PhoneUi.compact(energy) + " / " + PhoneUi.compact(cap) + " FE"), x + 4, y + 33, w - 8, PhoneUi.MUTED);
        long in = n.getLongOr("in", 0), out = n.getLongOr("out", 0);
        Component flow = Component.literal("▲" + PhoneUi.compact(in)).withColor(PhoneUi.GOOD)
                .append(Component.literal(" ▼" + PhoneUi.compact(out)).withColor(0xFFF09040));
        PhoneUi.textRight(g, flow, x + w - 4, y + 33, PhoneUi.TEXT);
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(amount) * 13));
        return true;
    }
}
