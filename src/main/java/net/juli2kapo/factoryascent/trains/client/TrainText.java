package net.juli2kapo.factoryascent.trains.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.trains.DieselLocomotive;
import net.juli2kapo.factoryascent.trains.Locomotive;
import net.juli2kapo.factoryascent.trains.SteamLocomotive;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** The read-outs shared by the HUD and the cab screen. */
final class TrainText {
    private TrainText() {}

    static Component speed(float blocksPerTick) {
        return Component.translatable("gui.factoryascent.train.speed", String.format("%.1f", Math.abs(blocksPerTick) * 20 * 3.6));
    }

    static Component throttle(double t) {
        int pct = (int) Math.round(Math.abs(t) * 100);
        if (pct == 0) return Component.translatable("gui.factoryascent.train.idle").withStyle(ChatFormatting.GRAY);
        return t > 0 ? Component.translatable("gui.factoryascent.train.throttle", pct).withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.factoryascent.train.reverse", pct).withStyle(ChatFormatting.GOLD);
    }

    static Component status(int status) {
        ChatFormatting color = switch (status) {
            case Locomotive.STATUS_OK -> ChatFormatting.AQUA;
            case Locomotive.STATUS_STATION -> ChatFormatting.GREEN;
            default -> ChatFormatting.RED;
        };
        return Component.translatable("gui.factoryascent.train.status." + status).withStyle(color);
    }

    /** Fuel lines with a bar fraction and colour each: {text, fraction, color}. */
    record Gauge(Component text, float fraction, int color) {}

    static List<Gauge> gauges(Locomotive loco) {
        List<Gauge> out = new ArrayList<>();
        if (loco instanceof SteamLocomotive) {
            out.add(new Gauge(Component.translatable("gui.factoryascent.train.pressure", loco.gaugeA() / 10), loco.gaugeA() / 1000f, 0xFFE0E0E0));
            out.add(new Gauge(Component.translatable("gui.factoryascent.train.water", loco.gaugeB(), SteamLocomotive.WATER_CAPACITY),
                    loco.gaugeB() / (float) SteamLocomotive.WATER_CAPACITY, 0xFF3F7FD0));
            out.add(new Gauge(Component.translatable("gui.factoryascent.train.fire", loco.gaugeC() / 10), loco.gaugeC() / 1000f, 0xFFE07020));
        } else if (loco instanceof DieselLocomotive) {
            out.add(new Gauge(Component.translatable("gui.factoryascent.train.battery", EnergyUtil.format(loco.gaugeA()),
                    EnergyUtil.format(DieselLocomotive.BATTERY)), loco.gaugeA() / (float) DieselLocomotive.BATTERY, 0xFFE0C040));
            out.add(new Gauge(Component.translatable("gui.factoryascent.train.diesel", loco.gaugeB(), DieselLocomotive.TANK),
                    loco.gaugeB() / (float) DieselLocomotive.TANK, 0xFFD8A531));
        }
        return out;
    }

    static Component lights(boolean on) {
        return Component.translatable("gui.factoryascent.train.lights",
                Component.translatable(on ? "gui.factoryascent.train.on" : "gui.factoryascent.train.off"));
    }
}
