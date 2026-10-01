package net.juli2kapo.factoryascent.xdim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.xdim.LinkTier.Resource;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Moves things between the endpoints of every channel, once a server tick: each endpoint's
 * outgoing buffers go to the incoming buffers of the other endpoints on its channel that receive
 * that resource (round-robin), at the rate of the lower tier of the two, as far as the lower tier
 * reaches. A Quantum link pays FE from the sender's operating buffer (then the receiver's).
 */
public final class XdimService {
    private static int roundRobin;

    private XdimService() {}

    public static void tick(MinecraftServer server) {
        XdimNetwork net = XdimNetwork.get(server);
        Map<Integer, List<LinkBlockEntity>> byChannel = new HashMap<>();
        List<XdimNetwork.Endpoint> gone = new ArrayList<>();
        for (XdimNetwork.Endpoint e : net.endpoints()) {
            LinkBlockEntity link = loaded(server, e.pos());
            if (link == null) {
                // loaded but no endpoint there any more (removed without breaking it): forget it
                ServerLevel level = server.getLevel(e.pos().dimension());
                if (level == null || level.isLoaded(e.pos().pos())) gone.add(e);
                continue;
            }
            if (e.channel() <= 0) continue;
            XdimNetwork.Channel ch = net.channel(e.channel());
            if (ch == null) {
                link.setStatus(LinkBlockEntity.ST_NO_CHANNEL);
                continue;
            }
            if (link.owner() == null || !XdimNetwork.mayUse(server, link.owner(), ch)) {
                link.setStatus(LinkBlockEntity.ST_DENIED);
                continue;
            }
            byChannel.computeIfAbsent(e.channel(), k -> new ArrayList<>()).add(link);
        }
        for (XdimNetwork.Endpoint e : gone) {
            net.remove(e.pos());
            ServerLevel level = server.getLevel(e.pos().dimension());
            if (level != null && e.anchored()) {
                ChunkPos c = ChunkPos.containing(e.pos().pos());
                XdimContent.TICKETS.forceChunk(level, e.pos().pos(), c.x(), c.z(), false, true);
            }
        }
        roundRobin++;
        for (List<LinkBlockEntity> links : byChannel.values()) {
            if (links.size() < 2) {
                links.forEach(l -> l.setStatus(LinkBlockEntity.ST_ALONE));
                continue;
            }
            for (LinkBlockEntity l : links) l.setStatus(l.isActive() ? LinkBlockEntity.ST_ACTIVE : LinkBlockEntity.ST_IDLE);
            for (Resource r : Resource.VALUES) transfer(server, links, r);
        }
    }

