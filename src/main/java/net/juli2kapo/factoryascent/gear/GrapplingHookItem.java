package net.juli2kapo.factoryascent.gear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bronze Grappling Hook: aim at a block up to {@link #RANGE} blocks away and use it to be yanked
 * towards it: across ravines, up cliffs, out of holes. The launch clears fall damage built up so
 * far; the hook needs a moment ({@link #COOLDOWN} ticks) to be wound in again and wears one
 * durability point per throw.
 */
public class GrapplingHookItem extends Item {
    public static final int RANGE = 24;
    public static final int COOLDOWN = 20;

    public GrapplingHookItem(Properties properties) {
        super(properties);
    }

    /** Where the hook would bite, or null if nothing is in reach. */
    public static BlockHitResult aim(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
        return level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    /** The pull the player gets towards {@code target}: faster for longer throws, with a little lift. */
    public static Vec3 launchVelocity(Vec3 from, Vec3 target) {
        Vec3 to = target.subtract(from);
        double dist = to.length();
        double speed = Math.min(2.6, 0.9 + dist * 0.075);
        Vec3 v = to.normalize().scale(speed);
        return new Vec3(v.x, v.y + 0.25 + Math.min(0.35, dist * 0.012), v.z);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = aim(level, player);
        if (hit.getType() != HitResult.Type.BLOCK) {
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.6f, 0.7f);
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            Vec3 target = hit.getLocation();
            player.setDeltaMovement(launchVelocity(player.position(), target));
            player.hurtMarked = true;
            player.resetFallDistance();
            Vec3 from = player.getEyePosition().subtract(0, 0.3, 0);
            Vec3 line = target.subtract(from);
            int steps = (int) Math.max(4, line.length() * 2);
            for (int i = 0; i <= steps; i++) {
                Vec3 p = from.add(line.scale(i / (double) steps));
                server.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8f, 0.6f);
            level.playSound(null, hit.getBlockPos(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8f, 1.2f);
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
            player.getCooldowns().addCooldown(stack, COOLDOWN);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.grappling_hook", RANGE).withStyle(ChatFormatting.GRAY));
    }
}
