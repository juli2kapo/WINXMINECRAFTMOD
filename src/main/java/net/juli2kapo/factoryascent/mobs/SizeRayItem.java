package net.juli2kapo.factoryascent.mobs;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Automation Age: the Minimizer and Maximizer rays. Hold right-click for a second to charge, release to
 * fire a beam that multiplies the first living thing it hits in size (×0.5 or ×2, between 0.25 and 4).
 *
 * <p>The size lives in one permanent modifier ({@link #MODIFIER}) on {@code minecraft:scale}, replaced
 * on every shot, so it is saved with the entity and survives a trip through a Mob Capsule.
 */
public class SizeRayItem extends Item {
    public static final int CAPACITY = 50_000;
    public static final int COST_PER_SHOT = 5_000;
    public static final int CHARGE_TICKS = 20;
    public static final int COOLDOWN = 20;
    public static final double RANGE = 24.0;
    public static final double MIN_SIZE = 0.25;
    public static final double MAX_SIZE = 4.0;
    public static final Identifier MODIFIER = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "size_ray");

    private final double factor;
    private final int color;
    private final String tooltipKey;

    public SizeRayItem(Properties properties, double factor, int color, String tooltipKey) {
        super(properties);
        this.factor = factor;
        this.color = color;
        this.tooltipKey = tooltipKey;
    }

    // ---------------------------------------------------------------- size logic

    /** The multiplier the ray has applied to this entity so far (1 when untouched). */
    public static double sizeFactor(LivingEntity entity) {
        AttributeInstance scale = entity.getAttribute(Attributes.SCALE);
        if (scale == null) return 1.0;
        AttributeModifier mod = scale.getModifier(MODIFIER);
        return mod == null ? 1.0 : 1.0 + mod.amount();
    }

    /**
     * One shot: multiplies the entity's ray size by {@code factor}, clamped to [0.25, 4]. Returns the new
     * factor. At 1.0 the modifier is removed altogether.
     */
    public static double applyShot(LivingEntity entity, double factor) {
        AttributeInstance scale = entity.getAttribute(Attributes.SCALE);
        if (scale == null) return 1.0;
        double next = Mth.clamp(sizeFactor(entity) * factor, MIN_SIZE, MAX_SIZE);
        if (Math.abs(next - 1.0) < 1e-6) {
            scale.removeModifier(MODIFIER);
            return 1.0;
        }
        scale.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER, next - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return next;
    }

    // ---------------------------------------------------------------- energy

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    private static boolean canPay(Player player, ItemStack stack) {
        return player.hasInfiniteMaterials() || energy(stack) >= COST_PER_SHOT;
    }

    // ---------------------------------------------------------------- use

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canPay(player, stack)) {
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.factoryascent.size_ray.no_energy",
                        EnergyUtil.format(COST_PER_SHOT)).withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    private static Vec3 muzzle(LivingEntity user) {
        Vec3 look = user.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        boolean mainRight = user.getUsedItemHand() == InteractionHand.MAIN_HAND
                == (user.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        return user.getEyePosition().add(look.scale(0.9)).add(right.scale(mainRight ? 0.3 : -0.3)).add(0, -0.25, 0);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
        if (!(level instanceof ServerLevel server)) return;
        int elapsed = getUseDuration(stack, user) - ticksRemaining;
        Vec3 m = muzzle(user);
        if (elapsed <= CHARGE_TICKS) {
            float progress = elapsed / (float) CHARGE_TICKS;
            server.sendParticles(new DustParticleOptions(color, 0.6f + progress), m.x, m.y, m.z,
                    1 + (int) (progress * 3), 0.05, 0.05, 0.05, 0);
            if (elapsed % 2 == 0) server.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 1, 0.08, 0.08, 0.08, 0.02);
            if (elapsed % 3 == 0) {
                server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.NOTE_BLOCK_BIT, SoundSource.PLAYERS,
                        0.4f, 0.5f + 1.5f * progress);
            }
            if (elapsed == CHARGE_TICKS) {
                server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        1f, 1.6f);
                if (user instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(Component.translatable("message.factoryascent.size_ray.ready").withStyle(ChatFormatting.AQUA));
                }
            }
        } else if (elapsed % 4 == 0) {
            server.sendParticles(new DustParticleOptions(color, 1.2f), m.x, m.y, m.z, 1, 0.03, 0.03, 0.03, 0);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int ticksRemaining) {
        if (!(user instanceof Player player)) return false;
        int elapsed = getUseDuration(stack, user) - ticksRemaining;
        if (elapsed < CHARGE_TICKS) {
            if (level instanceof ServerLevel server) {
                server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3f, 1.8f);
            }
            return false;
        }
        if (!canPay(player, stack)) return false;
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        if (!(level instanceof ServerLevel server)) return true;
        if (!player.hasInfiniteMaterials()) stack.set(ModComponents.ENERGY.get(), energy(stack) - COST_PER_SHOT);
        fire(server, player);
        return true;
    }

    private void fire(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
        HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB area = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, area,
                e -> e instanceof LivingEntity && e != player && e.isAlive() && !e.isSpectator(), 0.3f);
        if (hit != null) end = hit.getLocation();

        Vec3 from = muzzle(player);
        Vec3 path = end.subtract(from);
        int steps = Math.max(1, (int) (path.length() * 4));
        DustParticleOptions dust = new DustParticleOptions(color, 1.0f);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(path.scale(i / (double) steps));
            level.sendParticles(dust, true, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            if (i % 4 == 0) level.sendParticles(ParticleTypes.END_ROD, true, false, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS,
                0.8f, factor < 1 ? 1.6f : 0.7f);

        if (hit == null) return;
        Entity target = hit.getEntity();
        double size = applyShot((LivingEntity) target, factor);
        Vec3 c = target.position().add(0, target.getBbHeight() / 2, 0);
        level.sendParticles(dust, c.x, c.y, c.z, 30, target.getBbWidth() / 2, target.getBbHeight() / 2, target.getBbWidth() / 2, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 15, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.8f, factor < 1 ? 1.8f : 0.6f);
        if (player instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.translatable("message.factoryascent.size_ray.hit", target.getName(),
                    Math.round(size * 100)).withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    // ---------------------------------------------------------------- display

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.08f + 0.25f * energy(stack) / CAPACITY, 0.9f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.stored_energy",
                EnergyUtil.format(energy(stack)), EnergyUtil.format(CAPACITY)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(tooltipKey).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.size_ray.usage", EnergyUtil.format(COST_PER_SHOT))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
