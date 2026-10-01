package net.juli2kapo.factoryascent.phone.apps;

import net.juli2kapo.factoryascent.phone.PhoneApp;
import net.juli2kapo.factoryascent.phone.PhoneConfig;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.juli2kapo.factoryascent.phone.PhoneDevices;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Power: the linked energy networks (sneak-use the phone on any power cable of a network): stored
 * energy, average FE/t in and out, the network's rate. Same reach rule as the Machines app.
 */
public final class PowerApp implements PhoneApp {
    @Override
    public String id() {
        return "power";
    }

    @Override
    public int order() {
        return 50;
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
    public CompoundTag data(PhoneContext ctx) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (PhoneMemory.Link link : ctx.memory().of(PhoneMemory.POWER)) list.add(PhoneDevices.power(ctx.player(), link, ctx.signal()));
        tag.put("networks", list);
        tag.putInt("max", PhoneConfig.MAX_POWER.get());
        return tag;
    }
}
