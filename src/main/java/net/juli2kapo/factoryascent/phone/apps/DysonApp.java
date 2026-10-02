package net.juli2kapo.factoryascent.phone.apps;

import net.juli2kapo.factoryascent.dyson.DysonConfig;
import net.juli2kapo.factoryascent.dyson.DysonService;
import net.juli2kapo.factoryascent.dyson.DysonSwarm;
import net.juli2kapo.factoryascent.phone.PhoneApp;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.minecraft.nbt.CompoundTag;

/**
 * Dyson: the team's Dyson Cube project, read-only (what the Dyson Monitor shows): collectors in
 * solar orbit out of the target, the milestones, the swarm's power and what the team's receivers
 * took in the last second. Needs signal.
 */
public final class DysonApp implements PhoneApp {
    @Override
    public String id() {
        return "dyson";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public boolean needsSignal() {
        return true;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public CompoundTag data(PhoneContext ctx) {
        String team = DysonService.teamOf(ctx.server(), ctx.player().getUUID());
        DysonSwarm swarm = DysonSwarm.get(ctx.server());
        long collectors = swarm.collectors(team);
        CompoundTag tag = new CompoundTag();
        tag.putLong("collectors", collectors);
        tag.putInt("target", DysonSwarm.target());
        tag.putDouble("completion", swarm.completion(team));
        tag.putLong("power", swarm.swarmPower(team));
        tag.putLong("received", swarm.receivedPerTick(team, ctx.server().overworld().getGameTime()));
        tag.putInt("perCollector", DysonConfig.FE_PER_COLLECTOR.get());
        int[] milestones = new int[DysonService.milestoneCount()];
        for (int i = 0; i < milestones.length; i++) milestones[i] = DysonService.milestonePercent(i);
        tag.putIntArray("milestones", milestones);
        // The team's Dyson Receivers linked to this phone (Link Card + Phone Dock, or sneak-use).
        int linked = 0, formed = 0, active = 0;
        long out = 0;
        for (var link : ctx.memory().of(net.juli2kapo.factoryascent.phone.PhoneMemory.MACHINE)) {
            var level = ctx.server().getLevel(link.pos().dimension());
            if (level == null || !level.isLoaded(link.pos().pos())) {
                if (link.block().contains("dyson_receiver")) linked++;
                continue;
            }
            if (!(level.getBlockEntity(link.pos().pos()) instanceof net.juli2kapo.factoryascent.dyson.DysonReceiverBlockEntity r)) continue;
            linked++;
            if (r.isFormed()) formed++;
            if (r.active()) active++;
            out += r.lastOut();
        }
        tag.putInt("receivers", linked);
        tag.putInt("receiversFormed", formed);
        tag.putInt("receiversActive", active);
        tag.putLong("receiversOut", out);
        return tag;
    }
}
