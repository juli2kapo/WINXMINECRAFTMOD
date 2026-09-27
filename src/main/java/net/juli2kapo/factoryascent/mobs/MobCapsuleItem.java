package net.juli2kapo.factoryascent.mobs;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Automation Age: traps a living mob. Hold right-click on it for 3 seconds (keep looking at it, stay
 * within 6 blocks) and it is stored, with all its data, in the capsule. Right-click a block to let it out.
 *
 * <p>The capture runs on the server: a map from player to target, advanced from {@link #onUseTick}
 * and cancelled from {@link #onStopUsing}. While it runs the target is held in place by transient
 * (never saved) speed modifiers, removed whatever way the capture ends.
 */
public class MobCapsuleItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int CAPTURE_TICKS = 60;
    public static final double RANGE = 6.0;
    public static final int EMPTY_STACK = 16;
    /** Ticks the player may aim off the target before the capture fails (keeps small mobs fair). */
    private static final int AIM_GRACE = 4;

    public static final TagKey<EntityType<?>> BLACKLIST = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "capsule_blacklist"));
    private static final Identifier HOLD = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "capsule_hold");
    /** Saved keys that describe where the mob was rather than what it is. */
    private static final String[] PLACE_KEYS = {"UUID", "Pos", "Motion", "fall_distance", "OnGround", "Passengers", "leash"};

    private static final Map<UUID, Capture> CAPTURES = new HashMap<>();

    private static final class Capture {
        final UUID target;
        final long start;
        final Vec3 anchor;
        int missedAim;

        Capture(UUID target, long start, Vec3 anchor) {
            this.target = target;
            this.start = start;
            this.anchor = anchor;
        }
    }

    public MobCapsuleItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- data

    public static @Nullable CapturedMob captured(ItemStack stack) {
        return stack.get(MobContent.CAPTURED_MOB.get());
    }

    public static boolean isFull(ItemStack stack) {
        return stack.has(MobContent.CAPTURED_MOB.get());
    }

    /** Why this entity can't be captured, or empty if it can. */
    public static Optional<Component> refusal(Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.isAlive()) return Optional.of(msg("refused.not_mob"));
        if (entity.is(BLACKLIST) || entity.is(Tags.EntityTypes.BOSSES) || entity.is(Tags.EntityTypes.CAPTURING_NOT_SUPPORTED))
            return Optional.of(msg("refused.blacklist", entity.getName()));
        if (entity.isPassenger() || entity.isVehicle()) return Optional.of(msg("refused.riding", entity.getName()));
        if (entity instanceof Leashable l && l.isLeashed()) return Optional.of(msg("refused.leashed", entity.getName()));
        return Optional.empty();
    }

    /**
     * The store step of a capture: saves the entity's complete data into a new full capsule and removes
     * the entity. Callers must check {@link #refusal} first.
     */
    public static ItemStack store(LivingEntity entity) {
        releaseHold(entity);
        CompoundTag data;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            TagValueOutput out = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            entity.saveWithoutId(out);
            data = out.buildResult();
        }
        for (String key : PLACE_KEYS) data.remove(key);
        ItemStack full = new ItemStack(MobContent.MOB_CAPSULE.get());
        full.set(MobContent.CAPTURED_MOB.get(), new CapturedMob(entity.getType(), data, entity.getHealth(),
                entity.getMaxHealth(), Optional.ofNullable(entity.getCustomName())));
        full.set(DataComponents.MAX_STACK_SIZE, 1);
        entity.discard();
        return full;
    }

    /**
     * The restore step of a release: recreates the stored mob at {@code pos} with a fresh UUID and adds it
     * to the level. Returns null (and adds nothing) if it can't be created or doesn't fit there.
     */
    public static @Nullable Entity restore(ServerLevel level, CapturedMob mob, Vec3 pos, float yRot) {
        Entity entity = mob.type().create(level, new EntitySpawnRequest(EntitySpawnReason.SPAWN_ITEM_USE, true));
        if (entity == null) return null;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            ValueInput in = TagValueInput.create(reporter, level.registryAccess(), mob.data());
            entity.load(in);
        }
        entity.snapTo(pos.x, pos.y, pos.z, yRot, 0f);
        entity.setYHeadRot(yRot);
        if (!level.noCollision(entity)) return null;
        if (entity instanceof Mob m) m.setPersistenceRequired();
        return level.addFreshEntity(entity) ? entity : null;
    }

    // ---------------------------------------------------------------- capture

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isFull(stack)) return InteractionResult.PASS;
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, e -> e instanceof LivingEntity && e != player, RANGE);
        if (!(hit instanceof EntityHitResult eh)) {
            if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(msg("no_target").withStyle(ChatFormatting.GRAY));
            return InteractionResult.PASS;
        }
        return tryStart(player, hand, eh.getEntity());
    }

    /** Right-clicking a mob up close goes through the interact event (before villagers trade or horses mount). */
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(MobContent.MOB_CAPSULE.get()) || isFull(stack)) return;
        InteractionResult result = tryStart(event.getEntity(), event.getHand(), event.getTarget());
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    private static InteractionResult tryStart(Player player, InteractionHand hand, Entity target) {
        if (player.isUsingItem()) return InteractionResult.CONSUME;
        Optional<Component> refused = refusal(target);
        if (refused.isPresent()) {
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(refused.get().copy().withStyle(ChatFormatting.RED));
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.6f);
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        if (player instanceof ServerPlayer sp) {
            CAPTURES.put(sp.getUUID(), new Capture(target.getUUID(), sp.level().getGameTime(), target.position()));
            applyHold((LivingEntity) target);
            sp.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 0.8f, 0.6f);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
        if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return;
        Capture capture = CAPTURES.get(player.getUUID());
        if (capture == null) {
            player.stopUsingItem();
            return;
        }
        Entity found = server.getEntity(capture.target);
        if (!(found instanceof LivingEntity target) || !target.isAlive()) {
            fail(player, found, "failed.died");
            return;
        }
        if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > RANGE * RANGE) {
            fail(player, target, "failed.too_far");
            return;
        }
        HitResult aim = ProjectileUtil.getHitResultOnViewVector(player, e -> e == target, RANGE + 1);
        if (aim instanceof EntityHitResult eh && eh.getEntity() == target) {
            capture.missedAim = 0;
        } else if (++capture.missedAim > AIM_GRACE) {
            fail(player, target, "failed.looked_away");
            return;
        }

        hold(target, capture.anchor);
        int elapsed = (int) (server.getGameTime() - capture.start);
        float progress = Mth.clamp(elapsed / (float) CAPTURE_TICKS, 0f, 1f);
        trapEffects(server, target, elapsed, progress);
        player.sendOverlayMessage(msg("capturing", target.getName(), Math.round(progress * 100))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (elapsed >= CAPTURE_TICKS) succeed(player, target);
    }

    private static void trapEffects(ServerLevel level, LivingEntity target, int elapsed, float progress) {
        double radius = target.getBbWidth() * 0.75 + 0.4;
        double height = target.getBbHeight();
        int points = 10;
        for (int i = 0; i < points; i++) {
            double a = (i / (double) points) * Math.PI * 2 + elapsed * 0.25;
            double x = target.getX() + Math.cos(a) * radius;
            double z = target.getZ() + Math.sin(a) * radius;
            double y = target.getY() + height * ((elapsed % 20) / 20.0);
            level.sendParticles(i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH, x, y, z, 1, 0, 0, 0, 0);
        }
        if (elapsed % 2 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + height / 2, target.getZ(),
                    2 + (int) (progress * 6), target.getBbWidth() / 3, height / 3, target.getBbWidth() / 3, 0.02);
        }
        if (elapsed % 5 == 0) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.6f, 0.5f + 1.5f * progress);
        }
        if (elapsed % 20 == 0) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS,
                    0.3f, 0.8f + progress);
        }
    }

    private static void succeed(ServerPlayer player, LivingEntity target) {
        CAPTURES.remove(player.getUUID());
        InteractionHand hand = player.getUsedItemHand();
        player.stopUsingItem();
        ServerLevel level = player.level();
        Vec3 at = target.position().add(0, target.getBbHeight() / 2, 0);
        Component name = target.getName();
        ItemStack full = store(target);
        ItemStack held = player.getItemInHand(hand);
        if (held.getCount() <= 1) {
            player.setItemInHand(hand, full);
        } else {
            if (!player.hasInfiniteMaterials()) held.shrink(1);
            if (!player.getInventory().add(full)) player.drop(full, false);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 60, 0.3, 0.3, 0.3, 0.3);
        level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.4f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
        player.sendOverlayMessage(msg("captured", name).withStyle(ChatFormatting.GREEN));
    }

    private static void fail(ServerPlayer player, @Nullable Entity target, String reason) {
        CAPTURES.remove(player.getUUID());
        if (target instanceof LivingEntity living) releaseHold(living);
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof MobCapsuleItem) player.stopUsingItem();
        player.sendOverlayMessage(msg(reason).withStyle(ChatFormatting.RED));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** Called whenever the use ends (release, slot change, death…): anything still running failed. */
    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity instanceof ServerPlayer player && CAPTURES.containsKey(player.getUUID())) {
            Capture capture = CAPTURES.get(player.getUUID());
            fail(player, player.level().getEntity(capture.target), "failed.released");
        }
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Capture capture = CAPTURES.remove(event.getEntity().getUUID());
        if (capture != null && event.getEntity().level().getEntity(capture.target) instanceof LivingEntity target) {
            releaseHold(target);
        }
    }

    // ---------------------------------------------------------------- holding the target

    private static void applyHold(LivingEntity target) {
        AttributeModifier stop = new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        for (var attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance != null) instance.addOrUpdateTransientModifier(stop);
        }
    }

    private static void releaseHold(LivingEntity target) {
        for (var attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance != null) instance.removeModifier(HOLD);
        }
    }

    /** Stops the mob's pathing and pulls it back to where the capture began. */
    private static void hold(LivingEntity target, Vec3 anchor) {
        if (target instanceof Mob mob) mob.getNavigation().stop();
        Vec3 pull = anchor.subtract(target.position()).scale(0.3);
        if (pull.length() > 0.5) pull = pull.normalize().scale(0.5);
        boolean flies = target.isNoGravity() || target.getAttribute(Attributes.FLYING_SPEED) != null;
        target.setDeltaMovement(pull.x, flies ? pull.y : Math.min(target.getDeltaMovement().y, 0), pull.z);
        target.hurtMarked = true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return isFull(stack) ? 0 : 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    // ---------------------------------------------------------------- release

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        CapturedMob mob = captured(stack);
        if (mob == null) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        BlockPos at = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() ? pos : pos.relative(context.getClickedFace());
        Player player = context.getPlayer();
        float yaw = player != null ? Mth.wrapDegrees(player.getYRot() + 180f) : 0f;
        Entity released = restore(level, mob, Vec3.atBottomCenterOf(at), yaw);
        if (released == null) {
            if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(msg("no_room").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        Vec3 c = released.position().add(0, released.getBbHeight() / 2, 0);
        level.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 50, 0.3, 0.4, 0.3, 0.5);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.8f);
        if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(msg("released", released.getName()).withStyle(ChatFormatting.GREEN));
        stack.remove(MobContent.CAPTURED_MOB.get());
        stack.set(DataComponents.MAX_STACK_SIZE, EMPTY_STACK); // equal to the default, so the override goes away
        return InteractionResult.SUCCESS_SERVER;
    }

    // ---------------------------------------------------------------- display

    @Override
    public Component getName(ItemStack stack) {
        CapturedMob mob = captured(stack);
        return mob == null ? super.getName(stack)
                : Component.translatable("item.factoryascent.mob_capsule.full", mob.displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        CapturedMob mob = captured(stack);
        if (mob != null) {
            Component type = mob.type().getDescription();
            if (mob.isBaby()) type = Component.translatable("tooltip.factoryascent.mob_capsule.baby", type);
            tooltip.accept(Component.translatable("tooltip.factoryascent.mob_capsule.contains", type).withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.translatable("tooltip.factoryascent.mob_capsule.health",
                    String.format(Locale.ROOT, "%.1f", mob.health()), String.format(Locale.ROOT, "%.1f", mob.maxHealth()))
                    .withStyle(ChatFormatting.GRAY));
            mob.customName().ifPresent(n -> tooltip.accept(
                    Component.translatable("tooltip.factoryascent.mob_capsule.custom_name", n).withStyle(ChatFormatting.GRAY)));
            tooltip.accept(Component.translatable("tooltip.factoryascent.mob_capsule.release").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.accept(Component.translatable("tooltip.factoryascent.mob_capsule").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isFull(stack);
    }

    private static net.minecraft.network.chat.MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.factoryascent.mob_capsule." + key, args);
    }
}
