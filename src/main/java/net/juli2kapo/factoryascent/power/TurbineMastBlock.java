package net.juli2kapo.factoryascent.power;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A section of a Wind Turbine's steel tower: stack at least four and put the turbine on top. */
public class TurbineMastBlock extends Block implements DescribedBlock {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public TurbineMastBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.turbine_mast", WindTurbineBlockEntity.MIN_MAST)
                .withStyle(ChatFormatting.GRAY));
    }
}
