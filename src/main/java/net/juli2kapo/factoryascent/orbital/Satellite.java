package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One satellite in orbit.
 *
 * @param id         stable id: what deorbit orders, radar locks and missiles refer to
 * @param dimension  the dimension it covers (the one it was launched from)
 * @param launchTime overworld game time of the launch
 * @param launcher   who mounted it on the pad
 */
public record Satellite(UUID id, SatelliteType type, ResourceKey<Level> dimension, long launchTime, String name, UUID launcher) {
    /** Satellites saved before ids existed get a fresh one on load (the registry then saves it). */
    public static final Codec<Satellite> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("id").forGetter(s -> Optional.of(s.id())),
            SatelliteType.CODEC.fieldOf("type").forGetter(Satellite::type),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Satellite::dimension),
            Codec.LONG.fieldOf("launch_time").forGetter(Satellite::launchTime),
            Codec.STRING.fieldOf("name").forGetter(Satellite::name),
            UUIDUtil.CODEC.fieldOf("launcher").forGetter(Satellite::launcher)
    ).apply(i, (id, type, dim, time, name, launcher) -> new Satellite(id.orElseGet(UUID::randomUUID), type, dim, time, name, launcher)));

    /** A new satellite with a fresh id. */
    public Satellite(SatelliteType type, ResourceKey<Level> dimension, long launchTime, String name, UUID launcher) {
        this(UUID.randomUUID(), type, dimension, launchTime, name, launcher);
    }
}
