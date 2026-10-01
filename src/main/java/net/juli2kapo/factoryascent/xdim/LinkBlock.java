package net.juli2kapo.factoryascent.xdim;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.juli2kapo.factoryascent.item.WrenchItem;
import net.juli2kapo.factoryascent.xdim.LinkTier.Resource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * An interdimensional link endpoint: an Ender Link (Automation age) or a Quantum Entangler
 * (Quantum age). Right-click opens its screen (channel, faces, linked endpoints, throughput); the
 * Wrench cycles the clicked face through off / send / receive for everything it carries.
 */
public class LinkBlock extends BaseEntityBlock implements DescribedBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape ENDER_SHAPE = Block.box(1, 0, 1, 15, 16, 15);
    private final LinkTier tier;

    public LinkBlock(LinkTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    public LinkTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new LinkBlock(tier, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return tier == LinkTier.ENDER ? ENDER_SHAPE : super.getShape(state, level, pos, context);
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        String k = "tooltip.factoryascent." + tier.id();
        tooltip.accept(Component.translatable(k).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(k + ".rates", tier.rate(Resource.ITEM) * 20, tier.rate(Resource.FLUID) * 20,
                tier.rate(Resource.ENERGY)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable(k + ".reach").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.accept(Component.translatable("tooltip.factoryascent.xdim_howto").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof LinkBlockEntity link) {
            link.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof WrenchItem)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LinkBlockEntity link)) return InteractionResult.PASS;
        if (!link.mayControl(player)) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.xdim.not_yours"));
            return InteractionResult.FAIL;
        }
        Direction face = hit.getDirection();
        int next = (cycleBase(link, face) + 1) % 3;
        for (Resource r : Resource.VALUES) link.setMode(face, r, next);
        link.sync();
        player.sendOverlayMessage(Component.translatable("message.factoryascent.xdim.face",
                Component.translatable("direction.factoryascent." + face.getName()),
                Component.translatable("gui.factoryascent.xdim.mode." + next)));
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, 0.8f + next * 0.25f);
        return InteractionResult.SUCCESS;
    }

    /** The face's mode for the wrench cycle: what most of its resources are set to. */
    private static int cycleBase(LinkBlockEntity link, Direction face) {
        int[] count = new int[3];
        for (Resource r : Resource.VALUES) if (link.tier().carries(r)) count[link.mode(face, r)]++;
        int best = 0;
        for (int m = 1; m < 3; m++) if (count[m] > count[best]) best = m;
        return best;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof LinkBlockEntity link) {
            XdimPayloads.open(sp, link);
        }
        return InteractionResult.SUCCESS;
    }

    /** Ender particles swirl in towards the core while it transfers; a few drift around while idle. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean active = state.getValue(ACTIVE);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        int n = active ? 6 : (random.nextInt(4) == 0 ? 1 : 0);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 0.9 + random.nextDouble() * 0.5;
            double dx = Math.cos(a) * r, dz = Math.sin(a) * r, dy = (random.nextDouble() - 0.5) * 1.2;
            // portal particles fly from (pos + speed) towards pos: spawn at the core, "speed" = the offset outwards
            level.addParticle(tier == LinkTier.QUANTUM ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL,
                    cx, cy, cz, dx, dy, dz);
        }
        if (active && tier == LinkTier.QUANTUM && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, cx + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.05,
                    cz + (random.nextDouble() - 0.5) * 0.3, 0, 0.05 + random.nextDouble() * 0.05, 0);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == XdimContent.LINK_BE.get() ? (lvl, pos, st, be) -> ((LinkBlockEntity) be).serverTick((ServerLevel) lvl, st) : null;
    }
}
