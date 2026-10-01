package net.juli2kapo.factoryascent.outpost;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.juli2kapo.factoryascent.ships.ShipConfig;
import net.juli2kapo.factoryascent.space.planet.Navigation;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.juli2kapo.factoryascent.space.planet.PlanetContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Ascent Module: a one-seat capsule on a small booster, for getting off a planet without a
 * shuttle. Place it on the surface and use it with enough shuttle fuel in your inventory (Rocket
 * Fuel, Hydrolox Cells, Helium-3 Fuel Cells; see {@link Ascent} for the cost): it burns the fuel and
 * climbs into Earth orbit above your launch site, where the station deck's Return Pod takes you
 * the rest of the way down. The module is spent on the way up.
 */
public class AscentModuleBlock extends HorizontalDirectionalBlock implements DescribedBlock {
    public static final MapCodec<AscentModuleBlock> CODEC = simpleCodec(AscentModuleBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public AscentModuleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The shuttle fuels, smallest first: what the module can burn. */
    static List<Item> fuels() {
        return List.of(net.juli2kapo.factoryascent.orbital.OrbitalContent.ROCKET_FUEL.get(), OutpostContent.HYDROLOX_FUEL_CELL.get(),
                PlanetContent.HELIUM_3_FUEL_CELL.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        Navigation.Destination here = Navigation.Destination.at(level.dimension());
        if (Planet.of(level) == null || here == null) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.ascent_not_planet").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        if (OutpostLaunches.launching(sp)) return InteractionResult.SUCCESS;
        int needed = Ascent.fuelNeeded(here, ShipConfig.navCosts(), OutpostConfig.ascentFuelFraction());
        if (!player.hasInfiniteMaterials() && !payFuel(player.getInventory(), needed, sp)) return InteractionResult.SUCCESS;
        level.removeBlock(pos, false);
        OutpostLaunches.launch(sp, pos);
        return InteractionResult.SUCCESS;
    }

    /** Takes the fuel for the climb from the inventory; false (and a message) if it isn't there. */
    static boolean payFuel(Inventory inventory, int needed, ServerPlayer player) {
        List<Item> fuels = fuels();
        int per = ShipConfig.fuelPerItem();
        int[] values = new int[fuels.size()];
        int[] counts = new int[fuels.size()];
        for (int i = 0; i < fuels.size(); i++) {
            values[i] = PlanetContent.shuttleFuelValue(new ItemStack(fuels.get(i)), per);
            counts[i] = inventory.countItem(fuels.get(i));
        }
        int[] take = Ascent.pay(needed, values, counts);
        if (take == null) {
            player.sendSystemMessage(Component.translatable("message.factoryascent.ascent_no_fuel", needed, Ascent.worth(values, counts))
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        for (int i = 0; i < fuels.size(); i++) {
            int left = take[i];
            for (int s = 0; s < inventory.getContainerSize() && left > 0; s++) {
                ItemStack stack = inventory.getItem(s);
                if (!stack.is(fuels.get(i))) continue;
                int n = Math.min(left, stack.getCount());
                stack.shrink(n);
                left -= n;
            }
        }
        inventory.setChanged();
        return true;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.ascent_module").withStyle(ChatFormatting.GRAY));
        List<Component> costs = new ArrayList<>();
        for (Planet p : Planet.values()) {
            int units = Ascent.fuelNeeded(Navigation.Destination.at(p.key), ShipConfig.navCosts(), OutpostConfig.ascentFuelFraction());
            costs.add(Component.translatable("tooltip.factoryascent.ascent_cost", p.displayName(), units));
        }
        for (Component c : costs) tooltip.accept(c.copy().withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.ascent_module_how").withStyle(ChatFormatting.DARK_AQUA));
    }
}
