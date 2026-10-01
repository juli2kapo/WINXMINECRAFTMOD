package net.juli2kapo.factoryascent.phone.apps;

import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.orbital.OrbitRegistry;
import net.juli2kapo.factoryascent.orbital.SatelliteType;
import net.juli2kapo.factoryascent.orbital.SurveyData;
import net.juli2kapo.factoryascent.orbital.SurveyService;
import net.juli2kapo.factoryascent.phone.PhoneApp;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Map: the team's survey map of this dimension, over the uplink ({@link SurveyService#openRemote}).
 * The app shows whether a Survey Satellite is imaging and how much is mapped, and opens the full
 * survey map; closing the map returns to the phone.
 */
public final class MapApp implements PhoneApp {
    @Override
    public String id() {
        return "map";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean needsSignal() {
        return true;
    }

    @Override
    public CompoundTag data(PhoneContext ctx) {
        String team = FactoryTeams.get(ctx.server()).teamOf(ctx.player().getUUID());
        var dim = ctx.player().level().dimension();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("surveying", OrbitRegistry.get(ctx.server()).has(team, dim, SatelliteType.SURVEY));
        tag.putInt("chunks", SurveyData.get(ctx.server(), team, dim).count());
        tag.putInt("x", ctx.player().getBlockX());
        tag.putInt("z", ctx.player().getBlockZ());
        tag.putString("dim", dim.identifier().toString());
        return tag;
    }

    @Override
    public @Nullable Component action(PhoneContext ctx, String action, CompoundTag args) {
        if (!action.equals("launch")) return null;
        Component problem = SurveyService.openRemote(ctx.player());
        return problem == null ? null : problem.copy().withStyle(ChatFormatting.RED);
    }
}
