package net.juli2kapo.factoryascent.space.client;

import net.juli2kapo.factoryascent.space.planet.Planet;
import net.juli2kapo.factoryascent.space.station.StationPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** The Station Core's status screen and the Star Chart. */
final class StationScreens {
    static final int BG = 0xFF1B1E26, INSET = 0xFF10131A, EDGE_LIGHT = 0xFF3A4050, EDGE_DARK = 0xFF0A0C10,
            ACCENT = 0xFF40C8E0, TEXT = 0xFFE0E4EC, MUTED = 0xFF8890A0;

    private StationScreens() {}

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
        g.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, EDGE_LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, EDGE_LIGHT);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, EDGE_DARK);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, EDGE_DARK);
        g.fill(x + 3, y + 3, x + w - 3, y + 4, ACCENT);
    }

    static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, EDGE_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, INSET);
    }

    static void handleView(StationPayloads.StationView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof StationScreen screen && screen.view.pos().equals(view.pos())) {
            screen.view = view;
        } else if (view.open()) {
            mc.gui.setScreen(new StationScreen(view));
        }
    }

    static void handleChart(StationPayloads.StarChart chart) {
        Minecraft.getInstance().gui.setScreen(new StarChartScreen(chart));
    }

    /** The Station Core: owner, modules, power and air, refreshed every second. */
    static final class StationScreen extends Screen {
        private static final int W = 250, H = 196;
        StationPayloads.StationView view;
        private int ticks;

        StationScreen(StationPayloads.StationView view) {
            super(Component.translatable("block.factoryascent.station_core"));
            this.view = view;
        }

        @Override
        public void tick() {
            if (++ticks % 20 == 0) ClientPacketDistributor.sendToServer(new StationPayloads.StationRefresh(view.pos()));
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractBackground(g, mouseX, mouseY, partial);
            int x = (width - W) / 2, y = (height - H) / 2;
            panel(g, x, y, W, H);
            inset(g, x + 6, y + 30, W - 12, H - 36);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            int x = (width - W) / 2, y = (height - H) / 2;
            g.text(font, title, x + 9, y + 9, ACCENT, false);
            g.text(font, Component.literal(view.name()), x + 9, y + 19, TEXT, false);
            int ly = y + 35;
            for (Component line : view.lines()) {
                for (FormattedCharSequence l : font.split(line, W - 24)) {
                    if (ly > y + H - 12) break;
                    g.text(font, l, x + 12, ly, TEXT, false);
                    ly += 10;
                }
            }
            super.extractRenderState(g, mouseX, mouseY, partial);
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

    /**
     * The Star Chart: the Sun and the worlds around it (Earth and its Moon, Mars, Jupiter with Io)
     * moving slowly on their orbits, where you are, and every station built out there.
     */
    static final class StarChartScreen extends Screen {
        private static final int W = 340, H = 214, MAP = 196;
        private final StationPayloads.StarChart chart;

        StarChartScreen(StationPayloads.StarChart chart) {
            super(Component.translatable("item.factoryascent.star_chart"));
            this.chart = chart;
        }

        private static void dot(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
            for (int dy = -r; dy <= r; dy++) {
                int half = (int) Math.round(Math.sqrt(r * r - dy * dy + 0.3));
                g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
            }
        }

        private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
            int steps = Math.max(24, r * 5);
            for (int i = 0; i < steps; i++) {
                double a = i * Math.PI * 2 / steps;
                int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(px, py, px + 1, py + 1, color);
            }
        }

        private static int[] onOrbit(int cx, int cy, int r, double angle) {
            return new int[] {cx + (int) Math.round(Math.cos(angle) * r), cy + (int) Math.round(Math.sin(angle) * r)};
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            super.extractBackground(g, mouseX, mouseY, partial);
            int x = (width - W) / 2, y = (height - H) / 2;
            panel(g, x, y, W, H);
            inset(g, x + 6, y + 14, MAP, H - 20);
            inset(g, x + MAP + 10, y + 14, W - MAP - 16, H - 20);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            int x = (width - W) / 2, y = (height - H) / 2;
            g.text(font, title, x + 9, y + 5, ACCENT, false);
            Minecraft mc = Minecraft.getInstance();
            double t = mc.level == null ? 0 : (mc.level.getGameTime() + partial) / 24000.0;
            int cx = x + 6 + MAP / 2, cy = y + 14 + (H - 20) / 2;
            // the Sun
            dot(g, cx, cy, 6, 0xFFFFD050);
            dot(g, cx, cy, 4, 0xFFFFF4B0);
            int earthR = 34, marsR = 56, jupiterR = 86;
            ring(g, cx, cy, earthR, 0xFF2C3446);
            ring(g, cx, cy, marsR, 0xFF2C3446);
            ring(g, cx, cy, jupiterR, 0xFF2C3446);
            int[] earth = onOrbit(cx, cy, earthR, t * 2 * Math.PI / 8 + 0.4);
            int[] mars = onOrbit(cx, cy, marsR, t * 2 * Math.PI / 15 + 2.2);
            int[] jupiter = onOrbit(cx, cy, jupiterR, t * 2 * Math.PI / 95 + 4.0);
            int[] moon = onOrbit(earth[0], earth[1], 7, t * 2 * Math.PI * 1.2);
            int[] io = onOrbit(jupiter[0], jupiter[1], 9, t * 2 * Math.PI * 3);
            dot(g, earth[0], earth[1], 3, 0xFF3E8EE8);
            dot(g, moon[0], moon[1], 1, Planet.MOON.color);
            dot(g, mars[0], mars[1], 2, Planet.MARS.color);
            dot(g, jupiter[0], jupiter[1], 5, 0xFFD8B080);
            dot(g, io[0], io[1], 1, Planet.IO.color);
            label(g, Component.translatable("planet.factoryascent.earth"), earth[0], earth[1] + 5);
            label(g, Planet.MOON.displayName(), moon[0], moon[1] - 11);
            label(g, Planet.MARS.displayName(), mars[0], mars[1] + 4);
            label(g, Component.translatable("planet.factoryascent.jupiter"), jupiter[0], jupiter[1] + 7);
            label(g, Planet.IO.displayName(), io[0], io[1] - 11);
            // you are here
            int[] here = where(chart.here(), earth, moon, mars, io);
            if (here != null && (mc.player == null || mc.player.tickCount / 10 % 2 == 0)) {
                g.fill(here[0] - 5, here[1], here[0] - 2, here[1] + 1, 0xFF60FF60);
                g.fill(here[0] + 3, here[1], here[0] + 6, here[1] + 1, 0xFF60FF60);
                g.fill(here[0], here[1] - 5, here[0] + 1, here[1] - 2, 0xFF60FF60);
                g.fill(here[0], here[1] + 3, here[0] + 1, here[1] + 6, 0xFF60FF60);
            }
            // stations: little squares next to their world
            for (StationPayloads.ChartStation s : chart.stations()) {
                int[] at = where(s.dimension(), earth, moon, mars, io);
                if (at == null) continue;
                int ox = 4 + Math.floorMod(s.x() / 64, 5), oy = -3 - Math.floorMod(s.z() / 64, 4);
                g.fill(at[0] + ox, at[1] + oy, at[0] + ox + 2, at[1] + oy + 2, s.own() ? 0xFF60FF60 : 0xFFFFA040);
            }
            // list
            int lx = x + MAP + 14, ly = y + 18;
            g.text(font, Component.translatable("gui.factoryascent.chart.stations", chart.stations().size()), lx, ly, TEXT, false);
            ly += 12;
            if (chart.stations().isEmpty()) {
                for (FormattedCharSequence l : font.split(Component.translatable("gui.factoryascent.chart.none"), W - MAP - 24)) {
                    g.text(font, l, lx, ly, MUTED, false);
                    ly += 10;
                }
            }
            for (StationPayloads.ChartStation s : chart.stations()) {
                if (ly > y + H - 26) break;
                g.text(font, fit(font, Component.literal(s.name()), W - MAP - 24), lx, ly, s.own() ? 0xFF80FF80 : 0xFFFFC080, false);
                ly += 9;
                Component where = Component.translatable("gui.factoryascent.chart.where", place(s.dimension()), s.x(), s.z());
                g.text(font, fit(font, where, W - MAP - 24), lx + 4, ly, MUTED, false);
                ly += 11;
            }
            super.extractRenderState(g, mouseX, mouseY, partial);
        }

        private void label(GuiGraphicsExtractor g, Component text, int cx, int y) {
            g.text(font, text, cx - font.width(text) / 2, y, MUTED, false);
        }

        private static Component place(Identifier dim) {
            String path = dim.getPath();
            if (path.equals("orbit")) return Component.translatable("planet.factoryascent.earth_orbit");
            for (Planet p : Planet.values()) {
                if (p.key.identifier().equals(dim)) return p.displayName();
            }
            return Component.literal(path);
        }

        private static int[] where(Identifier dim, int[] earth, int[] moon, int[] mars, int[] io) {
            String path = dim.getPath();
            return switch (path) {
                case "overworld", "orbit", "the_nether", "the_end", "the_deep" -> earth;
                case "moon" -> moon;
                case "mars" -> mars;
                case "io" -> io;
                default -> null;
            };
        }

        private static FormattedCharSequence fit(Font font, Component text, int width) {
            if (font.width(text) <= width) return text.getVisualOrderText();
            return net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text, width));
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
}
