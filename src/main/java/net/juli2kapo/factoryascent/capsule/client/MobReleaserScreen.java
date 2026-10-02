package net.juli2kapo.factoryascent.capsule.client;

import net.juli2kapo.factoryascent.capsule.MobReleaserMenu;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Mob Releaser: nine capsule slots, the release point (distance ahead, height up) and Release All. */
public class MobReleaserScreen extends DeviceScreen<MobReleaserMenu> {
    private static final int ACCENT = 0xFFC050D0;
    private static final int DX = 72, DY = 16, DW = 96, DH = 30;

    public MobReleaserScreen(MobReleaserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MobReleaserMenu.WIDTH, MobReleaserMenu.HEIGHT, MobReleaserMenu.INV_Y, ACCENT);
    }

    @Override
    protected void layoutButtons() {
        int bx = DX + DW - 30;
        buttons.add(new Btn(bx, 49, 14, 12, Component.literal("−"), MobReleaserMenu.B_NEARER,
                Component.translatable("gui.factoryascent.mob_releaser.nearer")));
        buttons.add(new Btn(bx + 16, 49, 14, 12, Component.literal("+"), MobReleaserMenu.B_FARTHER,
                Component.translatable("gui.factoryascent.mob_releaser.farther")));
        buttons.add(new Btn(bx, 63, 14, 12, Component.literal("−"), MobReleaserMenu.B_DOWN,
                Component.translatable("gui.factoryascent.mob_releaser.lower")));
        buttons.add(new Btn(bx + 16, 63, 14, 12, Component.literal("+"), MobReleaserMenu.B_UP,
                Component.translatable("gui.factoryascent.mob_releaser.higher")));
        buttons.add(new Btn(DX, 78, DW, 14, Component.translatable("gui.factoryascent.mob_releaser.release"), MobReleaserMenu.B_RELEASE,
                Component.translatable("gui.factoryascent.mob_releaser.release_tip")));
    }

    @Override
    protected boolean enabled(Btn b) {
        return switch (b.id()) {
            case MobReleaserMenu.B_RELEASE -> menu.get(MobReleaserMenu.D_FILLED) > 0 && menu.energy() >= menu.costPerMob();
            case MobReleaserMenu.B_NEARER -> menu.get(MobReleaserMenu.D_DISTANCE) > 1;
            case MobReleaserMenu.B_FARTHER -> menu.get(MobReleaserMenu.D_DISTANCE) < menu.get(MobReleaserMenu.D_MAX_DISTANCE);
            case MobReleaserMenu.B_DOWN -> menu.get(MobReleaserMenu.D_HEIGHT) > 0;
            default -> menu.get(MobReleaserMenu.D_HEIGHT) < 16;
        };
    }

    @Override
    protected void background(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        FactoryGui.display(g, x + DX, y + DY, DW, DH);
        // a launch arrow from the grid to the read-out
        int ax = x + 66, ay = y + 50;
        g.fill(ax - 2, ay - 1, ax + 3, ay + 2, 0xFF8A5A9A);
        g.fill(ax + 3, ay - 3, ax + 4, ay + 4, 0xFF8A5A9A);
        float e = menu.capacity() <= 0 ? 0 : menu.energy() / (float) menu.capacity();
        FactoryGui.energyBar(g, x + DX, y + 95, DW, 5, e);
    }

    @Override
    protected void labels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tx = DX + 4, w = DW - 8;
        int filled = menu.get(MobReleaserMenu.D_FILLED);
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.mob_releaser.loaded", filled, 9), w), tx, DY + 4,
                filled > 0 ? 0xFFE0B0F0 : FactoryGui.DISPLAY_MUTED, false);
        int last = menu.get(MobReleaserMenu.D_LAST);
        boolean starved = menu.get(MobReleaserMenu.D_NO_POWER) != 0 || (filled > 0 && menu.energy() < menu.costPerMob());
        Component status = starved ? Component.translatable("gui.factoryascent.mob_releaser.no_power",
                net.juli2kapo.factoryascent.util.EnergyUtil.format(menu.costPerMob()))
                : last < 0 ? Component.translatable("gui.factoryascent.mob_releaser.redstone")
                : Component.translatable("gui.factoryascent.mob_releaser.last", last);
        g.text(font, FactoryGui.fit(font, status, w), tx, DY + 16, starved ? FactoryGui.BAD : FactoryGui.DISPLAY_MUTED, false);
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.mob_releaser.ahead", menu.get(MobReleaserMenu.D_DISTANCE)), DW - 34),
                DX, 51, FactoryGui.TEXT, false);
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.mob_releaser.up", menu.get(MobReleaserMenu.D_HEIGHT)), DW - 34),
                DX, 65, FactoryGui.TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (FactoryGui.inside(mouseX - leftPos, mouseY - topPos, DX, 95, DW, 5)) {
            g.setComponentTooltipForNextFrame(font, java.util.List.of(
                    Component.literal(net.juli2kapo.factoryascent.util.EnergyUtil.format(menu.energy()) + " / "
                            + net.juli2kapo.factoryascent.util.EnergyUtil.format(menu.capacity()) + " FE"),
                    Component.translatable("gui.factoryascent.mob_releaser.cost",
                            net.juli2kapo.factoryascent.util.EnergyUtil.format(menu.costPerMob()))), mouseX, mouseY);
        }
    }
}
