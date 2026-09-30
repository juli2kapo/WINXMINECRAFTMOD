package net.juli2kapo.factoryascent.nuclear;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The passive parts of a fission reactor: the Reactor Casing and Reactor Glass walls, and the two
 * things that go inside: Fuel Channels (each holds one fuel rod's worth of the reactor's fuel) and
 * Control Rods (absorb neutrons; one per four channels gives full control).
 */
public class ReactorPartBlock extends Block implements DescribedBlock {
    public enum Part { CASING, GLASS, FUEL_CHANNEL, CONTROL_ROD }

    private static final VoxelShape COLUMN = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape ROD = Shapes.or(Block.box(4, 0, 4, 12, 16, 12), Block.box(3, 13, 3, 13, 16, 13));
    private final Part part;

    public ReactorPartBlock(Part part, Properties properties) {
        super(properties);
        this.part = part;
    }

    public Part part() {
        return part;
    }

    public boolean isWall() {
        return part == Part.CASING || part == Part.GLASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (part) {
            case FUEL_CHANNEL -> COLUMN;
            case CONTROL_ROD -> ROD;
            default -> Shapes.block();
        };
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbor, Direction direction) {
        return part == Part.GLASS && neighbor.is(this) || super.skipRendering(state, neighbor, direction);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return part == Part.GLASS ? 1.0f : super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return part == Part.GLASS;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        String key = part.name().toLowerCase(java.util.Locale.ROOT);
        tooltip.accept(Component.translatable("tooltip.factoryascent.reactor_" + key).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.reactor_build").withStyle(ChatFormatting.DARK_GRAY));
    }
}
