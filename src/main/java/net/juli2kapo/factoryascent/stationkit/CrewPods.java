package net.juli2kapo.factoryascent.stationkit;

import net.juli2kapo.factoryascent.space.ReturnPodBlock;
import net.juli2kapo.factoryascent.space.SpaceContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * The crew pod: a Crew Capsule (or an Ascent Module) that reaches orbit where nobody has built a
 * station stays up there as a small floating pod (a {@link ReturnPodBlock}) with the astronaut
 * sitting inside it ({@link PodSeatEntity}, cabin air). Sneak to climb out and stand on it, use
 * it to climb back in, and use it from inside to go home. There is no free floor: build from the pod,
 * or launch a Station Kit.
 */
public final class CrewPods {
    private CrewPods() {}

    /** Where the pod goes: the spot above the launch site, or the first free block above it. */
    public static BlockPos podSpot(Level orbit, BlockPos centre) {
        BlockPos p = centre;
        for (int i = 0; i < 64 && !orbit.getBlockState(p).isAir(); i++) p = p.above();
        return p;
    }

    /** Leaves a pod at {@code pod} and seats the player (already in orbit, at the pod) in it. */
    public static void arrive(ServerLevel orbit, ServerPlayer player, BlockPos pod) {
        if (!(orbit.getBlockState(pod).getBlock() instanceof ReturnPodBlock)) {
            orbit.setBlock(pod, SpaceContent.RETURN_POD.get().defaultBlockState().setValue(ReturnPodBlock.FACING, Direction.SOUTH), 3);
        }
        seat(orbit, player, pod);
        player.sendSystemMessage(Component.translatable("message.factoryascent.crew_pod_arrived").withStyle(ChatFormatting.AQUA));
    }

    /** Sits the player in the pod (cabin air); false if someone is already in it. */
    public static boolean seat(ServerLevel level, ServerPlayer player, BlockPos pod) {
        if (player.isPassenger()) player.stopRiding();
        boolean taken = !level.getEntitiesOfClass(PodSeatEntity.class, new net.minecraft.world.phys.AABB(pod).inflate(1),
                s -> s.pod().equals(pod) && !s.getPassengers().isEmpty()).isEmpty();
        if (taken) return false;
        PodSeatEntity seat = PodSeatEntity.create(level, pod);
        level.addFreshEntity(seat);
        if (!player.startRiding(seat, true, true)) {
            seat.discard();
            return false;
        }
        level.playSound(null, pod, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.2f);
        return true;
    }

    /** Whether the player sits in the pod at {@code pod}. */
    public static boolean seatedIn(ServerPlayer player, BlockPos pod) {
        return player.getVehicle() instanceof PodSeatEntity seat && seat.pod().equals(pod);
    }

    /** Using a pod in orbit from outside: climb in. */
    public static void climbIn(ServerPlayer player, BlockPos pod) {
        if (!seat(player.level(), player, pod)) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.crew_full").withStyle(ChatFormatting.RED));
        } else {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.crew_pod_in").withStyle(ChatFormatting.AQUA));
        }
    }
}
