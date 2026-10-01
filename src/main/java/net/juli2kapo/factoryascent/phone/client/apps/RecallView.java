package net.juli2kapo.factoryascent.phone.client.apps;

import net.juli2kapo.factoryascent.phone.client.PhoneAppView;
import net.juli2kapo.factoryascent.phone.client.PhoneUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Recall app: the Recall Charm's Ender Beacon and a Recall button that starts the charm's channel. */
public final class RecallView extends PhoneAppView {
    private static final int BX = 8, BY = 146, BW = 116, BH = 18;

    public RecallView(String app) {
        super(app);
    }

    private boolean channelling() {
        return data.contains("channel");
    }

    private boolean canRecall() {
        return data.getBooleanOr("linked", false) && data.getBooleanOr("reachable", false)
                && data.getIntOr("cooldown", 0) <= 0 && !"gone".equals(data.getStringOr("state", ""));
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float partial) {
        // a swirling portal
        int cx = W / 2, cy = 34;
        long t = System.currentTimeMillis();
        float speed = channelling() ? 4f : 1f;
        for (int i = 0; i < 22; i++) {
            float a = i * 0.9f + (t % 100000) / 1000f * speed;
            float r = 6 + (i * 37 % 19);
            int px = cx + Math.round(Mth.cos(a) * r), py = cy + Math.round(Mth.sin(a) * r * 0.8f);
            g.fill(px, py, px + 2, py + 2, i % 3 == 0 ? 0xFFE0A0FF : i % 3 == 1 ? 0xFF9A50E0 : 0xFF5A2A9A);
        }
        PhoneUi.round2(g, cx - 4, cy - 4, 8, 8, 0xFF1A0A2A);
        if (!data.getBooleanOr("charm", false)) {
            PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.recall.no_charm"), W / 2, 66, W - 8, PhoneUi.TEXT);
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.recall.how"), 8, 80, W - 16, 7, PhoneUi.MUTED);
            return;
        }
        if (!data.getBooleanOr("linked", false)) {
            PhoneUi.centered(g, Component.translatable("gui.factoryascent.charm.not_linked"), W / 2, 66, W - 8, PhoneUi.TEXT);
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.charm.how_to_link"), 8, 80, W - 16, 7, PhoneUi.MUTED);
            return;
        }
        String name = data.getStringOr("name", "");
        Component title = name.isEmpty() ? Component.translatable("gui.factoryascent.phone.recall.beacon") : Component.literal(name);
        PhoneUi.centered(g, title, W / 2, 62, W - 8, 0xFFE0B8FF);
        PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.at", data.getIntOr("x", 0), data.getIntOr("y", 0),
                data.getIntOr("z", 0)), W / 2, 74, W - 8, PhoneUi.MUTED);
        String dim = data.getStringOr("dim", "minecraft:overworld");
        PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.recall.dim", Identifier.parse(dim).getPath()), W / 2, 84, W - 8, PhoneUi.FAINT);
        String state = data.getStringOr("state", "unknown");
        int sc = switch (state) {
            case "ready" -> PhoneUi.GOOD;
            case "no_pearl" -> PhoneUi.WARN;
            case "gone" -> PhoneUi.BAD;
            default -> PhoneUi.OFF;
        };
        PhoneUi.card(g, 6, 98, W - 12, 40, false);
        PhoneUi.dot(g, 10, 104, sc);
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.recall.state." + state), 18, 102, W - 26, sc);
        if (!data.getBooleanOr("reachable", false)) {
            PhoneUi.text(g, Component.translatable("message.factoryascent.charm_other_dimension"), 10, 114, W - 20, PhoneUi.BAD);
        } else if (data.getIntOr("cooldown", 0) > 0) {
            PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.recall.cooling", data.getIntOr("cooldown", 0)), 10, 114, W - 20, PhoneUi.WARN);
        } else {
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.recall.rule", data.getIntOr("seconds", 5)), 10, 114, W - 20, 2, PhoneUi.MUTED);
        }
        if (channelling()) {
            float p = data.getIntOr("channel", 0) / (float) Math.max(1, data.getIntOr("channelTotal", 1));
            PhoneUi.bar(g, 10, 126, W - 20, 6, p, 0xFFB070F0);
            PhoneUi.button(g, BX, BY, BW, BH, Component.translatable("gui.factoryascent.phone.recall.cancel"), 0xFF6A4A8A,
                    PhoneUi.inside(mx, my, BX, BY, BW, BH), true);
        } else {
            PhoneUi.button(g, BX, BY, BW, BH, Component.translatable("gui.factoryascent.phone.recall.go"), 0xFF8E4FD6,
                    PhoneUi.inside(mx, my, BX, BY, BW, BH), canRecall());
        }
    }

    @Override
    public boolean click(double mx, double my, int button) {
        if (!PhoneUi.inside(mx, my, BX, BY, BW, BH)) return false;
        if (channelling()) {
            send("cancel");
            return true;
        }
        if (canRecall()) {
            send("recall");
            return true;
        }
        return false;
    }
}
