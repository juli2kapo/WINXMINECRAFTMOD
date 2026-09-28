package net.juli2kapo.factoryascent.ui.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.juli2kapo.factoryascent.ui.ScreenPayloads;
import net.juli2kapo.factoryascent.ui.ScreenPayloads.PipeAction;
import net.juli2kapo.factoryascent.ui.ScreenPayloads.PipeView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Item pipe: a diagram of its six faces (compass cross for the sides, up/down beside it) with the
 * block on each face, a row per face with an Insert/Extract switch where the face touches an
 * inventory (what the Wrench toggles), and the pipe network's numbers.
 */
public class PipeScreen extends NetScreen {
    private static final int W = 260, H = 176;
    private static final int DIAGRAM_X = 8, DIAGRAM_Y = 20, DIAGRAM_W = 118, DIAGRAM_H = 104;
    private static final int CELL = 22;
    /** Left edge of the compass cross's core, and of the up/down column (relative to the screen). */
    private static final int CORE_X = DIAGRAM_X + 6 + CELL + 4, COLUMN_X = CORE_X + 2 * CELL + 4 + 10;
    private static final int ROWS_X = 132, ROWS_Y = 24, ROW_H = 17;
    private static final int COLOR_PIPE = 0xFF4A78D8, COLOR_INSERT = 0xFF4CB050, COLOR_EXTRACT = 0xFFE08A2A, COLOR_NONE = 0xFF3A3F48;

    private PipeView view;

    public PipeScreen(PipeView view, Component title) {
        super(title);
        this.view = view;
    }

    @Override
    BlockPos pos() {
        return view.pos();
    }

