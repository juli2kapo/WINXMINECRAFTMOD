package net.juli2kapo.factoryascent.phone.client.apps;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.phone.client.PhoneAppView;
import net.juli2kapo.factoryascent.phone.client.PhoneUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Recall app: every Ender Beacon the phone knows, one card at a time. Swipe the card (drag it
 * sideways) or use the arrows to switch; the Recall button channels a recall to the one shown.
 */
public final class RecallView extends PhoneAppView {
    private static final int BX = 8, BY = 150, BW = 116, BH = 18;
    /** The beacon card and its arrows. */
    private static final int CX = 16, CY = 50, CW = 100, CH = 58;
    private static final int LX = 2, RX = W - 14, AY = CY + 18, AW = 12, AH = 20;
    /** Pixels a drag must cover to count as a swipe. */
    private static final int SWIPE = 18;

    private final List<CompoundTag> beacons = new ArrayList<>();
    private int selected;
    /** A drag in progress: where it started (x) and how far it went, or NaN for none. */
    private double dragStart = Double.NaN, dragOffset;

    public RecallView(String app) {
        super(app);
    }

    @Override
    protected void dataChanged() {
        beacons.clear();
        for (Tag t : data.getListOrEmpty("beacons")) {
            if (t instanceof CompoundTag c) beacons.add(c);
        }
        // While channelling, show the beacon the channel goes to.
        int[] target = data.getIntArray("channelTarget").orElse(null);
        if (target != null && target.length == 3) {
            String dim = data.getStringOr("channelDim", "");
            for (int i = 0; i < beacons.size(); i++) {
                CompoundTag b = beacons.get(i);
                if (b.getIntOr("x", 0) == target[0] && b.getIntOr("y", 0) == target[1] && b.getIntOr("z", 0) == target[2]
                        && b.getStringOr("dim", "").equals(dim)) selected = i;
            }
        }
        selected = beacons.isEmpty() ? 0 : Mth.clamp(selected, 0, beacons.size() - 1);
    }

    @Override
    public Component subtitle() {
        return beacons.isEmpty() ? Component.empty() : Component.literal((selected + 1) + "/" + beacons.size());
    }

    private boolean channelling() {
        return data.contains("channel");
    }

    private CompoundTag current() {
        return beacons.isEmpty() ? new CompoundTag() : beacons.get(selected);
    }

    private boolean canRecall() {
        CompoundTag b = current();
        return !beacons.isEmpty() && b.getBooleanOr("reachable", false) && data.getIntOr("cooldown", 0) <= 0
                && !"gone".equals(b.getStringOr("state", ""));
    }

