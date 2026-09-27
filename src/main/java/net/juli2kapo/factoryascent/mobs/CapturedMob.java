package net.juli2kapo.factoryascent.mobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;

/**
 * What a full Mob Capsule holds: the entity type plus its complete saved data (everything
 * {@code Entity.saveWithoutId} writes, minus UUID, position and motion), and a small summary
 * (health, custom name) so the tooltip doesn't have to decode the data.
 */
public record CapturedMob(EntityType<?> type, CompoundTag data, float health, float maxHealth,
                          Optional<Component> customName) {
    public static final Codec<CapturedMob> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityType.CODEC.fieldOf("type").forGetter(CapturedMob::type),
            CompoundTag.CODEC.fieldOf("data").forGetter(CapturedMob::data),
            Codec.FLOAT.fieldOf("health").forGetter(CapturedMob::health),
            Codec.FLOAT.fieldOf("max_health").forGetter(CapturedMob::maxHealth),
            ComponentSerialization.CODEC.optionalFieldOf("custom_name").forGetter(CapturedMob::customName)
    ).apply(i, CapturedMob::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapturedMob> STREAM_CODEC = StreamCodec.composite(
            EntityType.STREAM_CODEC, CapturedMob::type,
            ByteBufCodecs.COMPOUND_TAG, CapturedMob::data,
            ByteBufCodecs.FLOAT, CapturedMob::health,
            ByteBufCodecs.FLOAT, CapturedMob::maxHealth,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, CapturedMob::customName,
            CapturedMob::new);

    /** The name shown on the capsule: the custom name if any, otherwise the type's name. */
    public Component displayName() {
        return customName.orElseGet(type::getDescription);
    }

    public boolean isBaby() {
        return data.getIntOr("Age", 0) < 0 || data.getBooleanOr("IsBaby", false);
    }
}
