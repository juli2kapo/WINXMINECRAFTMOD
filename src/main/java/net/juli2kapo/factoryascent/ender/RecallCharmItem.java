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
import org.jspecify.annotations.Nullable;

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
            context.getItemInHand().remove(EnderContent.BEACON_BROKEN.get());
            refreshName(context.getItemInHand(), level.dimension(), context.getClickedPos(), beacon);
            player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_linked").withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, context.getClickedPos(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1f, 1.4f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Sneak-use (in the air): the charm's screen instead of a recall.
        if (player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer sp) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, view(sp, hand, true));
            }
            return InteractionResult.SUCCESS;
        }
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
        if (player instanceof ServerPlayer sp) {
            // The button that got the last failure is still held: stay quiet (its message stays on
            // screen) until it is let go and pressed again. Stopping here also stops the client's channel.
            if (net.juli2kapo.factoryascent.util.HeldUse.stillHeld(sp)) {
                sp.stopUsingItem();
                return InteractionResult.CONSUME;
            }
            // Check the beacon before channelling: no point waiting 5 s to hear it is gone.
            Component problem = problem(sp, target);
            if (problem == null && cooldownLeft(sp) > 0) {
                problem = Component.translatable("gui.factoryascent.phone.recall.cooling", (cooldownLeft(sp) + 19) / 20)
                        .withStyle(ChatFormatting.RED);
            }
            if (problem != null) {
                fail(sp, stack, target, problem, true);
                return InteractionResult.CONSUME;
            }
            stack.remove(EnderContent.BEACON_BROKEN.get());
            level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        return InteractionResult.CONSUME;
    }

    /** Ticks a failed recall blocks the charm, so the reason stays readable. */
    public static final int FAIL_COOLDOWN = 40;

    /**
     * A recall that can't happen: shows why (and keeps showing it: nothing else is reported until the
     * button is pressed again), marks the link broken if the beacon is gone, and blocks the charm briefly.
     */
    private static void fail(ServerPlayer player, ItemStack stack, GlobalPos target, Component why, boolean stopUsing) {
        if (beaconGone(player, target)) {
            stack.set(EnderContent.BEACON_BROKEN.get(), true);
            why = destroyedMessage(target, stack.getOrDefault(EnderContent.LINKED_BEACON_NAME.get(), "")); // with its name
        } else {
            stack.remove(EnderContent.BEACON_BROKEN.get());
        }
        player.sendOverlayMessage(why);
        if (stopUsing) player.stopUsingItem();
        player.getCooldowns().addCooldown(stack, FAIL_COOLDOWN);
        net.juli2kapo.factoryascent.util.HeldUse.hold(player, FAIL_COOLDOWN);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    // ---------------------------------------------------------------- the recall rules (charm and phone)

    /** Server tick count at which each player may recall again (charm or phone: one shared cooldown). */
    private static final java.util.Map<java.util.UUID, Integer> READY_AT = new java.util.HashMap<>();

    /** Ticks until this player may recall again (0 = now). */
    public static int cooldownLeft(ServerPlayer player) {
        Integer at = READY_AT.get(player.getUUID());
        if (at == null) return 0;
        int left = at - player.level().getServer().getTickCount();
        if (left <= 0) READY_AT.remove(player.getUUID());
        return Math.max(0, left);
    }

    public static void forgetCooldown(java.util.UUID player) {
        READY_AT.remove(player);
    }

    private static @Nullable EnderBeaconBlockEntity beacon(ServerPlayer player, GlobalPos target) {
        ServerLevel destination = player.level().getServer().getLevel(target.dimension());
        if (destination == null) return null;
        destination.getChunk(target.pos()); // load it if needed, like the vanilla stasis chamber's pearl
        return destination.getBlockEntity(target.pos()) instanceof EnderBeaconBlockEntity b ? b : null;
    }

    private static boolean beaconGone(ServerPlayer player, GlobalPos target) {
        return player.level().getServer().getLevel(target.dimension()) != null && beacon(player, target) == null;
    }

    /** "Your Ender Beacon (name) at x, y, z (dimension) was destroyed". */
    public static Component destroyedMessage(GlobalPos target, String name) {
        BlockPos p = target.pos();
        Component beacon = name == null || name.isEmpty() ? Component.translatable("block.factoryascent.ender_beacon")
                : Component.literal(name);
        return Component.translatable("message.factoryascent.charm_beacon_destroyed", beacon, p.getX(), p.getY(), p.getZ(),
                target.dimension().identifier().getPath()).withStyle(ChatFormatting.RED);
    }

    /**
     * Why a recall to {@code target} can't happen right now, or null if it can: the dimension rule,
     * the beacon still standing, a pearl in it (creative players need none) and room on top.
     * Loads the beacon's chunk to look. Changes nothing.
     */
    public static @Nullable Component problem(ServerPlayer player, GlobalPos target) {
        if (!Config.RECALL_CROSS_DIMENSION.get() && !target.dimension().equals(player.level().dimension())) {
            return Component.translatable("message.factoryascent.charm_other_dimension").withStyle(ChatFormatting.RED);
        }
        ServerLevel destination = player.level().getServer().getLevel(target.dimension());
        if (destination == null) {
            return Component.translatable("message.factoryascent.charm_beacon_gone").withStyle(ChatFormatting.RED);
        }
        EnderBeaconBlockEntity beacon = beacon(player, target);
        if (beacon == null) return destroyedMessage(target, "");
        BlockPos pos = target.pos();
        if (!beacon.hasPearl() && !player.getAbilities().instabuild) {
            return Component.translatable("message.factoryascent.charm_no_pearl_at", beacon.displayName()).withStyle(ChatFormatting.RED);
        }
        if (!destination.getBlockState(pos.above()).getCollisionShape(destination, pos.above()).isEmpty()
                || !destination.getBlockState(pos.above(2)).getCollisionShape(destination, pos.above(2)).isEmpty()) {
            return Component.translatable("message.factoryascent.charm_blocked").withStyle(ChatFormatting.RED);
        }
        return null;
    }

    /**
     * The teleport itself (charm and phone): checks {@link #problem}, uses up the beacon's pearl,
     * moves the player on top of it and starts the shared cooldown. Returns null on success, else why not.
     */
    public static @Nullable Component teleport(ServerPlayer player, GlobalPos target) {
        Component problem = problem(player, target);
        if (problem != null) return problem;
        EnderBeaconBlockEntity beacon = beacon(player, target);
        if (beacon == null) return destroyedMessage(target, "");
        ServerLevel destination = (ServerLevel) beacon.getLevel();
        BlockPos pos = target.pos();
        // Like a vanilla stasis chamber: the recall triggers the pearl waiting in the beacon, and it is used up.
        beacon.triggerPearl();
        ServerLevel from = player.level();
        from.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 0.4, 0.9, 0.4, 0.2);
        from.playSound(null, player.blockPosition(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        player.teleport(new TeleportTransition(destination, Vec3.atBottomCenterOf(pos.above()), Vec3.ZERO,
                player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
        destination.playSound(null, pos.above(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
        destination.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 60, 0.4, 0.9, 0.4, 0.4);
        player.resetFallDistance();
        READY_AT.put(player.getUUID(), player.level().getServer().getTickCount() + Config.RECALL_COOLDOWN_SECONDS.get() * 20);
        return null;
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

    /** The end of the channel: the teleport, or the reason it can't happen (public for GameTests). */
    public void recall(ServerPlayer player, ItemStack stack) {
        GlobalPos target = stack.get(EnderContent.LINKED_BEACON.get());
        if (target == null) return;
        Component problem = teleport(player, target);
        if (problem != null) {
            fail(player, stack, target, problem, false);
            return;
        }
        stack.remove(EnderContent.BEACON_BROKEN.get());
        if (beacon(player, target) instanceof EnderBeaconBlockEntity beacon) refreshName(stack, target.dimension(), target.pos(), beacon);
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
            String name = stack.get(EnderContent.LINKED_BEACON_NAME.get());
            if (name != null && !name.isEmpty()) {
                tooltip.accept(Component.translatable("tooltip.factoryascent.charm_linked_name", name).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            tooltip.accept(Component.translatable("tooltip.factoryascent.charm_linked", p.getX(), p.getY(), p.getZ(),
                    target.dimension().identifier().getPath()).withStyle(ChatFormatting.LIGHT_PURPLE));
            if (stack.getOrDefault(EnderContent.BEACON_BROKEN.get(), false)) {
                tooltip.accept(Component.translatable("tooltip.factoryascent.charm_broken").withStyle(ChatFormatting.RED));
            }
        }
        tooltip.accept(Component.translatable("tooltip.factoryascent.charm_screen").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.charm_howto", Config.RECALL_SECONDS.get())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---------------------------------------------------------------- screen

    /** Copies the beacon's current name onto a charm linked to it (other stacks are left alone). */
    public static void refreshName(ItemStack stack, net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos,
                                   EnderBeaconBlockEntity beacon) {
        GlobalPos target = stack.get(EnderContent.LINKED_BEACON.get());
        if (!(stack.getItem() instanceof RecallCharmItem) || target == null
                || !target.dimension().equals(dimension) || !target.pos().equals(pos)) return;
        if (beacon.name().isEmpty()) stack.remove(EnderContent.LINKED_BEACON_NAME.get());
        else stack.set(EnderContent.LINKED_BEACON_NAME.get(), beacon.name());
    }

    /** What the charm screen shows for the charm in {@code hand}; looks the beacon up (loading its chunk). */
    public static net.juli2kapo.factoryascent.ui.ScreenPayloads.CharmView view(ServerPlayer player, InteractionHand hand, boolean open) {
        ItemStack stack = player.getItemInHand(hand);
        GlobalPos target = stack.get(EnderContent.LINKED_BEACON.get());
        int recall = Config.RECALL_SECONDS.get(), cooldown = Config.RECALL_COOLDOWN_SECONDS.get();
        boolean cross = Config.RECALL_CROSS_DIMENSION.get();
        if (target == null) {
            return new net.juli2kapo.factoryascent.ui.ScreenPayloads.CharmView(open, hand.ordinal(), false, "", BlockPos.ZERO, "",
                    true, net.juli2kapo.factoryascent.ui.ScreenPayloads.BEACON_UNKNOWN, recall, cooldown, cross);
        }
        int state;
        String name = stack.getOrDefault(EnderContent.LINKED_BEACON_NAME.get(), "");
        ServerLevel destination = player.level().getServer().getLevel(target.dimension());
        if (destination == null) {
            state = net.juli2kapo.factoryascent.ui.ScreenPayloads.BEACON_UNKNOWN;
        } else {
            destination.getChunk(target.pos()); // like a recall: load it to look
            if (destination.getBlockEntity(target.pos()) instanceof EnderBeaconBlockEntity beacon) {
                refreshName(stack, target.dimension(), target.pos(), beacon);
                name = beacon.name();
                state = beacon.hasPearl() ? net.juli2kapo.factoryascent.ui.ScreenPayloads.BEACON_READY
                        : net.juli2kapo.factoryascent.ui.ScreenPayloads.BEACON_NO_PEARL;
            } else {
                state = net.juli2kapo.factoryascent.ui.ScreenPayloads.BEACON_GONE;
            }
        }
        return new net.juli2kapo.factoryascent.ui.ScreenPayloads.CharmView(open, hand.ordinal(), true, name, target.pos(),
                target.dimension().identifier().toString(), target.dimension().equals(player.level().dimension()), state,
                recall, cooldown, cross);
    }

    /**
     * A charm screen button: refresh, or unlink the charm in {@code hand}. Only acts on a Recall
     * Charm actually held there, and answers with a fresh view.
     */
    public static void handleAction(ServerPlayer player, int handIndex, int action) {
        InteractionHand hand = net.juli2kapo.factoryascent.ui.ScreenPayloads.hand(handIndex);
        if (hand == null || !(player.getItemInHand(hand).getItem() instanceof RecallCharmItem)) return;
        applyAction(player, handIndex, action);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, view(player, hand, false));
    }

    /** The change behind {@link #handleAction}, without the reply packet (GameTests call this). */
    public static boolean applyAction(ServerPlayer player, int handIndex, int action) {
        InteractionHand hand = net.juli2kapo.factoryascent.ui.ScreenPayloads.hand(handIndex);
        if (hand == null) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof RecallCharmItem)) return false;
        boolean done = true;
        if (action == net.juli2kapo.factoryascent.ui.ScreenPayloads.CharmAction.UNLINK) {
            done = stack.has(EnderContent.LINKED_BEACON.get());
            stack.remove(EnderContent.LINKED_BEACON.get());
            stack.remove(EnderContent.LINKED_BEACON_NAME.get());
            stack.remove(EnderContent.BEACON_BROKEN.get());
            if (done) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        } else if (action != net.juli2kapo.factoryascent.ui.ScreenPayloads.CharmAction.REFRESH) {
            return false;
        }
        return done;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(EnderContent.LINKED_BEACON.get());
    }
}