    public void update(PipeView view) {
        boolean relayout = !view.modes().equals(this.view.modes()) || !view.kinds().equals(this.view.kinds());
        this.view = view;
        if (relayout) rebuildWidgets();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private boolean configurable(int side) {
        return view.kinds().get(side) == ScreenPayloads.SIDE_INVENTORY;
    }

    private boolean extracting(int side) {
        return view.modes().get(side) == PipeConnection.EXTRACT.ordinal();
    }

    private void toggle(int side) {
        if (!configurable(side)) return;
        ClientPacketDistributor.sendToServer(new PipeAction(view.pos(), side,
                extracting(side) ? PipeAction.INSERT : PipeAction.EXTRACT));
    }

    /** Diagram cell of a face, relative to the screen: north up, east right; up/down in a column on the right. */
    private int[] cell(Direction dir) {
        int cx = CORE_X, cy = DIAGRAM_Y + (DIAGRAM_H - CELL) / 2;
        return switch (dir) {
            case NORTH -> new int[] {cx, cy - CELL - 4};
            case SOUTH -> new int[] {cx, cy + CELL + 4};
            case WEST -> new int[] {cx - CELL - 4, cy};
            case EAST -> new int[] {cx + CELL + 4, cy};
            case UP -> new int[] {COLUMN_X, DIAGRAM_Y + 14};
            case DOWN -> new int[] {COLUMN_X, DIAGRAM_Y + DIAGRAM_H - CELL - 14};
        };
    }

    private int[] core() {
        return new int[] {CORE_X, DIAGRAM_Y + (DIAGRAM_H - CELL) / 2};
    }

    private int color(int side) {
        return switch (view.kinds().get(side)) {
            case ScreenPayloads.SIDE_PIPE -> COLOR_PIPE;
            case ScreenPayloads.SIDE_INVENTORY -> extracting(side) ? COLOR_EXTRACT : COLOR_INSERT;
            default -> COLOR_NONE;
        };
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        for (Direction dir : Direction.values()) {
            int side = dir.get3DDataValue();
            if (!configurable(side)) continue;
            boolean ext = extracting(side);
            Button b = Button.builder(Component.translatable(ext ? "gui.factoryascent.pipe.extract" : "gui.factoryascent.pipe.insert")
                            .withStyle(ext ? ChatFormatting.GOLD : ChatFormatting.GREEN), btn -> toggle(side))
                    .bounds(x + W - 60, y + ROWS_Y + side * ROW_H - 2, 52, 14).build();
            b.setTooltip(Tooltip.create(Component.translatable(ext ? "gui.factoryascent.pipe.extract_tip" : "gui.factoryascent.pipe.insert_tip")));
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x + W - 60, y + H - 22, 52, 14).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Direction dir : Direction.values()) {
                int[] c = cell(dir);
                if (FactoryGui.inside(event.x(), event.y(), left() + c[0], top() + c[1], CELL, CELL) && configurable(dir.get3DDataValue())) {
                    toggle(dir.get3DDataValue());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        FactoryGui.panel(g, x, y, W, H, FactoryGui.AUTOMATION);
        FactoryGui.display(g, x + DIAGRAM_X, y + DIAGRAM_Y, DIAGRAM_W, DIAGRAM_H);
        int[] core = core();
        int kx = x + core[0], ky = y + core[1];
        // arms from the core to the four side cells
        for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            int side = dir.get3DDataValue();
            boolean linked = view.modes().get(side) != PipeConnection.NONE.ordinal();
            int c = linked ? color(side) : 0xFF30343C;
            int mid = CELL / 2;
            switch (dir) {
                case NORTH -> g.fill(kx + mid - 3, ky - 4, kx + mid + 3, ky, c);
                case SOUTH -> g.fill(kx + mid - 3, ky + CELL, kx + mid + 3, ky + CELL + 4, c);
                case WEST -> g.fill(kx - 4, ky + mid - 3, kx, ky + mid + 3, c);
                case EAST -> g.fill(kx + CELL, ky + mid - 3, kx + CELL + 4, ky + mid + 3, c);
                default -> {}
            }
        }
        // core: the pipe itself, with dots for up/down arms
        g.fill(kx, ky, kx + CELL, ky + CELL, 0xFF000000);
        g.fill(kx + 1, ky + 1, kx + CELL - 1, ky + CELL - 1, 0xFF8A8F98);
        g.fill(kx + 6, ky + 6, kx + CELL - 6, ky + CELL - 6, 0xFF5A5F68);
        for (Direction dir : new Direction[] {Direction.UP, Direction.DOWN}) {
            int side = dir.get3DDataValue();
            if (view.modes().get(side) == PipeConnection.NONE.ordinal()) continue;
            int c = color(side);
            if (dir == Direction.UP) {
                g.fill(kx + 8, ky + 8, kx + 14, ky + 14, c);
            } else {
                g.fill(kx + 9, ky + 9, kx + 13, ky + 13, 0xFF000000);
                g.fill(kx + 7, ky + 7, kx + 9, ky + 9, c);
                g.fill(kx + 13, ky + 13, kx + 15, ky + 15, c);
                g.fill(kx + 13, ky + 7, kx + 15, ky + 9, c);
                g.fill(kx + 7, ky + 13, kx + 9, ky + 15, c);
            }
        }
        // the six face cells
        for (Direction dir : Direction.values()) {
            int side = dir.get3DDataValue();
            int[] c = cell(dir);
            int cx = x + c[0], cy = y + c[1];
            boolean hover = FactoryGui.inside(mouseX, mouseY, cx, cy, CELL, CELL) && configurable(side);
            int border = color(side);
            g.fill(cx, cy, cx + CELL, cy + CELL, hover ? FactoryGui.brighter(border) : border);
            g.fill(cx + 2, cy + 2, cx + CELL - 2, cy + CELL - 2, 0xFF22262D);
        }
        // a thin separator between the cross and the up/down column
        int sepX = x + COLUMN_X - 6;
        g.fill(sepX, y + DIAGRAM_Y + 6, sepX + 1, y + DIAGRAM_Y + DIAGRAM_H - 6, 0xFF30343C);
        // rows
        for (int side = 0; side < 6; side++) {
            int ry = y + ROWS_Y + side * ROW_H - 3;
            g.fill(x + ROWS_X, ry, x + ROWS_X + 3, ry + 15, color(side));
        }
        FactoryGui.display(g, x + 8, y + DIAGRAM_Y + DIAGRAM_H + 4, W - 72, 28);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = left(), y = top();
        g.text(font, title, x + 8, y + 8, FactoryGui.TEXT, false);
        Component rate = Component.translatable("gui.factoryascent.pipe.rate", view.rate());
        g.text(font, rate, x + W - 8 - font.width(rate), y + 8, FactoryGui.MUTED, false);
        for (Direction dir : Direction.values()) {
            int side = dir.get3DDataValue();
            int[] c = cell(dir);
            ItemStack icon = view.neighbours().get(side);
            if (!icon.isEmpty()) g.item(icon, x + c[0] + 3, y + c[1] + 3);
            else g.text(font, "·", x + c[0] + CELL / 2 - 1, y + c[1] + 7, 0xFF5A5F68, false);
        }
        Component up = Component.translatable("gui.factoryascent.pipe.up_short");
        Component down = Component.translatable("gui.factoryascent.pipe.down_short");
        g.text(font, up, x + COLUMN_X + CELL - font.width(up), y + cell(Direction.UP)[1] - 10, FactoryGui.DISPLAY_MUTED, false);
        g.text(font, down, x + COLUMN_X + CELL - font.width(down), y + cell(Direction.DOWN)[1] + CELL + 2, FactoryGui.DISPLAY_MUTED, false);
        int[] n = cell(Direction.NORTH);
        g.text(font, "N", x + n[0] - 9, y + n[1] + 1, FactoryGui.DISPLAY_MUTED, false);
        // face rows
        for (int side = 0; side < 6; side++) {
            Direction dir = Direction.from3DDataValue(side);
            int ry = y + ROWS_Y + side * ROW_H;
            Component face = Component.translatable("direction.factoryascent." + dir.getName());
            g.text(font, FactoryGui.fit(font, face, 40), x + ROWS_X + 6, ry, FactoryGui.TEXT, false);
            ItemStack icon = view.neighbours().get(side);
            if (!icon.isEmpty()) g.item(icon, x + ROWS_X + 48, ry - 4);
            int kind = view.kinds().get(side);
            if (kind != ScreenPayloads.SIDE_INVENTORY) {
                Component what = Component.translatable(kind == ScreenPayloads.SIDE_PIPE ? "gui.factoryascent.pipe.to_pipe"
                        : "gui.factoryascent.pipe.nothing");
                g.text(font, FactoryGui.fit(font, what, 52), x + W - 60 + (52 - Math.min(52, font.width(what))) / 2, ry,
                        kind == ScreenPayloads.SIDE_PIPE ? 0xFF3A5AA8 : FactoryGui.MUTED, false);
            }
        }
        // network numbers
        int ny = y + DIAGRAM_Y + DIAGRAM_H + 8;
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.pipe.network", view.pipes(), view.destinations()), W - 80),
                x + 12, ny, FactoryGui.DISPLAY_TEXT, false);
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.pipe.extracting", view.extracting()), W - 80),
                x + 12, ny + 11, view.extracting() > 0 ? 0xFFF0B060 : FactoryGui.DISPLAY_MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, partial);
        // tooltips on the diagram and the rows' icons
        for (Direction dir : Direction.values()) {
            int side = dir.get3DDataValue();
            int[] c = cell(dir);
            boolean overRowIcon = FactoryGui.inside(mouseX, mouseY, x + ROWS_X + 48, y + ROWS_Y + side * ROW_H - 4, 16, 16);
            if (!FactoryGui.inside(mouseX, mouseY, x + c[0], y + c[1], CELL, CELL) && !overRowIcon) continue;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("direction.factoryascent." + dir.getName()).withStyle(ChatFormatting.YELLOW));
            ItemStack icon = view.neighbours().get(side);
            if (!icon.isEmpty()) lines.add(icon.getHoverName());
            String key = switch (view.kinds().get(side)) {
                case ScreenPayloads.SIDE_PIPE -> "gui.factoryascent.pipe.state_pipe";
                case ScreenPayloads.SIDE_INVENTORY -> extracting(side) ? "gui.factoryascent.pipe.state_extract" : "gui.factoryascent.pipe.state_insert";
                default -> "gui.factoryascent.pipe.state_none";
            };
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            if (configurable(side)) lines.add(Component.translatable("gui.factoryascent.pipe.click_toggle").withStyle(ChatFormatting.DARK_GRAY));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }
}
