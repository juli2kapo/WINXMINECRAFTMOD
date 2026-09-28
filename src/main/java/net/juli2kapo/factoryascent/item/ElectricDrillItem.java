package net.juli2kapo.factoryascent.item;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.drill.DrillComponents;
import net.juli2kapo.factoryascent.item.drill.DrillMining;
import net.juli2kapo.factoryascent.item.drill.DrillMode;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Electric Age reward: a pickaxe + shovel that mines faster than netherite while it has charge.
 * Charge it in an Energy Cell's charging slot (or any charger that speaks Forge Energy).
 * <p>
 * Sneak + right-click cycles {@link DrillMode}: single block, a 3x3 area (half speed) or a whole ore vein.
 * Every block, extra ones included, costs {@link #COST_PER_BLOCK} FE.
 */
public class ElectricDrillItem extends Item {
    public static final int CAPACITY = 100_000;
    public static final int COST_PER_BLOCK = 100;
    private static final float SPEED = 14f;
    /** Area mode digs 9 blocks at a time, so each swing is slower. */
    public static final float AREA_SPEED_FACTOR = 0.5f;

    /** Face forced by {@link #drillBlock} (tests, automation); otherwise read from where the player looks. */
    private static @Nullable Direction forcedFace;

    public ElectricDrillItem(Properties properties) {
        super(properties);
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    public static boolean hasCharge(ItemStack stack) {
        return energy(stack) >= COST_PER_BLOCK;
    }

    public static DrillMode mode(ItemStack stack) {
        return stack.getOrDefault(DrillComponents.MODE, DrillMode.SINGLE);
    }

    public static void setMode(ItemStack stack, DrillMode mode) {
        stack.set(DrillComponents.MODE, mode);
    }

    private static boolean drillable(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    /** Blocks a charged drill mines fast and harvests (pickaxe/shovel blocks up to diamond tier). */
    public static boolean effectiveOn(BlockState state) {
        return drillable(state) && !state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!hasCharge(stack) || !drillable(state)) return 1f;
        return mode(stack) == DrillMode.AREA ? SPEED * AREA_SPEED_FACTOR : SPEED;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return hasCharge(stack) && effectiveOn(state);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (level.isClientSide() || owner instanceof Player p && p.getAbilities().instabuild) return true;
        boolean effective = hasCharge(stack) && effectiveOn(state);
        if (state.getDestroySpeed(level, pos) != 0f) {
            stack.set(ModComponents.ENERGY.get(), Math.max(0, energy(stack) - COST_PER_BLOCK));
        }
        DrillMode mode = mode(stack);
        if (effective && mode != DrillMode.SINGLE && !DrillMining.busy() && owner instanceof ServerPlayer player) {
            Direction face = forcedFace != null ? forcedFace : lookedAtFace(level, player, pos);
            DrillMining.harvestExtra(player, stack, pos, state, face, mode);
        }
        return true;
    }

    /** The face of {@code pos} the player is looking at (falls back to the nearest look direction). */
    private static Direction lookedAtFace(Level level, Player player, BlockPos pos) {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return hit.getDirection();
        return Direction.getApproximateNearest(player.getLookAngle()).getOpposite();
    }

    /**
     * Breaks {@code pos} with the drill in {@code player}'s main hand as if they had mined its {@code face}, including
     * the extra blocks of the current mode. Player-independent entry point for tests and automation.
     */
    public static boolean drillBlock(ServerPlayer player, BlockPos pos, Direction face) {
        forcedFace = face;
        try {
            return player.gameMode.destroyBlock(pos);
        } finally {
            forcedFace = null;
        }
    }

    /**
     * Sneak + right-click: looking at a block, cycles the mode (quick switch, as before); in the air,
     * opens the drill screen to pick one.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        boolean atBlock = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() == HitResult.Type.BLOCK;
        if (!atBlock) {
            if (player instanceof ServerPlayer sp) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new net.juli2kapo.factoryascent.ui.ScreenPayloads.OpenDrill(hand.ordinal()));
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            DrillMode next = mode(stack).next();
            setMode(stack, next);
            player.sendOverlayMessage(Component.translatable("message.factoryascent.electric_drill.mode",
                    next.displayName()));
            playModeSound(player, next);
        }
        return InteractionResult.SUCCESS;
    }

    private static void playModeSound(Player player, DrillMode mode) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK,
                SoundSource.PLAYERS, 0.4f, 0.8f + 0.2f * mode.ordinal());
    }

    /**
     * The drill screen picked a mode for the drill in {@code hand}. Only acts on an Electric Drill
     * actually held there. Returns whether the mode was set (for GameTests).
     */
    public static boolean handleModeAction(ServerPlayer player, int handIndex, int modeIndex) {
        InteractionHand hand = net.juli2kapo.factoryascent.ui.ScreenPayloads.hand(handIndex);
        if (hand == null || modeIndex < 0 || modeIndex >= DrillMode.values().length) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ElectricDrillItem)) return false;
        DrillMode mode = DrillMode.values()[modeIndex];
        if (mode(stack) != mode) {
            setMode(stack, mode);
            playModeSound(player, mode);
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
        tooltip.accept(Component.translatable("tooltip.factoryascent.electric_drill.mode_line",
                mode(stack).displayName()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.electric_drill").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.electric_drill.cycle").withStyle(ChatFormatting.DARK_GRAY));
    }
}
