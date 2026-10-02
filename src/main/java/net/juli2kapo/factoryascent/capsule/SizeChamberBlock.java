package net.juli2kapo.factoryascent.capsule;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Size Chamber block: see {@link SizeChamberBlockEntity}. */
public class SizeChamberBlock extends DeviceBlock {
    public static final MapCodec<SizeChamberBlock> CODEC = simpleCodec(SizeChamberBlock::new);

    public SizeChamberBlock(Properties properties) {
        super("size_chamber", properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SizeChamberBlockEntity(pos, state);
    }
}
