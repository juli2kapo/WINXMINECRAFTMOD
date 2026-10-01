package net.juli2kapo.factoryascent.xdim.client;

import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.juli2kapo.factoryascent.xdim.LinkBlockEntity;
import net.juli2kapo.factoryascent.xdim.LinkTier;
import net.juli2kapo.factoryascent.xdim.LinkTier.Resource;
import net.juli2kapo.factoryascent.xdim.XdimPayloads;
import net.juli2kapo.factoryascent.xdim.XdimPayloads.ChannelInfo;
import net.juli2kapo.factoryascent.xdim.XdimPayloads.EndpointInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The link screen: per-face modes (items / fluids / energy: off, send, receive) on the left, the
 * channel list on the right (click to tune; name a new one and create it, private to your team or
 * public; rename, open up or delete your own), and below the endpoints on the tuned channel with
 * their dimension, position, whether they keep their chunk loaded, and live throughput.
 */
public class XdimScreen extends Screen {
    private static final int W = 300, H = 238;
    private static final Direction[] FACES = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int GRID_X = 8, GRID_Y = 30, LABEL_W = 34, CELL_W = 30, CELL_H = 13, ROW_H = 15;
    private static final int CH_X = 142, CH_Y = 20, CH_W = 150, CH_ROWS = 6, CH_ROW_H = 12;
    private static final int EP_Y = 148, EP_ROWS = 4;
    private static final int[] MODE_COLOR = {0xFF606060, 0xFF3F8FE0, 0xFFE09030};
    private static final int CYAN = 0xFF40D8E8;

    private XdimPayloads.View view;
    private int ticks;
    private int page;
    private @Nullable EditBox name;
    private boolean newPublic;

