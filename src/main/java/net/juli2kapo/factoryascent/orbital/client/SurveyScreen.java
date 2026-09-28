package net.juli2kapo.factoryascent.orbital.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.Marker;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.Row;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.SurveyAction;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.SurveyView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The survey map: the team's satellite imagery of the dimension on a large pannable (drag) and
 * zoomable (scroll wheel, or the + / − buttons) map. Unimaged ground is dark with a chunk grid.
 * Markers show this station, the team's other Ground Stations and Launch Pads, the viewer and the
 * team's other online members (names on hover); the line under the map gives the coordinates and
 * biome under the cursor. The side panel lists the team's satellites over the dimension with a
 * two-click Deorbit button. Imagery arrives in batches ({@link SurveyImagery}); the status comes
 * from the server about once a second ({@link SurveyView}).
 */
public class SurveyScreen extends Screen {
    private static final double[] ZOOMS = {0.125, 0.25, 0.5, 1, 2, 4};
    private static final int SIDE = 128;
    private static final int ROW_H = 26;
    private static final int GRID = 0xFF1A2130;
    private static final int GROUND = 0xFF0B0E14;

    private SurveyView view;
    private double centreX, centreZ;
    private int zoom = 2;
    private boolean dragging;
    private int scroll;
    private @Nullable UUID confirming;

    public SurveyScreen(SurveyView view) {
        super(Component.translatable("gui.factoryascent.survey.title"));
        this.view = view;
        this.centreX = view.header().origin().getX() + 0.5;
        this.centreZ = view.header().origin().getZ() + 0.5;
        SurveyImagery.reset();
    }

    /** The server opened the map again (e.g. from another station): start over with the new view. */
    public void reopen(SurveyView view) {
        SurveyImagery.reset();
        this.view = view;
        this.centreX = view.header().origin().getX() + 0.5;
        this.centreZ = view.header().origin().getZ() + 0.5;
        confirming = null;
        scroll = 0;
        rebuildWidgets();
    }

    public void update(SurveyView view) {
        boolean relayout = !ids(view).equals(ids(this.view));
        this.view = view;
        if (relayout) {
            confirming = null;
            scroll = Math.min(scroll, maxScroll());
            rebuildWidgets();
        }
    }

    private static List<UUID> ids(SurveyView view) {
        return view.satellites().stream().map(Row::id).toList();
    }

    // ---------------------------------------------------------------- layout

    private int mapLeft() {
        return 14;
    }

    private int mapTop() {
        return 28;
    }

    private int mapRight() {
        return width - 14 - SIDE - 6;
    }

    private int mapBottom() {
        return height - 36;
    }

    private int sideLeft() {
        return width - 14 - SIDE;
    }

    private int listTop() {
        return mapTop() + 52;
    }

    private int rows() {
        return Math.max(1, (mapBottom() - listTop()) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, view.satellites().size() - rows());
    }

    private double scale() {
        return ZOOMS[zoom];
    }

    private boolean inMap(double x, double y) {
        return x >= mapLeft() && x < mapRight() && y >= mapTop() && y < mapBottom();
    }

    private double screenX(double blockX) {
        return (mapLeft() + mapRight()) / 2.0 + (blockX - centreX) * scale();
    }

    private double screenZ(double blockZ) {
        return (mapTop() + mapBottom()) / 2.0 + (blockZ - centreZ) * scale();
    }

    private double blockX(double screenX) {
        return centreX + (screenX - (mapLeft() + mapRight()) / 2.0) / scale();
    }

    private double blockZ(double screenY) {
        return centreZ + (screenY - (mapTop() + mapBottom()) / 2.0) / scale();
    }

