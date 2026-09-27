package net.juli2kapo.factoryascent.item.drill;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** How much an Electric Drill digs per block mined. Cycled with sneak + right-click. */
public enum DrillMode implements StringRepresentable {
    /** One block, full speed. */
    SINGLE("single", ChatFormatting.AQUA),
    /** The 3x3 square facing the player, half speed. */
    AREA("area", ChatFormatting.GOLD),
    /** A whole ore vein (same block, up to {@link DrillMining#MAX_VEIN} blocks). */
    VEIN("vein", ChatFormatting.GREEN);

    public static final Codec<DrillMode> CODEC = StringRepresentable.fromEnum(DrillMode::values);
    public static final StreamCodec<ByteBuf, DrillMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)], DrillMode::ordinal);

    private final String name;
    private final ChatFormatting color;

    DrillMode(String name, ChatFormatting color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public DrillMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** "Single", "Area 3×3", "Vein", coloured. */
    public Component displayName() {
        return Component.translatable("tooltip.factoryascent.electric_drill.mode." + name).withStyle(color);
    }
}
