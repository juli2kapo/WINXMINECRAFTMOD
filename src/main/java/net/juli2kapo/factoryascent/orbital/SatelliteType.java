package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** What a satellite does once in orbit over a dimension. */
public enum SatelliteType implements StringRepresentable {
    /** Maps: the Ground Station turns empty maps into filled maps of its surroundings. */
    SURVEY,
    /** Signal: the team has coverage in the whole dimension (wireless terminal, future phone apps). */
    UPLINK;

    public static final Codec<SatelliteType> CODEC = StringRepresentable.fromEnum(SatelliteType::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Survey Satellite" / "Uplink Satellite". */
    public Component displayName() {
        return Component.translatable("item.factoryascent." + getSerializedName() + "_satellite");
    }

    /** Short prefix for automatic names: "Survey" / "Uplink". */
    public Component shortName() {
        return Component.translatable("orbital.factoryascent.type." + getSerializedName());
    }
}
