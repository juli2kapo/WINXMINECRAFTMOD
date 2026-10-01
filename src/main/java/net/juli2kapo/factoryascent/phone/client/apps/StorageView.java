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

/** Storage app: the linked network's status, fill level, top items, and "Open terminal". */
public final class StorageView extends PhoneAppView {
    private static final int BX = 8, BY = 150, BW = 116, BH = 18;
    private static final int COLS = 6, CELL = 21;

    public StorageView(String app) {
        super(app);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if (!data.getBooleanOr("linked", false)) {
            PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.storage.unlinked"), W / 2, 40, W - 8, PhoneUi.TEXT);
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.storage.how"), 8, 56, W - 16, 8, PhoneUi.MUTED);
            return;
        }
        PhoneUi.card(g, 4, 4, W - 8, 40, false);
        Component where = Component.translatable("gui.factoryascent.phone.at", data.getIntOr("x", 0), data.getIntOr("y", 0), data.getIntOr("z", 0));
        PhoneUi.text(g, where, 8, 8, W - 16, PhoneUi.MUTED);
        if (data.contains("problem")) {
            PhoneUi.dot(g, 8, 21, PhoneUi.OFF);
            PhoneUi.text(g, Component.translatable(data.getStringOr("problem", "")), 16, 19, W - 24, PhoneUi.WARN);
            return;
        }
        boolean online = data.getBooleanOr("online", false);
        PhoneUi.dot(g, 8, 21, online ? PhoneUi.GOOD : PhoneUi.BAD);
        PhoneUi.text(g, Component.translatable(data.getStringOr("status", "")), 16, 19, W - 24, online ? PhoneUi.TEXT : PhoneUi.BAD);
        long used = data.getLongOr("used", 0), cap = data.getLongOr("capacity", 0);
        float fill = cap <= 0 ? 0f : used / (float) cap;
        PhoneUi.bar(g, 8, 31, W - 16, 5, fill, fill > 0.9f ? PhoneUi.BAD : fill > 0.7f ? PhoneUi.WARN : 0xFF2DB0C0);
        Component amount = Component.translatable("gui.factoryascent.phone.storage.items", PhoneUi.compact(used), PhoneUi.compact(cap));
        PhoneUi.text(g, amount, 6, 47, W - 10, PhoneUi.MUTED);
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.storage.types", data.getIntOr("types", 0),
                data.getIntOr("maxTypes", 0)), 6, 57, W - 10, PhoneUi.MUTED);
        List<CompoundTag> items = PhoneClient.compounds(data, "items");
        int gx = (W - COLS * CELL) / 2;
        for (int i = 0; i < items.size(); i++) {
            int x = gx + (i % COLS) * CELL, y = 70 + (i / COLS) * CELL;
            g.fill(x, y, x + CELL - 1, y + CELL - 1, PhoneUi.CARD);
            CompoundTag it = items.get(i);
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(it.getStringOr("id", "minecraft:air")));
            ItemStack stack = new ItemStack(item);
            g.item(stack, x + 2, y + 1);
            String count = PhoneUi.compact(it.getLongOr("count", 0));
            g.pose().pushMatrix();
            g.pose().translate(x + CELL - 1, y + CELL - 6);
            g.pose().scale(0.5f, 0.5f);
            g.text(PhoneUi.font(), count, -PhoneUi.font().width(count), 0, 0xFFFFFFFF, true);
            g.pose().popMatrix();
        }
        if (items.isEmpty()) PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.storage.empty"), W / 2, 100, W - 8, PhoneUi.FAINT);
        PhoneUi.button(g, BX, BY, BW, BH, Component.translatable("gui.factoryascent.phone.storage.open"), 0xFF2D8C9A,
                PhoneUi.inside(mx, my, BX, BY, BW, BH), online);
    }

    @Override
    public boolean click(double mx, double my, int button) {
        if (PhoneUi.inside(mx, my, BX, BY, BW, BH) && data.getBooleanOr("online", false)) {
            sendLeaving("terminal");
            return true;
        }
        return false;
    }
}
