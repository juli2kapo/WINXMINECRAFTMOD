package net.juli2kapo.factoryascent.ender;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Recall Charm: sneak-use it on an Ender Beacon to link it, then hold use anywhere to channel a
 * recall. The channel takes a few seconds (taking damage breaks it), then the beacon pays the
 * energy and you appear on top of it. A cooldown follows.
 */
public class RecallCharmItem extends Item {
    public RecallCharmItem(Properties properties) {
        super(properties);
    }

    private static int channelTicks() {
        return Config.RECALL_SECONDS.get() * 20;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !player.isShiftKeyDown()
                || !(level.getBlockEntity(context.getClickedPos()) instanceof EnderBeaconBlockEntity beacon)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (!beacon.mayLink(player.getUUID())) {
                player.sendOverlayMessage(Component.translatable("message.factoryascent.beacon_not_yours").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            context.getItemInHand().set(EnderContent.LINKED_BEACON.get(), GlobalPos.of(level.dimension(), context.getClickedPos()));
            player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_linked").withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, context.getClickedPos(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1f, 1.4f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        GlobalPos target = stack.get(EnderContent.LINKED_BEACON.get());
        if (target == null) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_unlinked").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (!Config.RECALL_CROSS_DIMENSION.get() && !target.dimension().equals(level.dimension())) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_other_dimension").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        if (!level.isClientSide()) {
            level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return channelTicks();
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) return;
        float progress = 1f - (float) ticksRemaining / channelTicks();
        server.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                4 + (int) (progress * 12), 0.4, 0.8, 0.4, 0.5 + progress);
        if (ticksRemaining % 20 == 0) {
            server.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS,
                    0.3f + progress * 0.4f, 0.8f + progress);
            int seconds = ticksRemaining / 20;
            player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_channel", seconds)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Taking real damage while channelling breaks the recall: no escaping a fight with it. */
    static void onDamaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getInflictedDamage() <= 0) return;
        ItemStack using = player.getUseItem();
        if (!player.isUsingItem() || !(using.getItem() instanceof RecallCharmItem)) return;
        player.stopUsingItem();
        player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_interrupted").withStyle(ChatFormatting.RED));
        player.getCooldowns().addCooldown(using, 40);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel && entity instanceof ServerPlayer player) {
            recall(player, stack);
        }
        return stack;
    }

    /** The teleport itself, after the channel (public for GameTests). */
    public void recall(ServerPlayer player, ItemStack stack) {
        GlobalPos target = stack.get(EnderContent.LINKED_BEACON.get());
        if (target == null) return;
        ServerLevel destination = player.level().getServer().getLevel(target.dimension());
        BlockPos pos = target.pos();
        if (destination == null) return;
        destination.getChunk(pos); // load it if needed
        if (!(destination.getBlockEntity(pos) instanceof EnderBeaconBlockEntity beacon)) {
            stack.remove(EnderContent.LINKED_BEACON.get());
            player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_beacon_gone").withStyle(ChatFormatting.RED));
            return;
        }
        if (!destination.getBlockState(pos.above()).getCollisionShape(destination, pos.above()).isEmpty()
                || !destination.getBlockState(pos.above(2)).getCollisionShape(destination, pos.above(2)).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_blocked").withStyle(ChatFormatting.RED));
            return;
        }
        // Like a vanilla stasis chamber: the charm triggers the pearl waiting in the beacon, and it is used up.
        if (!beacon.hasPearl() && !player.getAbilities().instabuild) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_no_pearl").withStyle(ChatFormatting.RED));
            return;
        }
        beacon.triggerPearl();
        ServerLevel from = player.level();
        from.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 0.4, 0.9, 0.4, 0.2);
        from.playSound(null, player.blockPosition(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        player.teleport(new TeleportTransition(destination, Vec3.atBottomCenterOf(pos.above()), Vec3.ZERO,
                player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
        destination.playSound(null, pos.above(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
        destination.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 60, 0.4, 0.9, 0.4, 0.4);
        player.resetFallDistance();
        player.getCooldowns().addCooldown(stack, Config.RECALL_COOLDOWN_SECONDS.get() * 20);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GlobalPos target = stack.get(EnderContent.LINKED_BEACON.get());
        if (target == null) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.charm_unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos p = target.pos();
            tooltip.accept(Component.translatable("tooltip.factoryascent.charm_linked", p.getX(), p.getY(), p.getZ(),
                    target.dimension().identifier().getPath()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.accept(Component.translatable("tooltip.factoryascent.charm_howto", Config.RECALL_SECONDS.get())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(EnderContent.LINKED_BEACON.get());
    }
}
