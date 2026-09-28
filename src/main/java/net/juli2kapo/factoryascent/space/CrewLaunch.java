package net.juli2kapo.factoryascent.space;

import java.util.List;
import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Crewed launches: a Crew Capsule mounted on the Launch Pad like any payload (same pad, fuel and
 * clearance rules, but a crewed launch burns {@link SpaceConfig#CREW_FUEL} and climbs longer).
 * A player boards from the Launch Controller screen (the Board button replaces Launch): they are
 * strapped into a {@link RocketSeatEntity} and the countdown starts at once. At the top of the
 * climb the crew goes to orbit ({@link Orbit#arrive}); the capsule is used up. Nobody checks for
 * a suit: without one the astronaut still goes, and dies up there (the screen warns first).
 *
 * <p>Everything here is called from small hooks in {@code orbital/LaunchControllerBlockEntity}.
 */
public final class CrewLaunch {
    /** A crewed launch sequence (ticks): the climb lasts 70 ticks instead of 40. */
    public static final int SEQUENCE = 130;

    private CrewLaunch() {}

    public static boolean isCapsule(ItemStack stack) {
        return stack.getItem() instanceof CrewCapsuleItem;
    }

    /** Fuel units the mounted payload needs. */
    public static int fuelCost(ItemStack payload, int normal) {
        return isCapsule(payload) ? SpaceConfig.crewFuel() : normal;
    }

    public static int sequence(ItemStack payload, int normal) {
        return isCapsule(payload) ? SEQUENCE : normal;
    }

    /** The seat of the capsule on this pad, wherever it is on its climb. */
    public static @Nullable RocketSeatEntity seat(Level level, BlockPos pad) {
        AABB column = new AABB(pad).inflate(2).expandTowards(0, 800, 0);
        List<RocketSeatEntity> seats = level.getEntitiesOfClass(RocketSeatEntity.class, column, s -> s.pad().equals(pad) && s.isAlive());
        return seats.isEmpty() ? null : seats.getFirst();
    }

    public static boolean hasCrew(LaunchControllerBlockEntity pad) {
        RocketSeatEntity seat = pad.getLevel() == null ? null : seat(pad.getLevel(), pad.getBlockPos());
        return seat != null && !seat.getPassengers().isEmpty();
    }

    /** Extra launch rule for capsules (null if fine): a capsule only flies with someone aboard. */
    public static @Nullable Component launchProblem(LaunchControllerBlockEntity pad) {
        if (isCapsule(pad.satellite()) && !hasCrew(pad)) {
            return Component.translatable("message.factoryascent.crew_board_first").withStyle(ChatFormatting.YELLOW);
        }
        return null;
    }

    /** Whether this player would survive orbit as they are now (full suit with air). */
    public static boolean suited(ServerPlayer player) {
        return SpaceRules.wearsFullSuit(player) && SuitItems.oxygen(SpaceRules.suitTank(player)) > 0;
    }

    /**
     * The player climbs into the capsule and the countdown starts. Null on success, else why not
     * (the launch rules of the pad apply, plus: one astronaut, standing near the pad).
     */
    public static @Nullable Component board(ServerPlayer player, LaunchControllerBlockEntity pad) {
        if (!(pad.getLevel() instanceof ServerLevel level)) return null;
        if (!isCapsule(pad.satellite())) {
            return Component.translatable("message.factoryascent.crew_no_capsule").withStyle(ChatFormatting.RED);
        }
        if (pad.launching()) return Component.translatable("message.factoryascent.pad_busy").withStyle(ChatFormatting.RED);
        if (hasCrew(pad)) return Component.translatable("message.factoryascent.crew_full").withStyle(ChatFormatting.RED);
        if (player.isPassenger()) return Component.translatable("message.factoryascent.crew_riding").withStyle(ChatFormatting.RED);
        BlockPos p = pad.getBlockPos();
        if (player.level() != level || player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) > 8 * 8) {
            return Component.translatable("message.factoryascent.crew_too_far").withStyle(ChatFormatting.RED);
        }
        RocketSeatEntity seat = RocketSeatEntity.create(level, p);
        level.addFreshEntity(seat);
        if (!player.startRiding(seat, true, true)) {
            seat.discard();
            return Component.translatable("message.factoryascent.crew_riding").withStyle(ChatFormatting.RED);
        }
        Component problem = pad.tryLaunch();
        if (problem != null) {
            seat.release();
            seat.discard();
            return problem;
        }
        level.playSound(null, p, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.7f);
        player.sendSystemMessage(Component.translatable("message.factoryascent.crew_boarded").withStyle(ChatFormatting.AQUA));
        if (!suited(player)) {
            player.sendSystemMessage(Component.translatable("message.factoryascent.crew_no_suit").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        return null;
    }

    /** Top of the climb: the crew goes to orbit (or, if orbit isn't there, back down safely). */
    public static void arrive(ServerLevel level, LaunchControllerBlockEntity pad) {
        RocketSeatEntity seat = seat(level, pad.getBlockPos());
        if (seat == null) return;
        List<Entity> crew = List.copyOf(seat.getPassengers());
        seat.release();
        for (Entity e : crew) {
            if (!(e instanceof ServerPlayer player)) continue;
            if (!Orbit.arrive(player, level.dimension(), pad.getBlockPos())) {
                player.sendSystemMessage(Component.translatable("message.factoryascent.orbit_unavailable").withStyle(ChatFormatting.RED));
                BlockPos p = pad.getBlockPos();
                player.teleportTo(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100));
            }
        }
        seat.discard();
    }

    /** The controller was broken or the launch scrubbed: everyone out, gently. */
    public static void abort(Level level, BlockPos pad) {
        RocketSeatEntity seat = seat(level, pad);
        if (seat == null) return;
        for (Entity e : List.copyOf(seat.getPassengers())) {
            seat.release();
            if (e instanceof ServerPlayer player) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 30));
            }
        }
        seat.discard();
    }
}
