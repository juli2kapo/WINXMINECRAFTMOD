package net.juli2kapo.factoryascent.capsule;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Mob Releaser block: see {@link MobReleaserBlockEntity}. */
public class MobReleaserBlock extends DeviceBlock {
    public static final MapCodec<MobReleaserBlock> CODEC = simpleCodec(MobReleaserBlock::new);

    public MobReleaserBlock(Properties properties) {
        super("mob_releaser", properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobReleaserBlockEntity(pos, state);
    }
}
