package io.github.mishkis.orbital_railgun.item;

import io.github.mishkis.orbital_railgun.client.item.OrbitalRailgunRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;

import io.github.mishkis.orbital_railgun.OrbitalRailgun;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.UseCooldown;

public class OrbitalRailgunItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public OrbitalRailgunItem(Item.Properties properties) {
        super(properties.rarity(Rarity.EPIC).stacksTo(1));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 24000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
            return ItemUtils.startUsingInstantly(level, player, hand);
        }

        return InteractionResult.FAIL;
    }

    /** Seconds between two shots of the same gun (the original was 120 s, shared by every railgun). */
    public static final int COOLDOWN_SECONDS = 30;

    /**
     * Starts this gun's cooldown. Every railgun stack gets its own cooldown group (see
     * {@link #inventoryTick}), so firing one gun doesn't lock the others.
     */
    public void shoot(Player player, ItemStack stack) {
        player.getCooldowns().addCooldown(stack, COOLDOWN_SECONDS * 20);
    }

    /** The railgun in the player's hands that isn't cooling down (main hand first), or the main hand stack. */
    public static ItemStack firingStack(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof OrbitalRailgunItem && !player.getCooldowns().isOnCooldown(stack)) return stack;
        }
        return player.getMainHandItem();
    }

    /** Gives each railgun its own cooldown group the first time it sits in an inventory (synced to the client). */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        UseCooldown cooldown = stack.get(DataComponents.USE_COOLDOWN);
        if (cooldown == null || cooldown.cooldownGroup().isEmpty()) {
            stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(COOLDOWN_SECONDS, Optional.of(Identifier.fromNamespaceAndPath(
                    OrbitalRailgun.MOD_ID, "railgun/" + UUID.randomUUID()))));
        }
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private OrbitalRailgunRenderer renderer;

            @Override
            public @Nullable GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new OrbitalRailgunRenderer();
                }

                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
