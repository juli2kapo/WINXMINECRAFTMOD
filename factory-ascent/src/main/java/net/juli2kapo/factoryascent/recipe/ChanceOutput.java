package net.juli2kapo.factoryascent.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;

/** A secondary output produced with some probability (crusher byproducts). */
public record ChanceOutput(ItemStackTemplate item, float chance) {
    public static final Codec<ChanceOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(ChanceOutput::item),
            Codec.floatRange(0f, 1f).fieldOf("chance").forGetter(ChanceOutput::chance)
    ).apply(i, ChanceOutput::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChanceOutput> STREAM_CODEC = StreamCodec.composite(
            ItemStackTemplate.STREAM_CODEC, ChanceOutput::item,
            ByteBufCodecs.FLOAT, ChanceOutput::chance,
            ChanceOutput::new);
}