    static LinkBlockEntity loaded(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null || !level.isLoaded(pos.pos())) return null;
        return level.getBlockEntity(pos.pos()) instanceof LinkBlockEntity l && !l.isRemoved() ? l : null;
    }

    private static boolean hasOutgoing(LinkBlockEntity l, Resource r) {
        return switch (r) {
            case ITEM -> !ResourceHandlerUtil.isEmpty(l.outItems);
            case FLUID -> !l.outFluid.isEmpty();
            case ENERGY -> l.outEnergy.getAmountAsInt() > 0;
        };
    }

    private static void transfer(MinecraftServer server, List<LinkBlockEntity> links, Resource r) {
        List<LinkBlockEntity> receivers = new ArrayList<>();
        for (LinkBlockEntity l : links) if (l.tier().carries(r) && l.has(r, LinkBlockEntity.RECEIVE)) receivers.add(l);
        if (receivers.isEmpty()) return;
        int n = receivers.size();
        for (LinkBlockEntity s : links) {
            if (!s.tier().carries(r) || !hasOutgoing(s, r)) continue;
            int budget = s.tier().rate(r);
            ResourceKey<Level> sDim = s.globalPos().dimension();
            for (int k = 0; k < n && budget > 0 && hasOutgoing(s, r); k++) {
                LinkBlockEntity t = receivers.get(Math.floorMod(roundRobin + k, n));
                if (t == s) continue;
                ResourceKey<Level> tDim = t.globalPos().dimension();
                LinkTier eff = LinkTier.lower(s.tier(), t.tier());
                if (!eff.carries(r) || !eff.reaches(sDim, tDim)) continue;
                int limit = Math.min(budget, eff.rate(r));
                int moved = move(server, s, t, r, eff, LinkTier.distanceFactor(sDim, tDim), limit);
                budget -= moved;
            }
        }
    }

    /** Moves up to {@code limit} of the resource from s to t; returns how much left s. */
    private static int move(MinecraftServer server, LinkBlockEntity s, LinkBlockEntity t, Resource r, LinkTier eff,
                            int factor, int limit) {
        boolean paid = eff == LinkTier.QUANTUM;
        boolean lava = r == Resource.FLUID && s.outFluid.holds(Fluids.LAVA);
        try (Transaction tx = Transaction.openRoot()) {
            int moved, arrived, cost = 0;
            switch (r) {
                case ITEM -> {
                    int per = paid ? XdimConfig.FE_PER_ITEM.get() * factor : 0;
                    if (per > 0) limit = (int) Math.min(limit, available(s, t) / per);
                    if (limit <= 0) return noPower(s);
                    moved = ResourceHandlerUtil.moveStacking(s.outItems, t.inItems, x -> true, limit, tx);
                    arrived = moved;
                    cost = moved * per;
                }
                case FLUID -> {
                    long perBucket = paid ? (long) XdimConfig.FE_PER_BUCKET.get() * factor : 0;
                    if (perBucket > 0) limit = (int) Math.min(limit, available(s, t) * 1000L / perBucket);
                    if (limit <= 0) return noPower(s);
                    moved = ResourceHandlerUtil.moveStacking(s.outFluid, t.inFluid, x -> true, limit, tx);
                    arrived = moved;
                    cost = (int) ((moved * perBucket + 999) / 1000);
                }
                default -> {
                    int permille = Math.min(1000, XdimConfig.ENERGY_LOSS_PERMILLE.get() * factor);
                    int space = t.inEnergy.getCapacityAsInt() - t.inEnergy.getAmountAsInt();
                    // what arrives is (1 - loss) of what leaves
                    int maxOut = permille >= 1000 ? 0 : (int) Math.min(limit, (long) space * 1000 / (1000 - permille));
                    moved = s.outEnergy.extract(maxOut, tx);
                    arrived = (int) ((long) moved * (1000 - permille) / 1000);
                    if (t.inEnergy.insert(arrived, tx) != arrived) return 0;
                }
            }
            if (moved <= 0) return 0;
            if (cost > 0) {
                int fromS = Math.min(cost, s.power.getAmountAsInt());
                if (fromS > 0 && !s.pay(fromS, tx)) return 0;
                if (cost - fromS > 0 && !t.pay(cost - fromS, tx)) return noPower(s);
            }
            tx.commit();
            s.counted(r, true, moved);
            t.counted(r, false, arrived);
            if (cost > 0) s.paid(cost);
            if (r == Resource.ENERGY && moved > arrived) s.paid(moved - arrived);
            reward(server, s, t, lava);
            return moved;
        }
    }

    private static long available(LinkBlockEntity s, LinkBlockEntity t) {
        return (long) s.power.getAmountAsInt() + t.power.getAmountAsInt();
    }

    private static int noPower(LinkBlockEntity s) {
        s.setStatus(LinkBlockEntity.ST_NO_POWER);
        return 0;
    }

    /** Advancements: lava from the Nether into the Overworld; anything to or from space. */
    private static void reward(MinecraftServer server, LinkBlockEntity s, LinkBlockEntity t, boolean lava) {
        if (server.getTickCount() % 20 != 0) return;
        ResourceKey<Level> sDim = s.globalPos().dimension(), tDim = t.globalPos().dimension();
        if (lava && sDim.equals(Level.NETHER) && tDim.equals(Level.OVERWORLD)) award(server, s, t, "xdim_portal_plumbing");
        if (!sDim.equals(tDim) && (SpaceRules.isAirless(sDim) || SpaceRules.isAirless(tDim))) award(server, s, t, "xdim_space_freight");
        if (!sDim.equals(tDim)) award(server, s, t, "xdim_first_link");
    }

    private static void award(MinecraftServer server, LinkBlockEntity s, LinkBlockEntity t, String key) {
        for (LinkBlockEntity l : new LinkBlockEntity[] {s, t}) {
            if (l.owner() == null) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(l.owner());
            if (p != null) FluidContent.award(p, key);
        }
    }
}
