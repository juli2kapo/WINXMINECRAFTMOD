package net.juli2kapo.factoryascent.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's radiation: the absorbed dose (rad) and, from the last check, the dose rate that got
 * through their shielding and the rate around them before shielding (rad/s). Synced to the player
 * for the HUD and the Geiger counter's clicks.
 */
public record RadiationState(float dose, float rate, float exposure) {
    public static final RadiationState NONE = new RadiationState(0, 0, 0);
    public static final MapCodec<RadiationState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("dose").forGetter(RadiationState::dose),
            Codec.FLOAT.optionalFieldOf("rate", 0f).forGetter(RadiationState::rate),
            Codec.FLOAT.optionalFieldOf("exposure", 0f).forGetter(RadiationState::exposure)
    ).apply(i, RadiationState::new));
    public static final StreamCodec<ByteBuf, RadiationState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RadiationState::dose,
            ByteBufCodecs.FLOAT, RadiationState::rate,
            ByteBufCodecs.FLOAT, RadiationState::exposure,
            RadiationState::new);
}
