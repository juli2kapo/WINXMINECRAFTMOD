package net.juli2kapo.factoryascent.phone.apps;

import net.juli2kapo.factoryascent.phone.PhoneApp;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Settings: ringtone on/off and which one, notifications on/off, the wallpaper, and the list of
 * linked devices with an Unlink button each. Works offline.
 */
public final class SettingsApp implements PhoneApp {
    @Override
    public String id() {
        return "settings";
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public boolean needsSignal() {
        return false;
    }

    @Override
    public CompoundTag data(PhoneContext ctx) {
        PhoneMemory memory = ctx.memory();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("sound", memory.sound());
        tag.putBoolean("alerts", memory.alerts());
        tag.putInt("wallpaper", memory.wallpaper());
        tag.putInt("ringtone", memory.ringtone());
        ListTag links = new ListTag();
        for (PhoneMemory.Link link : memory.links()) {
            CompoundTag l = new CompoundTag();
            BlockPos p = link.pos().pos();
            l.putInt("kind", link.kind());
            l.putString("name", link.block());
            l.putInt("x", p.getX());
            l.putInt("y", p.getY());
            l.putInt("z", p.getZ());
            l.putString("dim", link.pos().dimension().identifier().toString());
            links.add(l);
        }
        tag.put("links", links);
        return tag;
    }

    @Override
    public @Nullable Component action(PhoneContext ctx, String action, CompoundTag args) {
        PhoneMemory memory = ctx.memory();
        switch (action) {
            case "sound" -> ctx.store(memory.withSound(!memory.sound()));
            case "alerts" -> ctx.store(memory.withAlerts(!memory.alerts()));
            case "wallpaper" -> ctx.store(memory.withWallpaper(memory.wallpaper() + 1));
            case "ringtone" -> ctx.store(memory.withRingtone(memory.ringtone() + 1));
            case "unlink" -> {
                int index = args.getIntOr("index", -1);
                if (index < 0 || index >= memory.links().size()) return null;
                PhoneMemory.Link link = memory.links().get(index);
                // the client names the position too, so a stale list can't unlink the wrong device
                if (args.getIntOr("x", 0) != link.pos().pos().getX() || args.getIntOr("z", 0) != link.pos().pos().getZ()) return null;
                ctx.store(memory.without(link.pos()));
                return Component.translatable("message.factoryascent.phone.unlinked", Component.translatable(link.block()))
                        .withStyle(ChatFormatting.YELLOW);
            }
            default -> {
                return null;
            }
        }
        return null;
    }
}
