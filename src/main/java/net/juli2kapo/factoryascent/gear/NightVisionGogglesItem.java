package net.juli2kapo.factoryascent.gear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * Night-Vision Goggles: an FE helmet. Worn and charged, it keeps Night Vision on (refreshed once a
 * second, long enough that the screen never flickers) for {@link #COST_PER_SECOND} FE a second.
 * Empty goggles are just a leather strap with glass in it.
 */
public class NightVisionGogglesItem extends PoweredItem {
    public static final int CAPACITY = 100_000;
    public static final int COST_PER_SECOND = 20;
    private static final int EFFECT_TICKS = 300;

    public NightVisionGogglesItem(Properties properties) {
        super(CAPACITY, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (slot != EquipmentSlot.HEAD || !(owner instanceof LivingEntity wearer) || level.getGameTime() % 20 != 0) return;
        if (drain(stack, COST_PER_SECOND)) {
            wearer.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_TICKS, 0, true, false, true));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("tooltip.factoryascent.night_vision_goggles", COST_PER_SECOND).withStyle(ChatFormatting.GRAY));
    }
}
