package net.juli2kapo.factoryascent.trains;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Couples rolling stock: right-click one vehicle, then the one next to it on the same track (its
 * nearest ends are joined). Sneak-right-click a vehicle to uncouple the end you clicked. A train
 * may have at most {@code maxTrainLength} vehicles.
 */
public class CouplerItem extends Item {
    public CouplerItem(Properties properties) {
        super(properties);
    }

    public InteractionResult useOn(RollingStock stock, Player player, InteractionHand hand, Vec3 location) {
        if (!(player.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            boolean front = location.x * stock.front().x + location.z * stock.front().z >= 0;
            RollingStock other = stock.linked(front);
            if (other == null) other = stock.linked(!front);
            if (other == null) {
                tell(player, "message.factoryascent.coupler.no_link", ChatFormatting.YELLOW);
            } else {
                stock.uncouple(other);
                level.playSound(null, stock.getX(), stock.getY() + 0.5, stock.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                tell(player, "message.factoryascent.coupler.uncoupled", ChatFormatting.GREEN);
            }
            stack.remove(TrainContent.COUPLER_TARGET.get());
            return InteractionResult.SUCCESS;
        }
        UUID first = stack.get(TrainContent.COUPLER_TARGET.get());
        if (first == null || first.equals(stock.getUUID()) || !(level.getEntity(first) instanceof RollingStock a) || !a.isAlive()) {
            if (first != null && first.equals(stock.getUUID())) {
                stack.remove(TrainContent.COUPLER_TARGET.get());
                tell(player, "message.factoryascent.coupler.cleared", ChatFormatting.GRAY);
            } else {
                stack.set(TrainContent.COUPLER_TARGET.get(), stock.getUUID());
                player.sendOverlayMessage(Component.translatable("message.factoryascent.coupler.selected", stock.getDisplayName())
                        .withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
        Component why = couple(a, stock);
        stack.remove(TrainContent.COUPLER_TARGET.get());
        if (why != null) {
            player.sendOverlayMessage(why.copy().withStyle(ChatFormatting.RED));
        } else {
            level.playSound(null, stock.getX(), stock.getY() + 0.5, stock.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1f, 0.8f);
            level.playSound(null, stock.getX(), stock.getY() + 0.5, stock.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.5f, 1.6f);
            tell(player, "message.factoryascent.coupler.coupled", ChatFormatting.GREEN);
        }
        return InteractionResult.SUCCESS;
    }

    private static void tell(Player player, String key, ChatFormatting color) {
        player.sendOverlayMessage(Component.translatable(key).withStyle(color));
    }

    /** Couples two vehicles at their facing ends, or says why not. */
    public static @Nullable Component couple(RollingStock a, RollingStock b) {
        if (a == b || a.level() != b.level()) return Component.translatable("message.factoryascent.coupler.too_far");
        TrackWalker.Spot sa = a.spot(), sb = b.spot();
        if (sa == null || sb == null) return Component.translatable("message.factoryascent.coupler.off_track");
        if (Consist.sameTrain(a, b)) return Component.translatable("message.factoryascent.coupler.same_train");
        double spacing = TrainPhysics.spacing(a, b);
        if (a.position().distanceTo(b.position()) > spacing + 1.5) return Component.translatable("message.factoryascent.coupler.too_far");
        Vec3 ab = b.position().subtract(a.position());
        boolean aFront = ab.x * a.front().x + ab.z * a.front().z >= 0;
        boolean bFront = -ab.x * b.front().x - ab.z * b.front().z >= 0;
        if (a.link(aFront) != null || b.link(bFront) != null) return Component.translatable("message.factoryascent.coupler.in_use");
        int size = Consist.of(a).size() + Consist.of(b).size();
        if (size > TrainConfig.maxTrainLength()) {
            return Component.translatable("message.factoryascent.coupler.too_long", TrainConfig.maxTrainLength());
        }
        // they must stand on the same piece of track: walking out of a's end reaches b
        TrackWalker.Result r = TrackWalker.walk(a.level(), sa, aFront == a.frontTowardB, spacing, a);
        if (r.blocked() || r.spot().pos().distanceTo(b.position()) > 1.5) {
            return Component.translatable("message.factoryascent.coupler.not_track");
        }
        a.setLink(aFront, b.getUUID());
        b.setLink(bFront, a.getUUID());
        for (RollingStock r2 : Consist.of(a).members()) r2.setTrainSpeed(0);
        return null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(TrainContent.COUPLER_TARGET.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.coupler").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.coupler.max", TrainConfig.maxTrainLength()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
