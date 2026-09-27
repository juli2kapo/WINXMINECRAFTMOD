package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One satellite in orbit.
 *
 * @param dimension  the dimension it covers (the one it was launched from)
 * @param launchTime overworld game time of the launch
 * @param launcher   who mounted it on the pad
 */
public record Satellite(SatelliteType type, ResourceKey<Level> dimension, long launchTime, String name, UUID launcher) {
    public static final Codec<Satellite> CODEC = RecordCodecBuilder.create(i -> i.group(
            SatelliteType.CODEC.fieldOf("type").forGetter(Satellite::type),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Satellite::dimension),
            Codec.LONG.fieldOf("launch_time").forGetter(Satellite::launchTime),
            Codec.STRING.fieldOf("name").forGetter(Satellite::name),
            UUIDUtil.CODEC.fieldOf("launcher").forGetter(Satellite::launcher)
    ).apply(i, Satellite::new));
}