    public XdimScreen(XdimPayloads.View view) {
        super(Component.translatable(view.tier() == LinkTier.QUANTUM.ordinal() ? "block.factoryascent.quantum_entangler" : "block.factoryascent.ender_link"));
        this.view = view;
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(XdimPayloads.View view) {
        this.view = view;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int accent() {
        return quantum() ? CYAN : FactoryGui.ENDER;
    }

    private boolean quantum() {
        return view.tier() == LinkTier.QUANTUM.ordinal();
    }

    private void send(int action, int a, int b, String text) {
        ClientPacketDistributor.sendToServer(new XdimPayloads.Action(view.pos(), action, a, b, text));
    }

    @Override
    protected void init() {
        name = new EditBox(font, left() + CH_X, top() + CH_Y + 10 + CH_ROWS * CH_ROW_H + 5, 82, 14, Component.translatable("gui.factoryascent.xdim.name"));
        name.setMaxLength(24);
        name.setHint(Component.translatable("gui.factoryascent.xdim.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(name);
    }

    @Override
    public void tick() {
        if (++ticks % 10 == 0) send(XdimPayloads.REFRESH, 0, 0, "");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (name != null && name.isFocused() && event.isConfirmation()) {
            create();
            return true;
        }
        return super.keyPressed(event);
    }

    private void create() {
        if (name == null || !view.mayEdit()) return;
        send(XdimPayloads.CREATE, 0, newPublic ? 1 : 0, name.getValue());
        name.setValue("");
    }

    // ---------------------------------------------------------------- layout helpers

    private int[] cell(int row, int col) {
        return new int[] {left() + GRID_X + LABEL_W + col * (CELL_W + 2), top() + GRID_Y + row * ROW_H};
    }

    private @Nullable ChannelInfo tuned() {
        for (ChannelInfo c : view.channels()) if (c.id() == view.channel()) return c;
        return null;
    }

    private int pages() {
        return Math.max(1, (view.channels().size() + CH_ROWS - 1) / CH_ROWS);
    }

    /** The action buttons under the name field: create, public toggle, rename, open/close, delete, unlink. */
    private record Btn(int x, int y, int w, String key) {}

    private List<Btn> buttons() {
        int x = left() + CH_X, y = top() + CH_Y + 10 + CH_ROWS * CH_ROW_H + 5;
        int y2 = y + 17;
        return List.of(new Btn(x + 84, y, 18, "create"), new Btn(x + 104, y, 46, newPublic ? "public_short" : "private_short"),
                new Btn(x, y2, 49, "rename"), new Btn(x + 50, y2, 50, "toggle"), new Btn(x + 101, y2, 49, "unlink"));
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0 || event.button() == 1) {
            // faces
            if (view.mayEdit()) {
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 3; col++) {
                        if (col == 2 && !quantum()) continue;
                        int[] c = cell(row, col);
                        if (FactoryGui.inside(mx, my, c[0], c[1], CELL_W, CELL_H)) {
                            int idx = FACES[row].get3DDataValue() * 3 + col;
                            int mode = view.modes()[idx];
                            int next = Math.floorMod(mode + (event.button() == 0 ? 1 : -1), 3);
                            view.modes()[idx] = (byte) next;
                            send(XdimPayloads.SET_MODE, idx, next, "");
                            click();
                            return true;
                        }
                    }
                }
            }
            // channel list
            int lx = left() + CH_X, ly = top() + CH_Y + 10;
            List<ChannelInfo> list = view.channels();
            for (int i = 0; i < CH_ROWS; i++) {
                int idx = page * CH_ROWS + i;
                if (idx >= list.size()) break;
                if (FactoryGui.inside(mx, my, lx, ly + i * CH_ROW_H, CH_W - 18, CH_ROW_H) && view.mayEdit()) {
                    send(XdimPayloads.TUNE, list.get(idx).id(), 0, "");
                    click();
                    return true;
                }
            }
            if (FactoryGui.inside(mx, my, lx + CH_W - 16, ly, 16, CH_ROW_H * 3)) {
                page = Math.floorMod(page - 1, pages());
                click();
                return true;
            }
            if (FactoryGui.inside(mx, my, lx + CH_W - 16, ly + CH_ROW_H * 3, 16, CH_ROW_H * 3)) {
                page = Math.floorMod(page + 1, pages());
                click();
                return true;
            }
            for (Btn b : buttons()) {
                if (!FactoryGui.inside(mx, my, b.x(), b.y(), b.w(), 14)) continue;
                ChannelInfo t = tuned();
                switch (b.key()) {
                    case "create" -> create();
                    case "public_short", "private_short" -> newPublic = !newPublic;
                    case "rename" -> {
                        if (t != null && t.editable() && name != null && !name.getValue().isBlank()) {
                            send(XdimPayloads.RENAME, t.id(), 0, name.getValue());
                            name.setValue("");
                        }
                    }
                    case "toggle" -> {
                        if (t != null && t.editable()) send(XdimPayloads.SET_PUBLIC, t.id(), t.isPublic() ? 0 : 1, "");
                    }
                    case "unlink" -> {
                        if (event.button() == 1 && t != null && t.editable()) send(XdimPayloads.DELETE, t.id(), 0, "");
                        else send(XdimPayloads.TUNE, 0, 0, "");
                    }
                    default -> {}
                }
                click();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (FactoryGui.inside(mouseX, mouseY, left() + CH_X, top() + CH_Y, CH_W, CH_ROWS * CH_ROW_H + 10)) {
            page = Math.floorMod(page - (int) Math.signum(scrollY), pages());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        FactoryGui.panel(g, x, y, W, H, accent());
        // faces
        FactoryGui.well(g, x + GRID_X - 2, y + GRID_Y - 14, LABEL_W + 3 * (CELL_W + 2) + 3, 6 * ROW_H + 16);
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 3; col++) {
                int[] c = cell(row, col);
                boolean usable = col < 2 || quantum();
                int mode = view.modes()[FACES[row].get3DDataValue() * 3 + col];
                boolean hover = FactoryGui.inside(mouseX, mouseY, c[0], c[1], CELL_W, CELL_H);
                FactoryGui.button(g, c[0], c[1], CELL_W, CELL_H, hover && usable && view.mayEdit(), usable && mode != LinkBlockEntity.OFF,
                        MODE_COLOR[usable ? mode : 0]);
            }
        }
        // channels
        int lx = x + CH_X, ly = y + CH_Y + 10;
        FactoryGui.display(g, lx - 1, ly - 1, CH_W + 2, CH_ROWS * CH_ROW_H + 2);
        List<ChannelInfo> list = view.channels();
        for (int i = 0; i < CH_ROWS; i++) {
            int idx = page * CH_ROWS + i;
            if (idx >= list.size()) break;
            ChannelInfo c = list.get(idx);
            int ry = ly + i * CH_ROW_H;
            if (c.id() == view.channel()) g.fill(lx, ry, lx + CH_W - 18, ry + CH_ROW_H, (accent() & 0x00FFFFFF) | 0x70000000);
            else if (FactoryGui.inside(mouseX, mouseY, lx, ry, CH_W - 18, CH_ROW_H)) g.fill(lx, ry, lx + CH_W - 18, ry + CH_ROW_H, 0x30FFFFFF);
        }
        // page arrows
        FactoryGui.button(g, lx + CH_W - 16, ly, 16, CH_ROW_H * 3 - 1, FactoryGui.inside(mouseX, mouseY, lx + CH_W - 16, ly, 16, CH_ROW_H * 3), false, accent());
        FactoryGui.button(g, lx + CH_W - 16, ly + CH_ROW_H * 3, 16, CH_ROW_H * 3, FactoryGui.inside(mouseX, mouseY, lx + CH_W - 16, ly + CH_ROW_H * 3, 16, CH_ROW_H * 3), false, accent());
        ChannelInfo t = tuned();
        for (Btn b : buttons()) {
            boolean enabled = switch (b.key()) {
                case "rename", "toggle" -> t != null && t.editable();
                case "unlink" -> view.channel() != 0 && view.mayEdit();
                default -> view.mayEdit();
            };
            boolean selected = (b.key().equals("public_short"));
            FactoryGui.button(g, b.x(), b.y(), b.w(), 14, enabled && FactoryGui.inside(mouseX, mouseY, b.x(), b.y(), b.w(), 14), selected,
                    accent());
        }
        // endpoints and throughput
        FactoryGui.display(g, x + 8, y + EP_Y, W - 16, H - EP_Y - 8);
        if (quantum()) {
            float f = view.powerCapacity() <= 0 ? 0 : view.power() / (float) view.powerCapacity();
            FactoryGui.energyBar(g, x + GRID_X + 40, y + GRID_Y + 6 * ROW_H + 5, LABEL_W + 3 * (CELL_W + 2) - 42, 8, f);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        g.text(font, title, x + 8, y + 7, FactoryGui.TEXT, false);
        if (!view.team().isEmpty()) {
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.xdim.team", view.team()), 100),
                    x + 12 + font.width(title), y + 7, FactoryGui.MUTED, false);
        }
        // status lamp
        int status = view.status();
        int color = switch (status) {
            case LinkBlockEntity.ST_ACTIVE -> FactoryGui.GOOD;
            case LinkBlockEntity.ST_IDLE -> 0xFF60A0E0;
            case LinkBlockEntity.ST_NO_POWER, LinkBlockEntity.ST_DENIED -> FactoryGui.BAD;
            default -> FactoryGui.WARN;
        };
        Component st = Component.translatable("gui.factoryascent.xdim.status." + status);
        int sw = font.width(st);
        g.text(font, st, x + W - 10 - sw, y + 7, FactoryGui.TEXT, false);
        FactoryGui.lamp(g, x + W - 20 - sw, y + 7, color);

        // faces
        String[] heads = {"items", "fluids", "energy"};
        for (int col = 0; col < 3; col++) {
            int[] c = cell(0, col);
            Component h = Component.translatable("gui.factoryascent.xdim.col." + heads[col]);
            g.text(font, FactoryGui.fit(font, h, CELL_W + 2), c[0] + (CELL_W - Math.min(CELL_W + 2, font.width(h))) / 2, y + GRID_Y - 11,
                    col == 2 && !quantum() ? FactoryGui.MUTED : FactoryGui.TEXT, false);
        }
        for (int row = 0; row < 6; row++) {
            int[] c0 = cell(row, 0);
            g.text(font, Component.translatable("gui.factoryascent.xdim.face." + FACES[row].getName()), x + GRID_X + 1, c0[1] + 3, FactoryGui.TEXT, false);
            for (int col = 0; col < 3; col++) {
                int[] c = cell(row, col);
                boolean usable = col < 2 || quantum();
                int mode = view.modes()[FACES[row].get3DDataValue() * 3 + col];
                Component label = usable ? Component.translatable("gui.factoryascent.xdim.mode_short." + mode) : Component.literal("-");
                int lw = font.width(label);
                g.text(font, label, c[0] + (CELL_W - lw) / 2, c[1] + 3, usable && mode != 0 ? 0xFFFFFFFF : 0xFF303030, false);
            }
        }
        int noteY = y + GRID_Y + 6 * ROW_H + 4;
        if (quantum()) {
            g.text(font, Component.translatable("gui.factoryascent.xdim.power"), x + GRID_X + 1, noteY + 2, FactoryGui.TEXT, false);
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.xdim.power_value", EnergyUtil.format(view.power()),
                    EnergyUtil.format(view.cost())), 130), x + GRID_X + 1, noteY + 13, FactoryGui.MUTED, false);
        } else {
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.xdim.ender_note"), 130), x + GRID_X + 1, noteY + 2,
                    FactoryGui.MUTED, false);
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.xdim.ender_note2"), 130), x + GRID_X + 1, noteY + 13,
                    FactoryGui.MUTED, false);
        }

        // channel header and list
        int lx = x + CH_X, ly = y + CH_Y + 10;
        ChannelInfo t = tuned();
        Component head = t == null ? Component.translatable("gui.factoryascent.xdim.no_channel")
                : Component.translatable("gui.factoryascent.xdim.tuned", t.name());
        g.text(font, FactoryGui.fit(font, head, CH_W), lx, y + CH_Y, FactoryGui.TEXT, false);
        List<ChannelInfo> list = view.channels();
        if (list.isEmpty()) {
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.xdim.no_channels"), CH_W - 22), lx + 3, ly + 2,
                    FactoryGui.DISPLAY_MUTED, false);
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.xdim.no_channels2"), CH_W - 22), lx + 3, ly + 14,
                    FactoryGui.DISPLAY_MUTED, false);
        }
        for (int i = 0; i < CH_ROWS; i++) {
            int idx = page * CH_ROWS + i;
            if (idx >= list.size()) break;
            ChannelInfo c = list.get(idx);
            int ry = ly + i * CH_ROW_H + 2;
            int col = c.isPublic() ? 0xFF80E080 : FactoryGui.DISPLAY_TEXT;
            String count = "×" + c.endpoints();
            g.text(font, FactoryGui.fit(font, Component.literal((c.isPublic() ? "◎ " : "● ") + c.name()), CH_W - 44), lx + 3, ry, col, false);
            g.text(font, count, lx + CH_W - 20 - font.width(count), ry, FactoryGui.DISPLAY_MUTED, false);
        }
        g.text(font, "▲", lx + CH_W - 11, ly + CH_ROW_H + 8, FactoryGui.TEXT, false);
        g.text(font, "▼", lx + CH_W - 11, ly + CH_ROW_H * 4 + 8, FactoryGui.TEXT, false);
        if (pages() > 1) {
            String pg = (page + 1) + "/" + pages();
            g.text(font, pg, lx + CH_W - font.width(pg), y + CH_Y, FactoryGui.MUTED, false);
        }
        for (Btn b : buttons()) {
            Component label = Component.translatable("gui.factoryascent.xdim.btn." + (b.key().equals("toggle") && t != null && t.isPublic() ? "make_private" : b.key()));
            int lw = Math.min(font.width(label), b.w() - 2);
            g.text(font, FactoryGui.fit(font, label, b.w() - 2), b.x() + (b.w() - lw) / 2, b.y() + 3,
                    b.key().equals("public_short") ? 0xFFFFFFFF : FactoryGui.TEXT, false);
        }

        // endpoints
        int ex = x + 12, ey = y + EP_Y + 4, ew = W - 24;
        long[] s = view.stats();
        Component through = Component.translatable("gui.factoryascent.xdim.throughput",
                s.length > 1 ? s[0] : 0, s.length > 1 ? s[1] : 0, s.length > 3 ? fmt(s[2]) : "0", s.length > 3 ? fmt(s[3]) : "0",
                s.length > 5 ? EnergyUtil.format(s[4] / 20) : "0", s.length > 5 ? EnergyUtil.format(s[5] / 20) : "0");
        g.text(font, FactoryGui.fit(font, through, ew), ex, ey, FactoryGui.DISPLAY_TEXT, false);
        ey += 11;
        Component anchor = view.channel() == 0 ? Component.translatable("gui.factoryascent.xdim.anchor_none")
                : view.anchored() ? Component.translatable("gui.factoryascent.xdim.anchor_on")
                : Component.translatable("gui.factoryascent.xdim.anchor_limit");
        g.text(font, FactoryGui.fit(font, anchor, ew), ex, ey, view.anchored() ? 0xFF80E080 : FactoryGui.DISPLAY_MUTED, false);
        ey += 12;
        List<EndpointInfo> eps = view.endpoints();
        if (eps.isEmpty()) {
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.xdim.no_endpoints"), ew), ex, ey, FactoryGui.DISPLAY_MUTED, false);
        }
        for (int i = 0; i < Math.min(EP_ROWS, eps.size()); i++) {
            EndpointInfo e = eps.get(i);
            int c = e.here() ? accent() : e.loaded() ? FactoryGui.DISPLAY_TEXT : FactoryGui.DISPLAY_MUTED;
            String dim = dimName(e.dimension());
            String tierMark = e.tier() == LinkTier.QUANTUM.ordinal() ? "Q" : "E";
            Component line = Component.literal((e.anchored() ? "⚓" : "·") + " " + tierMark + " " + dim + "  " + e.pos().getX() + ", "
                    + e.pos().getY() + ", " + e.pos().getZ() + (e.here() ? "  ◄" : ""));
            g.text(font, FactoryGui.fit(font, line, ew - 90), ex, ey + i * 10, c, false);
            long[] es = e.stats();
            String io = es.length >= 6 ? "↑" + rate(es[0], es[2], es[4]) + "  ↓" + rate(es[1], es[3], es[5]) : "";
            Component epStatus = e.loaded() ? Component.literal(io) : Component.translatable("gui.factoryascent.xdim.unloaded");
            g.text(font, epStatus, ex + ew - font.width(epStatus), ey + i * 10, e.loaded() ? FactoryGui.DISPLAY_MUTED : FactoryGui.WARN, false);
        }
        if (eps.size() > EP_ROWS) {
            g.text(font, Component.translatable("gui.factoryascent.xdim.more", eps.size() - EP_ROWS), ex, ey + EP_ROWS * 10, FactoryGui.DISPLAY_MUTED, false);
        }
        // tooltips
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 3; col++) {
                int[] c = cell(row, col);
                if (!FactoryGui.inside(mouseX, mouseY, c[0], c[1], CELL_W, CELL_H)) continue;
                boolean usable = col < 2 || quantum();
                int mode = view.modes()[FACES[row].get3DDataValue() * 3 + col];
                g.setComponentTooltipForNextFrame(font, usable
                        ? List.of(Component.translatable("gui.factoryascent.xdim.mode." + mode),
                                Component.translatable("gui.factoryascent.xdim.mode_tip." + mode + "." + Resource.VALUES[col].key()).withStyle(ChatFormatting.GRAY),
                                Component.translatable("gui.factoryascent.xdim.click_cycle").withStyle(ChatFormatting.DARK_GRAY))
                        : List.of(Component.translatable("gui.factoryascent.xdim.no_energy")), mouseX, mouseY);
            }
        }
        for (Btn b : buttons()) {
            if (FactoryGui.inside(mouseX, mouseY, b.x(), b.y(), b.w(), 14)) {
                g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.factoryascent.xdim.tip." + b.key())), mouseX, mouseY);
            }
        }
    }

    /** One endpoint's flow in one direction, compactly: the biggest of items/s, mB/s and FE/t. */
    private static String rate(long items, long mb, long fePerSecond) {
        if (fePerSecond > 0) return EnergyUtil.format(fePerSecond / 20) + " FE/t";
        if (mb > 0) return fmt(mb) + " mB/s";
        if (items > 0) return items + "/s";
        return "0";
    }

    private static String fmt(long mbPerSecond) {
        return mbPerSecond >= 10_000 ? EnergyUtil.format(mbPerSecond) : Long.toString(mbPerSecond);
    }

    private static String dimName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> Component.translatable("gui.factoryascent.xdim.dim.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("gui.factoryascent.xdim.dim.nether").getString();
            case "minecraft:the_end" -> Component.translatable("gui.factoryascent.xdim.dim.end").getString();
            default -> {
                String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                yield path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
            }
        };
    }
}