    @Override
    protected void init() {
        int sx = sideLeft();
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomAt(1, null)).bounds(sx, mapTop(), 20, 16).build())
                .setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.survey.zoom_in")));
        addRenderableWidget(Button.builder(Component.literal("-"), b -> zoomAt(-1, null)).bounds(sx + 22, mapTop(), 20, 16).build())
                .setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.survey.zoom_out")));
        addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.survey.centre"), b -> {
            centreX = view.header().origin().getX() + 0.5;
            centreZ = view.header().origin().getZ() + 0.5;
        }).bounds(sx + 44, mapTop(), SIDE - 44, 16).build())
                .setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.survey.centre_tip")));
        for (int i = 0; i < rows() && scroll + i < view.satellites().size(); i++) {
            Row row = view.satellites().get(scroll + i);
            boolean asking = row.id().equals(confirming);
            Button deorbit = Button.builder(Component.translatable(asking ? "gui.factoryascent.team.deorbit_confirm"
                            : "gui.factoryascent.team.deorbit").withStyle(asking ? ChatFormatting.RED : ChatFormatting.WHITE), b -> {
                if (row.id().equals(confirming)) {
                    ClientPacketDistributor.sendToServer(new SurveyAction(SurveyAction.DEORBIT, row.id()));
                    confirming = null;
                } else {
                    confirming = row.id();
                }
                rebuildWidgets();
            }).bounds(sx + SIDE - 58, listTop() + i * ROW_H + 12, 56, 12).build();
            deorbit.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.team.deorbit_tip")));
            addRenderableWidget(deorbit);
        }
    }

    // ---------------------------------------------------------------- input

    private void zoomAt(int step, @Nullable double[] at) {
        int next = Mth.clamp(zoom + step, 0, ZOOMS.length - 1);
        if (next == zoom) return;
        if (at != null) {
            // keep the block under the cursor where it is
            double bx = blockX(at[0]), bz = blockZ(at[1]);
            zoom = next;
            centreX = bx - (at[0] - (mapLeft() + mapRight()) / 2.0) / scale();
            centreZ = bz - (at[1] - (mapTop() + mapBottom()) / 2.0) / scale();
        } else {
            zoom = next;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0 && inMap(event.x(), event.y())) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            centreX -= dx / scale();
            centreZ -= dy / scale();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inMap(mouseX, mouseY) && scrollY != 0) {
            zoomAt(scrollY > 0 ? 1 : -1, new double[] {mouseX, mouseY});
            return true;
        }
        if (mouseX >= sideLeft() && mouseY >= listTop()) {
            int next = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            if (next != scroll) {
                scroll = next;
                confirming = null;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        OrbitalGui.panel(g, 6, 6, width - 12, height - 12);
        OrbitalGui.inset(g, mapLeft() - 1, mapTop() - 1, mapRight() - mapLeft() + 2, mapBottom() - mapTop() + 2);
        OrbitalGui.inset(g, sideLeft() - 1, listTop() - 1, SIDE + 2, rows() * ROW_H + 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        SurveyImagery.upload();
        g.text(font, title, 14, 13, OrbitalGui.ACCENT, false);
        Component where = Component.translatableWithFallback("orbital.factoryascent.dimension."
                + view.header().dimension().getNamespace() + "." + view.header().dimension().getPath(), view.header().dimension().toString());
        Component sub = Component.translatable(view.header().atStation() ? "gui.factoryascent.survey.at_station" : "gui.factoryascent.survey.remote", where);
        g.text(font, OrbitalGui.fit(font, sub, mapRight() - 30 - font.width(title)), 24 + font.width(title), 13, OrbitalGui.MUTED, false);

        List<Component> hover = new ArrayList<>();
        drawMap(g, mouseX, mouseY, hover);
        drawSide(g);

        // status lines under the map
        int y = mapBottom() + 5;
        var h = view.header();
        Component progress = Component.translatable("gui.factoryascent.survey.progress", h.imaged(), h.total(),
                h.total() == 0 ? 0 : h.imaged() * 100 / h.total());
        g.text(font, OrbitalGui.fit(font, view.status(), mapRight() - mapLeft() - font.width(progress) - 8), mapLeft(), y,
                OrbitalGui.TEXT, false);
        g.text(font, progress, mapRight() - font.width(progress), y, OrbitalGui.MUTED, false);
        Component cursor;
        if (inMap(mouseX, mouseY)) {
            int bx = Mth.floor(blockX(mouseX)), bz = Mth.floor(blockZ(mouseY));
            String biome = SurveyImagery.biome(ChunkPos.pack(bx >> 4, bz >> 4));
            cursor = biome == null ? Component.translatable("gui.factoryascent.survey.cursor_unknown", bx, bz)
                    : Component.translatable("gui.factoryascent.survey.cursor", bx, bz, biome.isEmpty() ? Component.literal("?")
                    : Component.translatable("biome." + biome.replace(':', '.')));
        } else {
            cursor = Component.translatable("gui.factoryascent.survey.hint");
        }
        g.text(font, OrbitalGui.fit(font, cursor, mapRight() - mapLeft()), mapLeft(), y + 11, OrbitalGui.MUTED, false);
        if (!view.message().getString().isEmpty()) {
            g.text(font, OrbitalGui.fit(font, view.message(), SIDE), sideLeft(), y + 11, OrbitalGui.TEXT, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (!hover.isEmpty()) g.setComponentTooltipForNextFrame(font, hover, mouseX, mouseY);
    }

    private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY, List<Component> hover) {
        int x0 = mapLeft(), y0 = mapTop(), x1 = mapRight(), y1 = mapBottom();
        g.enableScissor(x0, y0, x1, y1);
        g.fill(x0, y0, x1, y1, GROUND);
        // chunk grid (or a 16-chunk grid when zoomed far out)
        int step = scale() * 16 >= 6 ? 16 : 256;
        int first = Math.floorDiv((int) Math.floor(blockX(x0)), step) * step;
        for (int bx = first; screenX(bx) < x1; bx += step) {
            int sx = (int) Math.floor(screenX(bx));
            g.fill(sx, y0, sx + 1, y1, GRID);
        }
        first = Math.floorDiv((int) Math.floor(blockZ(y0)), step) * step;
        for (int bz = first; screenZ(bz) < y1; bz += step) {
            int sz = (int) Math.floor(screenZ(bz));
            g.fill(x0, sz, x1, sz + 1, GRID);
        }
        // imagery
        for (var entry : SurveyImagery.regions().long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int bx = ChunkPos.getX(key) * SurveyImagery.REGION_PIXELS, bz = ChunkPos.getZ(key) * SurveyImagery.REGION_PIXELS;
            int sx0 = (int) Math.floor(screenX(bx)), sz0 = (int) Math.floor(screenZ(bz));
            int sx1 = (int) Math.floor(screenX(bx + SurveyImagery.REGION_PIXELS)), sz1 = (int) Math.floor(screenZ(bz + SurveyImagery.REGION_PIXELS));
            if (sx1 < x0 || sx0 > x1 || sz1 < y0 || sz0 > y1) continue;
            var texture = entry.getValue().texture;
            g.blit(texture.getTextureView(), texture.getSampler(), sx0, sz0, sx1, sz1, 0f, 1f, 0f, 1f);
        }
        // survey radius around the station
        var h = view.header();
        if (h.atStation()) {
            int ox = h.origin().getX() >> 4, oz = h.origin().getZ() >> 4;
            int ax = (int) Math.floor(screenX((ox - h.radius()) * 16)), az = (int) Math.floor(screenZ((oz - h.radius()) * 16));
            int bx = (int) Math.floor(screenX((ox + h.radius() + 1) * 16)), bz = (int) Math.floor(screenZ((oz + h.radius() + 1) * 16));
            g.outline(ax, az, bx - ax, bz - az, 0x80B060E0);
        }
        // markers
        for (Marker m : view.markers()) {
            int color = switch (m.kind()) {
                case Marker.THIS_STATION -> 0xFFB060E0;
                case Marker.STATION -> 0xFF8050B0;
                case Marker.PAD -> 0xFFE0A040;
                default -> 0xFF40D0E0;
            };
            int size = m.kind() == Marker.THIS_STATION ? 3 : 2;
            if (marker(g, m.x() + 0.5, m.z() + 0.5, size, color, mouseX, mouseY)) hover.add(markerName(m));
        }
        LocalPlayer me = Minecraft.getInstance().player;
        if (me != null && me.level().dimension().identifier().equals(h.dimension())) {
            if (marker(g, me.getX(), me.getZ(), 2, 0xFFFFFFFF, mouseX, mouseY)) {
                hover.add(Component.translatable("gui.factoryascent.survey.you"));
            }
            // facing tick
            double yaw = Math.toRadians(me.getYRot());
            int px = (int) Math.floor(screenX(me.getX())), pz = (int) Math.floor(screenZ(me.getZ()));
            int tx = px + (int) Math.round(-Math.sin(yaw) * 5), tz = pz + (int) Math.round(Math.cos(yaw) * 5);
            g.fill(tx - 1, tz - 1, tx + 1, tz + 1, 0xFFFFFFFF);
        }
        if (!h.surveying()) {
            g.fill(x0, y0, x1, y1, 0xA0000000);
            int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
            g.centeredText(font, Component.translatable("gui.factoryascent.survey.no_satellite").withStyle(ChatFormatting.RED), cx, cy - 20, 0xFFFFFFFF);
            Component how = Component.translatable("gui.factoryascent.survey.how");
            int w = Math.min(x1 - x0 - 20, 260);
            g.textWithWordWrap(font, how, cx - w / 2, cy - 6, w, OrbitalGui.MUTED, false);
        } else if (SurveyImagery.chunks() == 0) {
            g.centeredText(font, Component.translatable("gui.factoryascent.survey.waiting"), (x0 + x1) / 2, y0 + 8, OrbitalGui.MUTED);
        }
        g.disableScissor();
    }

    private static Component markerName(Marker m) {
        return switch (m.kind()) {
            case Marker.THIS_STATION -> Component.translatable("gui.factoryascent.survey.this_station");
            case Marker.STATION -> Component.translatable("gui.factoryascent.survey.station", m.name(), m.x(), m.z());
            case Marker.PAD -> Component.translatable("gui.factoryascent.survey.pad", m.name(), m.x(), m.z());
            default -> Component.translatable("gui.factoryascent.survey.member", m.name(), m.x(), m.z());
        };
    }

    /** A square marker with a dark outline; true if the mouse is over it. */
    private boolean marker(GuiGraphicsExtractor g, double bx, double bz, int r, int color, int mouseX, int mouseY) {
        int x = (int) Math.floor(screenX(bx)), z = (int) Math.floor(screenZ(bz));
        g.fill(x - r - 1, z - r - 1, x + r + 1, z + r + 1, 0xFF000000);
        g.fill(x - r, z - r, x + r, z + r, color);
        return inMap(mouseX, mouseY) && Math.abs(mouseX - x) <= r + 2 && Math.abs(mouseY - z) <= r + 2;
    }

    private void drawSide(GuiGraphicsExtractor g) {
        int sx = sideLeft();
        g.text(font, Component.translatable("gui.factoryascent.survey.satellites", view.satellites().size()), sx, listTop() - 11,
                OrbitalGui.TEXT, false);
        if (view.satellites().isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("gui.factoryascent.survey.no_satellites"), sx + 4, listTop() + 4, SIDE - 8,
                    OrbitalGui.MUTED, false);
        }
        for (int i = 0; i < rows() && scroll + i < view.satellites().size(); i++) {
            Row row = view.satellites().get(scroll + i);
            int ry = listTop() + i * ROW_H;
            g.text(font, OrbitalGui.fit(font, row.line(), SIDE - 8), sx + 4, ry + 3, OrbitalGui.TEXT, false);
            g.text(font, Component.translatable("gui.factoryascent.survey.days", row.progress()), sx + 4, ry + 14, OrbitalGui.MUTED, false);
            if (i > 0) g.fill(sx + 2, ry, sx + SIDE - 2, ry + 1, OrbitalGui.EDGE_LIGHT);
        }
        if (maxScroll() > 0) {
            String more = (scroll + 1) + "-" + Math.min(view.satellites().size(), scroll + rows()) + "/" + view.satellites().size();
            g.text(font, more, sx + SIDE - font.width(more), listTop() - 11, OrbitalGui.MUTED, false);
        }
        // legend
        int ly = mapTop() + 19;
        legend(g, sx, ly, 0xFFB060E0, "gui.factoryascent.survey.legend_station");
        legend(g, sx + 64, ly, 0xFFE0A040, "gui.factoryascent.survey.legend_pad");
        legend(g, sx, ly + 9, 0xFFFFFFFF, "gui.factoryascent.survey.legend_you");
        legend(g, sx + 64, ly + 9, 0xFF40D0E0, "gui.factoryascent.survey.legend_team");
    }

    private void legend(GuiGraphicsExtractor g, int x, int y, int color, String key) {
        g.fill(x, y + 1, x + 5, y + 6, 0xFF000000);
        g.fill(x + 1, y + 2, x + 4, y + 5, color);
        g.text(font, OrbitalGui.fit(font, Component.translatable(key), 56), x + 8, y, OrbitalGui.MUTED, false);
    }

    @Override
    public void removed() {
        ClientPacketDistributor.sendToServer(new SurveyAction(SurveyAction.CLOSE, new UUID(0, 0)));
        SurveyImagery.reset();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}
