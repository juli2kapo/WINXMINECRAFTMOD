package net.juli2kapo.factoryascent.orbital.client;

import java.util.List;
import net.juli2kapo.factoryascent.orbital.AsatMissileItem;
import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.juli2kapo.factoryascent.orbital.LaunchControllerMenu;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.orbital.SatelliteItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Launch Controller's screen: payload slot with what it is (and a missile's target), fuel
 * slot with the tank gauge, the pad's readiness, a Launch button and the countdown / climb
 * progress during a launch. Dark console style of the other Orbital screens.
 */
public class LaunchControllerScreen extends AbstractContainerScreen<LaunchControllerMenu> {
    private static final int INFO_X = 44;
    private static final int GAUGE_X = 40, GAUGE_Y = 64, GAUGE_W = 6, GAUGE_H = 20;
    private Button launch;
    private Component message = Component.empty();
    private int messageTicks;

    public LaunchControllerScreen(LaunchControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LaunchControllerMenu.WIDTH, LaunchControllerMenu.HEIGHT);
        this.inventoryLabelY = LaunchControllerMenu.PLAYER_INV_Y - 11;
    }

    /** A refusal from the server after pressing Launch. */
    public void showMessage(Component message) {
        this.message = message;
        this.messageTicks = 100;
    }

    @Override
    protected void init() {
        super.init();
        launch = addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.pad.launch"), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, crew() // [space hook] Board a crew capsule
                        ? LaunchControllerMenu.BUTTON_BOARD : LaunchControllerMenu.BUTTON_LAUNCH);
            }
        }).bounds(leftPos + imageWidth - 64, topPos + 80, 56, 16).build());
        launch.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.pad.launch_tip")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (messageTicks > 0) messageTicks--;
        if (launch != null) launch.active = menu.status() == LaunchControllerBlockEntity.STATUS_READY
                || menu.status() == LaunchControllerBlockEntity.STATUS_MISSILE;
        // [space hook] with a Crew Capsule mounted the button boards it (and warns without a suit)
        int mode = !crew() ? 0 : unsuited() ? 2 : 1;
        if (launch != null && mode != buttonMode) {
            buttonMode = mode;
            launch.setMessage(Component.translatable(mode == 0 ? "gui.factoryascent.pad.launch" : "gui.factoryascent.pad.board")
                    .withStyle(mode == 2 ? ChatFormatting.RED : ChatFormatting.RESET));
            launch.setTooltip(Tooltip.create(Component.translatable(mode == 0 ? "gui.factoryascent.pad.launch_tip"
                    : mode == 1 ? "gui.factoryascent.pad.board_tip" : "gui.factoryascent.pad.board_no_suit_tip")));
        }
    }

    private int buttonMode;

    /** [space hook] A Crew Capsule is the payload. */
    private boolean crew() {
        return net.juli2kapo.factoryascent.space.CrewLaunch.isCapsule(menu.getSlot(0).getItem());
    }

    /** [space hook] The viewer would die in orbit as they are. */
    private boolean unsuited() {
        return minecraft != null && minecraft.player != null
                && !(net.juli2kapo.factoryascent.space.SpaceRules.wearsFullSuit(minecraft.player)
                && net.juli2kapo.factoryascent.space.SuitItems.oxygen(net.juli2kapo.factoryascent.space.SpaceRules.suitTank(minecraft.player)) > 0);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        OrbitalGui.panel(g, x, y, imageWidth, imageHeight);
        slot(g, x + LaunchControllerMenu.PAYLOAD_X, y + LaunchControllerMenu.PAYLOAD_Y);
        slot(g, x + LaunchControllerMenu.FUEL_X, y + LaunchControllerMenu.FUEL_Y);
        OrbitalGui.inset(g, x + INFO_X + 8, y + 18, imageWidth - INFO_X - 16, 58);
        // fuel gauge: vertical, with a notch at one launch's worth
        int gx = x + GAUGE_X, gy = y + GAUGE_Y;
        g.fill(gx - 1, gy - 1, gx + GAUGE_W + 1, gy + GAUGE_H + 1, OrbitalGui.EDGE_DARK);
        g.fill(gx, gy, gx + GAUGE_W, gy + GAUGE_H, OrbitalGui.INSET);
        int filled = Math.round(GAUGE_H * Math.min(1f, menu.fuel() / (float) LaunchControllerBlockEntity.FUEL_MAX));
        int color = menu.fuel() >= menu.controller().fuelCost() ? 0xFFE08030 : 0xFFA04020;
        if (filled > 0) g.fill(gx, gy + GAUGE_H - filled, gx + GAUGE_W, gy + GAUGE_H, color);
        int notch = gy + GAUGE_H - GAUGE_H * Math.min(menu.controller().fuelCost(), LaunchControllerBlockEntity.FUEL_MAX) / LaunchControllerBlockEntity.FUEL_MAX;
        g.fill(gx - 2, notch, gx + GAUGE_W + 2, notch + 1, OrbitalGui.TEXT);
        // launch progress
        if (menu.launchTick() >= 0) {
            OrbitalGui.bar(g, x + INFO_X + 11, y + 66, imageWidth - INFO_X - 22, 6,
                    menu.launchTick() / (float) menu.controller().sequence(), 0xFFE0C040);
        }
        // player inventory slots
        for (int i = 0; i < 27; i++) slot(g, x + 8 + (i % 9) * 18, y + LaunchControllerMenu.PLAYER_INV_Y + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, x + 8 + i * 18, y + LaunchControllerMenu.PLAYER_INV_Y + 58);
        // ghost icons in empty slots
        if (menu.getSlot(0).getItem().isEmpty()) ghost(g, new ItemStack(OrbitalContent.SURVEY_SATELLITE.get()),
                x + LaunchControllerMenu.PAYLOAD_X, y + LaunchControllerMenu.PAYLOAD_Y);
        ghost(g, new ItemStack(OrbitalContent.ROCKET_FUEL.get()), x + LaunchControllerMenu.FUEL_X, y + LaunchControllerMenu.FUEL_Y);
    }

    private static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, OrbitalGui.EDGE_DARK);
        g.fill(x, y, x + 17, y + 17, OrbitalGui.EDGE_LIGHT);
        g.fill(x, y, x + 16, y + 16, OrbitalGui.INSET);
    }

    private static void ghost(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        g.fakeItem(stack, x, y);
        g.fill(x, y, x + 16, y + 16, 0xB010131A);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, OrbitalGui.ACCENT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, OrbitalGui.MUTED, false);
        g.text(font, Component.translatable("gui.factoryascent.pad.payload"), 8, LaunchControllerMenu.PAYLOAD_Y - 10, OrbitalGui.MUTED, false);
        g.text(font, Component.translatable("gui.factoryascent.pad.fuel"), 8, LaunchControllerMenu.FUEL_Y - 10, OrbitalGui.MUTED, false);
        int tx = INFO_X + 11, w = imageWidth - INFO_X - 22;
        ItemStack payload = menu.getSlot(0).getItem();
        int ty = 21;
        if (payload.isEmpty()) {
            g.text(font, Component.translatable("gui.factoryascent.pad.no_payload"), tx, ty, OrbitalGui.MUTED, false);
        } else {
            g.text(font, OrbitalGui.fit(font, payload.getHoverName(), w), tx, ty, OrbitalGui.TEXT, false);
            Component detail;
            boolean renamed = payload.getCustomName() != null;
            if (payload.getItem() instanceof AsatMissileItem) {
                AsatMissileItem.Target target = AsatMissileItem.target(payload);
                detail = target == null ? Component.translatable("gui.factoryascent.pad.missile_unprogrammed").withStyle(ChatFormatting.RED)
                        : Component.translatable("gui.factoryascent.pad.missile_target", target.label()).withStyle(ChatFormatting.GOLD);
            } else if (payload.getItem() instanceof SatelliteItem sat && renamed) {
                // the name alone says what it is unless it was renamed in an anvil
                detail = Component.translatable("gui.factoryascent.pad.satellite_type", sat.type().shortName()).withStyle(sat.type().color());
            } else {
                detail = Component.empty();
            }
            g.text(font, OrbitalGui.fit(font, detail, w), tx, ty + 10, OrbitalGui.TEXT, false);
        }
        g.text(font, OrbitalGui.fit(font, Component.translatable("gui.factoryascent.pad.fuel_amount", menu.fuel(),
                LaunchControllerBlockEntity.FUEL_MAX, menu.controller().fuelCost()), w), tx, ty + 22, OrbitalGui.MUTED, false);
        int status = menu.status();
        Component line = switch (status) {
            case LaunchControllerBlockEntity.STATUS_READY -> crew() // [space hook] boarding warnings
                    ? (unsuited() ? Component.translatable("gui.factoryascent.pad.status.no_suit").withStyle(ChatFormatting.RED)
                    : Component.translatable("gui.factoryascent.pad.status.board").withStyle(ChatFormatting.GREEN))
                    : Component.translatable("gui.factoryascent.pad.status.ready").withStyle(ChatFormatting.GREEN);
            case LaunchControllerBlockEntity.STATUS_LAUNCHING -> menu.launchTick() < LaunchControllerBlockEntity.LIFTOFF
                    ? Component.translatable("gui.factoryascent.pad.status.countdown",
                    (LaunchControllerBlockEntity.LIFTOFF - menu.launchTick() + 19) / 20).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("gui.factoryascent.pad.status.climbing").withStyle(ChatFormatting.GOLD);
            case LaunchControllerBlockEntity.STATUS_INCOMPLETE -> Component.translatable("gui.factoryascent.pad.status.incomplete").withStyle(ChatFormatting.RED);
            case LaunchControllerBlockEntity.STATUS_NO_PAYLOAD -> Component.translatable("gui.factoryascent.pad.status.no_payload").withStyle(ChatFormatting.GRAY);
            case LaunchControllerBlockEntity.STATUS_NO_FUEL -> Component.translatable("gui.factoryascent.pad.status.no_fuel").withStyle(ChatFormatting.YELLOW);
            case LaunchControllerBlockEntity.STATUS_BLOCKED -> Component.translatable("gui.factoryascent.pad.status.blocked").withStyle(ChatFormatting.RED);
            default -> Component.translatable("gui.factoryascent.pad.status.missile").withStyle(ChatFormatting.YELLOW);
        };
        if (messageTicks > 0) {
            // a refusal after pressing Launch replaces the status line (up to two lines)
            var lines = font.split(message, w);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), tx, ty + 33 + i * 10, OrbitalGui.TEXT, false);
        } else {
            g.text(font, OrbitalGui.fit(font, line, w), tx, ty + 33, OrbitalGui.TEXT, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (isHovering(GAUGE_X - 1, GAUGE_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.factoryascent.pad.fuel_amount", menu.fuel(), LaunchControllerBlockEntity.FUEL_MAX,
                            menu.controller().fuelCost()),
                    Component.translatable("gui.factoryascent.pad.fuel_tip", new ItemStack(Items.BLAZE_POWDER).getHoverName(),
                            new ItemStack(OrbitalContent.ROCKET_FUEL.get()).getHoverName()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (menu.getSlot(0).getItem().isEmpty()
                && isHovering(LaunchControllerMenu.PAYLOAD_X - 1, LaunchControllerMenu.PAYLOAD_Y - 1, 18, 18, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.factoryascent.pad.payload_tip"), mouseX, mouseY);
        } else if (isHovering(LaunchControllerMenu.FUEL_X - 1, LaunchControllerMenu.FUEL_Y - 1, 18, 18, mouseX, mouseY)
                && menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.factoryascent.pad.fuel_slot_tip"), mouseX, mouseY);
        }
    }
}
