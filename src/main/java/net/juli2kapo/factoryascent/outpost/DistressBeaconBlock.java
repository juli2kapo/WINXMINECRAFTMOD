package net.juli2kapo.factoryascent.outpost;

import com.mojang.serialization.MapCodec;
import java.util.UUID;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.space.SpaceContent;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Distress Beacon: lit on a planet, it calls the placer's team. Their shuttles see it in the
 * cockpit's Navigation panel ({@link DistressBeacons}) and a cruise to it comes down right next to
 * it. Breaking it puts it out.
 */
public class DistressBeaconBlock extends Block implements DescribedBlock {
    public static final MapCodec<DistressBeaconBlock> CODEC = simpleCodec(DistressBeaconBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);
    private static final DustParticleOptions RED = new DustParticleOptions(0xFF3020, 1.2f);

    public DistressBeaconBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server) || !(placer instanceof ServerPlayer player)) return;
        Planet planet = Planet.of(level);
        if (planet == null) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.beacon_not_planet").withStyle(ChatFormatting.YELLOW));
            return;
        }
        light(server, pos, player);
    }

    /** Lights the beacon for the player's team and tells everyone on it. */
    public static void light(ServerLevel level, BlockPos pos, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        String team = teams.teamOf(player.getUUID());
        DistressBeacons registry = DistressBeacons.get(server);
        // over the team's limit the oldest beacon goes dark (it stays placed; using it lights it again)
        registry.light(new DistressBeacons.Beacon(level.dimension(), pos.immutable(), team,
                player.getGameProfile().name(), level.getGameTime()), OutpostConfig.beaconsPerTeam());
        Planet planet = Planet.of(level);
        Component where = planet == null ? Component.literal(level.dimension().identifier().getPath()) : planet.displayName();
        Component msg = Component.translatable("message.factoryascent.beacon_lit", player.getDisplayName(), where, pos.getX(), pos.getZ())
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (UUID member : teams.members(team)) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p != null) p.sendSystemMessage(msg);
        }
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.6f);
        SpaceContent.award(player, "outpost_beacon");
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        DistressBeacons.get(level.getServer()).remove(level.dimension(), pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (Planet.of(level) == null) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.beacon_not_planet").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        var beacon = DistressBeacons.get(server.getServer()).at(net.minecraft.core.GlobalPos.of(level.dimension(), pos));
        if (beacon == null) {
            light(server, pos, sp); // a beacon placed by a machine (or before this existed): the user lights it
        } else {
            FactoryTeams teams = FactoryTeams.get(server.getServer());
            player.sendOverlayMessage(Component.translatable("message.factoryascent.beacon_calling", teams.displayName(beacon.team()))
                    .withStyle(ChatFormatting.GOLD));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        long t = level.getGameTime();
        if (t % 20 < 10) {
            level.addParticle(RED, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0, 0);
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.1,
                    pos.getY() + 1.1 + random.nextDouble() * 0.4, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.1, 0, 0.45, 0);
        }
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.distress_beacon").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.distress_beacon_how").withStyle(ChatFormatting.DARK_AQUA));
    }
}
