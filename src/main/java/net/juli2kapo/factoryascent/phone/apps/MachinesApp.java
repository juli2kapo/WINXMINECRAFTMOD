package net.juli2kapo.factoryascent.phone.apps;

import net.juli2kapo.factoryascent.phone.PhoneApp;
import net.juli2kapo.factoryascent.phone.PhoneConfig;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.juli2kapo.factoryascent.phone.PhoneDevices;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Machines: the phone's watch list (sneak-use the phone on a machine, generator or reactor
 * controller to add it) with each one's live status: working, idle (no input), no power, output
 * full, reactor temperature… Works without signal within short range ({@link PhoneConfig#LOCAL_RANGE});
 * farther away it needs the uplink. The background watch ({@code PhoneService.watch}) pushes an
 * alert when a watched machine stops or a reactor overheats.
 */
public final class MachinesApp implements PhoneApp {
    @Override
    public String id() {
        return "machines";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean needsSignal() {
        return false;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public int badge(PhoneContext ctx) {
        int bad = 0;
        for (PhoneMemory.Link link : ctx.memory().of(PhoneMemory.MACHINE)) {
            if (PhoneDevices.machine(ctx.player(), link, ctx.signal()).getIntOr("level", 0) == PhoneDevices.BAD) bad++;
        }
        return bad;
    }

    @Override
    public CompoundTag data(PhoneContext ctx) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (PhoneMemory.Link link : ctx.memory().of(PhoneMemory.MACHINE)) list.add(PhoneDevices.machine(ctx.player(), link, ctx.signal()));
        tag.put("machines", list);
        tag.putInt("max", PhoneConfig.MAX_MACHINES.get());
        tag.putInt("range", PhoneConfig.LOCAL_RANGE.get());
        return tag;
    }
}