    private void select(int index) {
        if (beacons.isEmpty() || channelling()) return;
        selected = Math.floorMod(index, beacons.size());
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float partial) {
        // a swirling portal
        int cx = W / 2, cy = 22;
        long t = System.currentTimeMillis();
        float speed = channelling() ? 4f : 1f;
        for (int i = 0; i < 22; i++) {
            float a = i * 0.9f + (t % 100000) / 1000f * speed;
            float r = 5 + (i * 37 % 15);
            int px = cx + Math.round(Mth.cos(a) * r), py = cy + Math.round(Mth.sin(a) * r * 0.7f);
            g.fill(px, py, px + 2, py + 2, i % 3 == 0 ? 0xFFE0A0FF : i % 3 == 1 ? 0xFF9A50E0 : 0xFF5A2A9A);
        }
        PhoneUi.round2(g, cx - 4, cy - 4, 8, 8, 0xFF1A0A2A);
        if (beacons.isEmpty()) {
            PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.recall.none"), W / 2, 50, W - 8, PhoneUi.TEXT);
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.recall.how_link"), 8, 64, W - 16, 9, PhoneUi.MUTED);
            return;
        }
        CompoundTag b = current();
        // the card, sliding with the finger while dragged
        int off = Double.isNaN(dragStart) ? 0 : (int) Mth.clamp(dragOffset, -40, 40);
        PhoneUi.card(g, CX + off, CY, CW, CH, PhoneUi.inside(mx, my, CX, CY, CW, CH));
        String name = b.getStringOr("name", "");
        Component title = name.isEmpty() ? Component.translatable("gui.factoryascent.phone.recall.beacon") : Component.literal(name);
        PhoneUi.centered(g, title, W / 2 + off, CY + 4, CW - 6, 0xFFE0B8FF);
        PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.at", b.getIntOr("x", 0), b.getIntOr("y", 0),
                b.getIntOr("z", 0)), W / 2 + off, CY + 15, CW - 6, PhoneUi.MUTED);
        String dim = b.getStringOr("dim", "minecraft:overworld");
        PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.recall.dim", Identifier.parse(dim).getPath()),
                W / 2 + off, CY + 25, CW - 6, PhoneUi.FAINT);
        String state = b.getStringOr("state", "unknown");
        int sc = switch (state) {
            case "ready" -> PhoneUi.GOOD;
            case "no_pearl" -> PhoneUi.WARN;
            case "gone" -> PhoneUi.BAD;
            default -> PhoneUi.OFF;
        };
        PhoneUi.dot(g, CX + 5 + off, CY + 39, sc);
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.recall.state." + state), CX + 12 + off, CY + 37, CW - 16, sc);
        if (b.getBooleanOr("charm", false)) {
            PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.recall.from_charm"), CX + 5 + off, CY + 47, CW - 10, PhoneUi.FAINT);
        }
        // arrows and page dots
        if (beacons.size() > 1) {
            boolean lock = channelling();
            PhoneUi.button(g, LX, AY, AW, AH, Component.literal("<"), 0xFF5A3A8A, PhoneUi.inside(mx, my, LX, AY, AW, AH), !lock);
            PhoneUi.button(g, RX, AY, AW, AH, Component.literal(">"), 0xFF5A3A8A, PhoneUi.inside(mx, my, RX, AY, AW, AH), !lock);
            int n = Math.min(beacons.size(), 16);
            int dx = W / 2 - n * 3;
            for (int i = 0; i < n; i++) {
                g.fill(dx + i * 6, CY + CH + 4, dx + i * 6 + 3, CY + CH + 7, i == selected ? 0xFFE0B8FF : 0xFF4A3A60);
            }
        }
        int ly = CY + CH + 12;
        if (!b.getBooleanOr("reachable", false)) {
            PhoneUi.wrapped(g, Component.translatable("message.factoryascent.charm_other_dimension"), 8, ly, W - 16, 2, PhoneUi.BAD);
        } else if ("gone".equals(state)) {
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.recall.gone_hint"), 8, ly, W - 16, 2, PhoneUi.BAD);
        } else if (data.getIntOr("cooldown", 0) > 0) {
            PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.recall.cooling", data.getIntOr("cooldown", 0)), 8, ly, W - 16, PhoneUi.WARN);
        } else {
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.recall.rule", data.getIntOr("seconds", 5)), 8, ly, W - 16, 2, PhoneUi.MUTED);
        }
        if (channelling()) {
            float p = data.getIntOr("channel", 0) / (float) Math.max(1, data.getIntOr("channelTotal", 1));
            PhoneUi.bar(g, 10, BY - 9, W - 20, 5, p, 0xFFB070F0);
            PhoneUi.button(g, BX, BY, BW, BH, Component.translatable("gui.factoryascent.phone.recall.cancel"), 0xFF6A4A8A,
                    PhoneUi.inside(mx, my, BX, BY, BW, BH), true);
        } else {
            PhoneUi.button(g, BX, BY, BW, BH, Component.translatable("gui.factoryascent.phone.recall.go"), 0xFF8E4FD6,
                    PhoneUi.inside(mx, my, BX, BY, BW, BH), canRecall());
        }
    }

    @Override
    public boolean click(double mx, double my, int button) {
        if (beacons.size() > 1 && PhoneUi.inside(mx, my, LX, AY, AW, AH)) {
            select(selected - 1);
            return true;
        }
        if (beacons.size() > 1 && PhoneUi.inside(mx, my, RX, AY, AW, AH)) {
            select(selected + 1);
            return true;
        }
        if (PhoneUi.inside(mx, my, CX, CY, CW, CH) && beacons.size() > 1) {
            dragStart = mx;
            dragOffset = 0;
            return false;
        }
        if (!PhoneUi.inside(mx, my, BX, BY, BW, BH)) return false;
        if (channelling()) {
            send("cancel");
            return true;
        }
        if (canRecall()) {
            CompoundTag b = current();
            CompoundTag args = new CompoundTag();
            args.putString("dim", b.getStringOr("dim", ""));
            args.putInt("x", b.getIntOr("x", 0));
            args.putInt("y", b.getIntOr("y", 0));
            args.putInt("z", b.getIntOr("z", 0));
            send("recall", args);
            return true;
        }
        return false;
    }

    @Override
    public boolean drag(double mx, double my, int button) {
        if (Double.isNaN(dragStart)) return false;
        dragOffset = mx - dragStart;
        return true;
    }

    @Override
    public boolean release(double mx, double my, int button) {
        if (Double.isNaN(dragStart)) return false;
        double moved = mx - dragStart;
        dragStart = Double.NaN;
        dragOffset = 0;
        if (Math.abs(moved) < SWIPE) return false;
        // swipe left shows the next beacon, swipe right the previous one
        select(moved < 0 ? selected + 1 : selected - 1);
        return true;
    }
}
