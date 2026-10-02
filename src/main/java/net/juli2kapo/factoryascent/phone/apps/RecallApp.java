package net.juli2kapo.factoryascent.phone.apps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.ender.EnderBeaconBlockEntity;
import net.juli2kapo.factoryascent.ender.EnderContent;
import net.juli2kapo.factoryascent.ender.RecallCharmItem;
import net.juli2kapo.factoryascent.phone.PhoneApp;
import net.juli2kapo.factoryascent.phone.PhoneConfig;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.juli2kapo.factoryascent.phone.PhoneService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Recall: the Ender Beacons this phone knows (its own beacon links, plus those of any Recall Charms
 * you carry) with name, dimension, position and pearl, and a Recall button that channels a recall
 * to the one shown: the same channel time and rules as the charm (taking damage or walking away
 * breaks it), the same teleport and the same shared cooldown ({@link RecallCharmItem#teleport}).
 * No charm needed. Ender magic needs no signal.
 */
public final class RecallApp implements PhoneApp {
    /** Blocks you may drift from where the channel started. */
    private static final double MAX_DRIFT = 1.5;

    private record Channel(GlobalPos target, Vec3 start, int total, int[] left) {}

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    @Override
    public String id() {
        return "recall";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public boolean needsSignal() {
        return false;
    }

    /** The player's Recall Charm: a linked one if any, else any; empty if none. */
    public static ItemStack charm(ServerPlayer player) {
        ItemStack any = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof RecallCharmItem)) continue;
            if (stack.has(EnderContent.LINKED_BEACON.get())) return stack;
            if (any.isEmpty()) any = stack;
        }
        return any;
    }

    /** One beacon the app can recall to. */
    public record Target(GlobalPos pos, String name, boolean fromCharm) {}

    /**
     * Every beacon this phone can take the player to: the phone's own linked Ender Beacons (linked
     * with a Link Card in a Phone Dock, or by sneak-using the phone on the beacon), then the beacons
     * of Recall Charms in the inventory that the phone doesn't list yet. No charm is needed.
     */
    public static List<Target> targets(ServerPlayer player, ItemStack phone) {
        List<Target> out = new ArrayList<>();
        for (PhoneMemory.Link link : PhoneMemory.of(phone).of(PhoneMemory.BEACON)) {
            out.add(new Target(link.pos(), "", false));
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            GlobalPos pos = stack.getItem() instanceof RecallCharmItem ? stack.get(EnderContent.LINKED_BEACON.get()) : null;
            if (pos == null || out.stream().anyMatch(t -> t.pos().equals(pos))) continue;
            out.add(new Target(pos, stack.getOrDefault(EnderContent.LINKED_BEACON_NAME.get(), ""), true));
        }
        return out;
    }

    @Override
    public CompoundTag data(PhoneContext ctx) {
        ServerPlayer player = ctx.player();
        CompoundTag tag = new CompoundTag();
        tag.putInt("seconds", Config.RECALL_SECONDS.get());
        tag.putInt("max", PhoneConfig.MAX_BEACONS.get());
        tag.putBoolean("charm", !charm(player).isEmpty());
        tag.putInt("cooldown", (RecallCharmItem.cooldownLeft(player) + 19) / 20);
        ListTag list = new ListTag();
        for (Target target : targets(player, ctx.phone())) {
            CompoundTag b = new CompoundTag();
            BlockPos p = target.pos().pos();
            b.putInt("x", p.getX());
            b.putInt("y", p.getY());
            b.putInt("z", p.getZ());
            b.putString("dim", target.pos().dimension().identifier().toString());
            b.putString("name", target.name());
            b.putBoolean("charm", target.fromCharm());
            boolean here = target.pos().dimension().equals(player.level().dimension());
            b.putBoolean("reachable", here || Config.RECALL_CROSS_DIMENSION.get());
            ServerLevel level = ctx.server().getLevel(target.pos().dimension());
            String state = "unknown";
            if (level != null) {
                level.getChunk(p); // like the charm's own screen: load it to look
                if (level.getBlockEntity(p) instanceof EnderBeaconBlockEntity beacon) {
                    state = beacon.hasPearl() ? "ready" : "no_pearl";
                    b.putString("name", beacon.name());
                } else {
                    state = "gone";
                }
            }
            b.putString("state", state);
            list.add(b);
        }
        tag.put("beacons", list);
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel != null) {
            tag.putInt("channel", channel.total() - channel.left()[0]);
            tag.putInt("channelTotal", channel.total());
            BlockPos p = channel.target().pos();
            tag.putIntArray("channelTarget", new int[] {p.getX(), p.getY(), p.getZ()});
            tag.putString("channelDim", channel.target().dimension().identifier().toString());
        }
        return tag;
    }

    @Override
    public @Nullable Component action(PhoneContext ctx, String action, CompoundTag args) {
        ServerPlayer player = ctx.player();
        if (action.equals("cancel")) {
            CHANNELS.remove(player.getUUID());
            return null;
        }
        if (!action.equals("recall")) return null;
        GlobalPos target = chosen(player, ctx.phone(), args);
        if (target == null) return Component.translatable("gui.factoryascent.phone.recall.none").withStyle(ChatFormatting.RED);
        Component problem = start(player, target);
        return problem == null ? Component.translatable("gui.factoryascent.phone.recall.channelling").withStyle(ChatFormatting.LIGHT_PURPLE) : problem;
    }

    /** The beacon the client picked, only if it really is one of this phone's targets. */
    private static @Nullable GlobalPos chosen(ServerPlayer player, ItemStack phone, CompoundTag args) {
        String dim = args.getStringOr("dim", "");
        BlockPos pos = new BlockPos(args.getIntOr("x", 0), args.getIntOr("y", 0), args.getIntOr("z", 0));
        for (Target t : targets(player, phone)) {
            if (t.pos().pos().equals(pos) && t.pos().dimension().identifier().toString().equals(dim)) return t.pos();
        }
        return null;
    }

    /** Starts the recall channel to the first of the player's targets (GameTests); null on success, else why not. */
    public static @Nullable Component start(ServerPlayer player) {
        ItemStack charm = charm(player);
        GlobalPos target = charm.isEmpty() ? null : charm.get(EnderContent.LINKED_BEACON.get());
        if (target == null) return Component.translatable("gui.factoryascent.phone.recall.none").withStyle(ChatFormatting.RED);
        return start(player, target);
    }

    /**
     * Starts the recall channel to {@code target}; null on success, else why not. The beacon is
     * checked first (gone, empty, blocked, other dimension), then the shared recall cooldown.
     */
    public static @Nullable Component start(ServerPlayer player, GlobalPos target) {
        Component problem = RecallCharmItem.problem(player, target);
        if (problem != null) return problem;
        if (RecallCharmItem.cooldownLeft(player) > 0) {
            return Component.translatable("gui.factoryascent.phone.recall.cooling", (RecallCharmItem.cooldownLeft(player) + 19) / 20)
                    .withStyle(ChatFormatting.RED);
        }
        if (CHANNELS.containsKey(player.getUUID())) return null;
        int ticks = Config.RECALL_SECONDS.get() * 20;
        CHANNELS.put(player.getUUID(), new Channel(target, player.position(), ticks, new int[] {ticks}));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.6f);
        return null;
    }

    public static boolean channelling(UUID player) {
        return CHANNELS.containsKey(player);
    }

    /** Every tick: run the channels; teleport through the charm when one completes. */
    public static void tick(MinecraftServer server) {
        if (CHANNELS.isEmpty()) return;
        for (var it = CHANNELS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Channel c = entry.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            if (player.position().distanceTo(c.start()) > MAX_DRIFT) {
                it.remove();
                player.sendOverlayMessage(Component.translatable("gui.factoryascent.phone.recall.moved").withStyle(ChatFormatting.RED));
                PhoneService.refreshOpen(server, "recall", p -> p == player);
                continue;
            }
            int left = --c.left()[0];
            float progress = 1f - (float) left / c.total();
            ServerLevel level = player.level();
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                    4 + (int) (progress * 12), 0.4, 0.8, 0.4, 0.5 + progress);
            if (left % 20 == 0 && left > 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.3f + progress * 0.4f, 0.8f + progress);
                player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_channel", left / 20).withStyle(ChatFormatting.LIGHT_PURPLE));
                PhoneService.refreshOpen(server, "recall", p -> p == player);
            }
            if (left > 0) continue;
            it.remove();
            Component problem = RecallCharmItem.teleport(player, c.target());
            if (problem != null) {
                // The beacon was broken or emptied during the channel: say so, and keep saying it.
                player.sendOverlayMessage(problem);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
            PhoneService.refreshOpen(server, "recall", p -> p == player);
        }
    }

    /** Taking real damage breaks a phone-started recall, like the charm's own. */
    public static void onDamaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getInflictedDamage() <= 0) return;
        if (CHANNELS.remove(player.getUUID()) != null) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.charm_interrupted").withStyle(ChatFormatting.RED));
        }
    }

    public static void forget(UUID player) {
        CHANNELS.remove(player);
    }

    public static void clear() {
        CHANNELS.clear();
    }
}
