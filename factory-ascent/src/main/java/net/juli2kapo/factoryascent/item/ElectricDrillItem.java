package net.juli2kapo.factoryascent.item;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electric Age reward: a pickaxe + shovel that mines faster than netherite while it has charge.
 * Charge it in an Energy Cell's charging slot (or any charger that speaks Forge Energy).
 */
public class ElectricDrillItem extends Item {
    public static final int CAPACITY = 100_000;
    public static final int COST_PER_BLOCK = 100;
    private static final float SPEED = 14f;

    public ElectricDrillItem(Properties properties) {
        super(properties);
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    private static boolean charged(ItemStack stack) {
        return energy(stack) >= COST_PER_BLOCK;
    }

    private static boolean drillable(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return charged(stack) && drillable(state) ? SPEED : 1f;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return charged(stack) && drillable(state) && !state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0f
                && !(owner instanceof Player p && p.getAbilities().instabuild)) {
            stack.set(ModComponents.ENERGY.get(), Math.max(0, energy(stack) - COST_PER_BLOCK));
        }
        return true;
    }

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
        tooltip.accept(Component.translatable("tooltip.factoryascent.electric_drill").withStyle(ChatFormatting.DARK_GRAY));
    }
}
