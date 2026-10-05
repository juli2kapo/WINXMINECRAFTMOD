package net.juli2kapo.factoryascent.trains;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * A train: its vehicles in order along the couplings (one end to the other), whether every coupled
 * vehicle is loaded, and its master (the one that moves; the rest follow). The master is the
 * locomotive somebody drives, else the first locomotive, else the first vehicle. An incomplete
 * train (a coupled vehicle sits in an unloaded chunk) has no master and stands still, so a train
 * is never torn apart by chunk loading.
 */
public record Consist(List<RollingStock> members, boolean complete, @Nullable RollingStock master) {

    /** The train a vehicle belongs to (just itself when it is not coupled). */
    public static Consist of(RollingStock any) {
        Deque<RollingStock> list = new ArrayDeque<>();
        list.add(any);
        Set<UUID> seen = new HashSet<>();
        seen.add(any.getUUID());
        boolean complete = true;
        for (boolean end : new boolean[] {true, false}) {
            RollingStock prev = any;
            UUID nextId = any.link(end);
            while (nextId != null) {
                RollingStock next = resolve(prev, nextId);
                if (next == null) {
                    complete = false;
                    break;
                }
                Boolean back = next.endLinkedTo(prev);
                if (back == null || !seen.add(next.getUUID())) {
                    // a one-sided or looping link: forget it on our side
                    Boolean mine = prev.endLinkedTo(next);
                    if (mine != null && back == null) prev.setLink(mine, null);
                    break;
                }
                if (end) list.addFirst(next);
                else list.addLast(next);
                nextId = next.link(!back);
                prev = next;
            }
        }
        List<RollingStock> members = new ArrayList<>(list);
        return new Consist(members, complete, complete ? pickMaster(members) : null);
    }

    private static @Nullable RollingStock resolve(RollingStock from, UUID id) {
        if (!(from.level() instanceof ServerLevel server)) return null;
        return server.getEntity(id) instanceof RollingStock r && r.isAlive() ? r : null;
    }

    private static RollingStock pickMaster(List<RollingStock> members) {
        for (RollingStock r : members) if (r instanceof Locomotive l && l.driver() != null) return r;
        for (RollingStock r : members) if (r.isLocomotive()) return r;
        return members.getFirst();
    }

    /** Whether two vehicles are coupled into the same train (directly or through others). */
    public static boolean sameTrain(RollingStock a, RollingStock b) {
        if (a == b) return true;
        if (!a.hasLinks() || !b.hasLinks()) return false;
        if (a.level().isClientSide()) return true; // the client doesn't know couplings: assume so (no client pushes)
        return of(a).members().contains(b);
    }

    public int size() {
        return members.size();
    }

    public boolean hasLocomotive() {
        return members.stream().anyMatch(RollingStock::isLocomotive);
    }
}
