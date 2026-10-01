package net.juli2kapo.factoryascent.phone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What a Factory Phone remembers (the {@code factoryascent:phone} item component): its linked
 * devices and its settings. Immutable; every change makes a new value.
 *
 * @param links     linked devices (one storage terminal, watched machines, energy networks)
 * @param sound     play the ringtone with notifications
 * @param alerts    push notifications at all (machine alerts and team chat)
 * @param wallpaper home-screen wallpaper index
 * @param ringtone  ringtone index ({@link PhoneSounds})
 */
public record PhoneMemory(List<Link> links, boolean sound, boolean alerts, int wallpaper, int ringtone) {
    public static final int STORAGE = 0, MACHINE = 1, POWER = 2;
    public static final int WALLPAPERS = 4;
    public static final PhoneMemory EMPTY = new PhoneMemory(List.of(), true, true, 0, 0);

    /**
     * One linked device.
     *
     * @param kind  {@link #STORAGE}, {@link #MACHINE} or {@link #POWER}
     * @param block the block's description id when it was linked (shown while it is out of reach)
     */
    public record Link(int kind, GlobalPos pos, String block) {
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("kind").forGetter(Link::kind),
                GlobalPos.CODEC.fieldOf("pos").forGetter(Link::pos),
                Codec.STRING.optionalFieldOf("block", "").forGetter(Link::block)
        ).apply(i, Link::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Link> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Link::kind,
                GlobalPos.STREAM_CODEC, Link::pos,
                ByteBufCodecs.stringUtf8(256), Link::block,
                Link::new);
    }

    public static final Codec<PhoneMemory> CODEC = RecordCodecBuilder.create(i -> i.group(
            Link.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(PhoneMemory::links),
            Codec.BOOL.optionalFieldOf("sound", true).forGetter(PhoneMemory::sound),
            Codec.BOOL.optionalFieldOf("alerts", true).forGetter(PhoneMemory::alerts),
            Codec.INT.optionalFieldOf("wallpaper", 0).forGetter(PhoneMemory::wallpaper),
            Codec.INT.optionalFieldOf("ringtone", 0).forGetter(PhoneMemory::ringtone)
    ).apply(i, PhoneMemory::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhoneMemory> STREAM_CODEC = StreamCodec.composite(
            Link.STREAM_CODEC.apply(ByteBufCodecs.list(64)), PhoneMemory::links,
            ByteBufCodecs.BOOL, PhoneMemory::sound,
            ByteBufCodecs.BOOL, PhoneMemory::alerts,
            ByteBufCodecs.VAR_INT, PhoneMemory::wallpaper,
            ByteBufCodecs.VAR_INT, PhoneMemory::ringtone,
            PhoneMemory::new);

    public static PhoneMemory of(ItemStack stack) {
        return stack.getOrDefault(PhoneContent.MEMORY.get(), EMPTY);
    }

    public void store(ItemStack stack) {
        stack.set(PhoneContent.MEMORY.get(), this);
    }

    public List<Link> of(int kind) {
        return links.stream().filter(l -> l.kind() == kind).toList();
    }

    public @Nullable Link storage() {
        return links.stream().filter(l -> l.kind() == STORAGE).findFirst().orElse(null);
    }

    public @Nullable Link find(GlobalPos pos) {
        return links.stream().filter(l -> l.pos().equals(pos)).findFirst().orElse(null);
    }

    /** Links a device. Storage replaces the old terminal; other kinds append. */
    public PhoneMemory with(Link link) {
        List<Link> out = new ArrayList<>();
        for (Link l : links) {
            if (l.pos().equals(link.pos())) continue;
            if (link.kind() == STORAGE && l.kind() == STORAGE) continue;
            out.add(l);
        }
        out.add(link);
        return new PhoneMemory(List.copyOf(out), sound, alerts, wallpaper, ringtone);
    }

    public PhoneMemory without(GlobalPos pos) {
        return new PhoneMemory(links.stream().filter(l -> !l.pos().equals(pos)).toList(), sound, alerts, wallpaper, ringtone);
    }

    public PhoneMemory withSound(boolean on) {
        return new PhoneMemory(links, on, alerts, wallpaper, ringtone);
    }

    public PhoneMemory withAlerts(boolean on) {
        return new PhoneMemory(links, sound, on, wallpaper, ringtone);
    }

    public PhoneMemory withWallpaper(int index) {
        return new PhoneMemory(links, sound, alerts, Math.floorMod(index, WALLPAPERS), ringtone);
    }

    public PhoneMemory withRingtone(int index) {
        return new PhoneMemory(links, sound, alerts, wallpaper, Math.floorMod(index, PhoneSounds.COUNT));
    }
}
