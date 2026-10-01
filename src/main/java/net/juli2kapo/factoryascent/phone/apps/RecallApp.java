package net.juli2kapo.factoryascent.phone.apps;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.ender.EnderBeaconBlockEntity;
import net.juli2kapo.factoryascent.ender.EnderContent;
import net.juli2kapo.factoryascent.ender.RecallCharmItem;
import net.juli2kapo.factoryascent.phone.PhoneApp;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.juli2kapo.factoryascent.phone.PhoneService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
 * Recall: shows the Ender Beacon your Recall Charm (anywhere in your inventory) is linked to, and
 * starts the charm's recall channel from the phone: the same channel time, the same rules (taking
 * damage or walking away breaks it), then the charm's own teleport ({@link RecallCharmItem#recall})
 * with its pearl and cooldown. Ender magic needs no signal.
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

    @Override
    public CompoundTag data(PhoneContext ctx) {
        ServerPlayer player = ctx.player();
        CompoundTag tag = new CompoundTag();
        tag.putInt("seconds", Config.RECALL_SECONDS.get());
        ItemStack charm = charm(player);
        tag.putBoolean("charm", !charm.isEmpty());
        if (charm.isEmpty()) return tag;
        GlobalPos target = charm.get(EnderContent.LINKED_BEACON.get());
        tag.putBoolean("linked", target != null);
        if (target == null) return tag;
        BlockPos p = target.pos();
        tag.putInt("x", p.getX());
        tag.putInt("y", p.getY());
        tag.putInt("z", p.getZ());
        tag.putString("dim", target.dimension().identifier().toString());
        tag.putString("name", charm.getOrDefault(EnderContent.LINKED_BEACON_NAME.get(), ""));
        boolean here = target.dimension().equals(player.level().dimension());
        tag.putBoolean("reachable", here || Config.RECALL_CROSS_DIMENSION.get());
        ServerLevel level = ctx.server().getLevel(target.dimension());
        String state = "unknown";
        if (level != null) {
            level.getChunk(p); // like the charm's own screen: load it to look
            if (level.getBlockEntity(p) instanceof EnderBeaconBlockEntity beacon) {
                state = beacon.hasPearl() ? "ready" : "no_pearl";
                if (!beacon.name().isEmpty()) tag.putString("name", beacon.name());
            } else {
                state = "gone";
            }
        }
        tag.putString("state", state);
        tag.putInt("cooldown", Math.round(player.getCooldowns().getCooldownPercent(charm, 0f) * Config.RECALL_COOLDOWN_SECONDS.get()));
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel != null) {
            tag.putInt("channel", channel.total() - channel.left()[0]);
            tag.putInt("channelTotal", channel.total());
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
        Component problem = start(player);
        return problem == null ? Component.translatable("gui.factoryascent.phone.recall.channelling").withStyle(ChatFormatting.LIGHT_PURPLE) : problem;
    }

    /** Starts the recall channel; null on success, else why not. */
    public static @Nullable Component start(ServerPlayer player) {
        ItemStack charm = charm(player);
        if (charm.isEmpty()) return Component.translatable("gui.factoryascent.phone.recall.no_charm").withStyle(ChatFormatting.RED);
        GlobalPos target = charm.get(EnderContent.LINKED_BEACON.get());
        if (target == null) return Component.translatable("message.factoryascent.charm_unlinked").withStyle(ChatFormatting.RED);
        if (!Config.RECALL_CROSS_DIMENSION.get() && !target.dimension().equals(player.level().dimension())) {
            return Component.translatable("message.factoryascent.charm_other_dimension").withStyle(ChatFormatting.RED);
        }
        if (player.getCooldowns().isOnCooldown(charm)) {
            return Component.translatable("gui.factoryascent.phone.recall.cooldown").withStyle(ChatFormatting.RED);
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
            ItemStack charm = charm(player);
            if (charm.getItem() instanceof RecallCharmItem item && c.target().equals(charm.get(EnderContent.LINKED_BEACON.get()))) {
                item.recall(player, charm);
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
