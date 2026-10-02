package net.juli2kapo.factoryascent.phone.dock;

import net.juli2kapo.factoryascent.capsule.DeviceBlock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Phone Dock block: see {@link PhoneDockBlockEntity}. */
public class PhoneDockBlock extends DeviceBlock {
    public static final MapCodec<PhoneDockBlock> CODEC = simpleCodec(PhoneDockBlock::new);

    public PhoneDockBlock(Properties properties) {
        super("phone_dock", properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhoneDockBlockEntity(pos, state);
    }
}
