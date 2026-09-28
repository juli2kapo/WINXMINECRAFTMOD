package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** What a satellite does once in orbit over a dimension. */
public enum SatelliteType implements StringRepresentable {
    /** Maps: the Ground Station turns empty maps into filled maps of its surroundings. */
    SURVEY("survey_satellite", ChatFormatting.GREEN),
    /** Signal: the team has coverage in the whole dimension (wireless terminal, future phone apps). */
    UPLINK("uplink_satellite", ChatFormatting.AQUA),
    /**
     * Guardian: intercepts the next anti-satellite missile fired at any of the team's satellites
     * over this dimension, and is used up doing so (see {@link LaunchControllerBlockEntity}).
     */
    DEFENSE("guardian_satellite", ChatFormatting.GOLD);

    public static final Codec<SatelliteType> CODEC = StringRepresentable.fromEnum(SatelliteType::values);

    private final String itemId;
    private final ChatFormatting color;

    SatelliteType(String itemId, ChatFormatting color) {
        this.itemId = itemId;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Id of the item that carries this satellite to orbit ("survey_satellite", "guardian_satellite"). */
    public String itemId() {
        return itemId;
    }

    /** Colour of the type in chat and screens. */
    public ChatFormatting color() {
        return color;
    }

    /** "Survey Satellite" / "Uplink Satellite" / "Guardian Satellite". */
    public Component displayName() {
        return Component.translatable("item.factoryascent." + itemId);
    }

    /** Short prefix for automatic names: "Survey" / "Uplink" / "Guardian". */
    public Component shortName() {
        return Component.translatable("orbital.factoryascent.type." + getSerializedName());
    }
}
