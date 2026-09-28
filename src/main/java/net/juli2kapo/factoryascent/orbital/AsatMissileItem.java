package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * Anti-Satellite missile: flies from a Launch Pad like a satellite, but on arrival destroys a
 * satellite instead of joining the orbit.
 *
 * <p>It must be programmed first: pick the target in an {@link OrbitalRadarBlock}'s screen (a
 * foreign satellite once the radar has locked it, or one of your own team's satellites right
 * away), then right-click the radar with the missile. For a foreign target the radar must still
 * hold the lock when the missile launches (see {@link LaunchControllerBlockEntity}).
 */
public class AsatMissileItem extends Item {
    /**
     * What a programmed missile flies at.
     *
     * @param satellite the target's {@link Satellite#id()}
     * @param radar     the radar that holds the lock
     * @param label     "Uplink-2 (team Foo)", for the tooltip
     */
    public record Target(UUID satellite, GlobalPos radar, String label) {
        public static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("satellite").forGetter(Target::satellite),
                GlobalPos.CODEC.fieldOf("radar").forGetter(Target::radar),
                Codec.STRING.fieldOf("label").forGetter(Target::label)
        ).apply(i, Target::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Target::satellite,
                GlobalPos.STREAM_CODEC, Target::radar,
                ByteBufCodecs.STRING_UTF8, Target::label,
                Target::new);
    }

    public AsatMissileItem(Properties properties) {
        super(properties);
    }

    public static @Nullable Target target(ItemStack stack) {
        return stack.get(OrbitalContent.ASAT_TARGET.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.asat_missile").withStyle(ChatFormatting.GRAY));
        Target target = target(stack);
        if (target == null) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.asat_unprogrammed").withStyle(ChatFormatting.RED));
        } else {
            BlockPos p = target.radar().pos();
            tooltip.accept(Component.translatable("tooltip.factoryascent.asat_target", target.label()).withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.translatable("tooltip.factoryascent.asat_radar", p.getX(), p.getY(), p.getZ(),
                    OrbitalText.dimensionName(target.radar().dimension())).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.accept(Component.translatable("tooltip.factoryascent.asat_launch",
                LaunchControllerBlockEntity.FUEL_PER_LAUNCH).withStyle(ChatFormatting.DARK_AQUA));
    }
}
