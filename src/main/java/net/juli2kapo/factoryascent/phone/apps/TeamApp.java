package net.juli2kapo.factoryascent.phone.apps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.orbital.OrbitalConsole;
import net.juli2kapo.factoryascent.phone.PhoneApp;
import net.juli2kapo.factoryascent.phone.PhoneContent;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.juli2kapo.factoryascent.phone.PhoneDevices;
import net.juli2kapo.factoryascent.phone.PhoneService;
import net.juli2kapo.factoryascent.phone.TeamChat;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * Team: your team's members (who is online) and the team chat. A message goes into the team's
 * history ({@link TeamChat}) and pops up as a notification on every online teammate's phone that
 * has signal; "Team screen" opens the orbital Team screen ({@link OrbitalConsole}) for invites and
 * satellites. Messages need signal on the sender's side too.
 */
public final class TeamApp implements PhoneApp {
    public static final int MAX_LENGTH = 120;
    /** Fewest ticks between two messages from one player. */
    private static final int SPAM_TICKS = 10;
    private static final Map<UUID, Long> LAST_SENT = new HashMap<>();
    private static final Map<UUID, Integer> UNREAD = new HashMap<>();

    @Override
    public String id() {
        return "team";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean needsSignal() {
        return true;
    }

    @Override
    public int badge(PhoneContext ctx) {
        return UNREAD.getOrDefault(ctx.player().getUUID(), 0);
    }

    @Override
    public CompoundTag data(PhoneContext ctx) {
        UNREAD.remove(ctx.player().getUUID());
        MinecraftServer server = ctx.server();
        FactoryTeams teams = FactoryTeams.get(server);
        String key = teams.teamOf(ctx.player().getUUID());
        CompoundTag tag = new CompoundTag();
        tag.putString("team", FactoryTeams.isSolo(key) ? "" : teams.displayName(key));
        ListTag members = new ListTag();
        for (UUID member : teams.members(key)) {
            CompoundTag m = new CompoundTag();
            m.putString("name", teams.playerName(member));
            m.putBoolean("online", server.getPlayerList().getPlayer(member) != null);
            members.add(m);
        }
        tag.put("members", members);
        ListTag messages = new ListTag();
        for (TeamChat.Message msg : TeamChat.get(server).messages(key)) {
            CompoundTag m = new CompoundTag();
            m.putString("name", msg.name());
            m.putString("text", msg.text());
            m.putLong("time", msg.time());
            m.putBoolean("mine", msg.sender().equals(ctx.player().getUUID()));
            messages.add(m);
        }
        tag.put("messages", messages);
        return tag;
    }

    @Override
    public @Nullable Component action(PhoneContext ctx, String action, CompoundTag args) {
        return switch (action) {
            case "send" -> send(ctx.player(), args.getStringOr("text", ""));
            case "screen" -> {
                OrbitalConsole.open(ctx.player());
                yield null;
            }
            default -> null;
        };
    }

    /**
     * Sends a team chat message from {@code player}: validated (length, spam, signal), stored, and
     * delivered to online teammates' phones. Null on success, else why not.
     */
    public static @Nullable Component send(ServerPlayer player, String raw) {
        String text = StringUtil.filterText(raw).trim();
        if (text.isEmpty()) return null;
        if (text.length() > MAX_LENGTH) text = text.substring(0, MAX_LENGTH);
        if (!PhoneDevices.signal(player)) return Component.translatable("message.factoryascent.no_signal").withStyle(ChatFormatting.RED);
        MinecraftServer server = player.level().getServer();
        long now = server.overworld().getGameTime();
        Long last = LAST_SENT.get(player.getUUID());
        if (last != null && now - last < SPAM_TICKS && now >= last) {
            return Component.translatable("gui.factoryascent.phone.team.slow_down").withStyle(ChatFormatting.RED);
        }
        LAST_SENT.put(player.getUUID(), now);
        FactoryTeams teams = FactoryTeams.get(server);
        teams.remember(player);
        String key = teams.teamOf(player.getUUID());
        String name = player.getGameProfile().name();
        TeamChat.get(server).add(key, new TeamChat.Message(player.getUUID(), name, text, server.overworld().getOverworldClockTime()));
        PhoneContent.award(player, "phone_chat");
        List<UUID> members = List.copyOf(teams.members(key));
        for (UUID member : members) {
            if (member.equals(player.getUUID())) continue;
            ServerPlayer other = server.getPlayerList().getPlayer(member);
            if (other == null || !PhoneDevices.signal(other)) continue;
            if (PhoneService.notify(other, "team", Component.literal(name).withStyle(ChatFormatting.AQUA), Component.literal(text), 0)) {
                if (!"team".equals(PhoneService.openApp(member))) UNREAD.merge(member, 1, Integer::sum);
            }
        }
        PhoneService.refreshOpen(server, "team", p -> members.contains(p.getUUID()));
        return null;
    }

    public static void forget(UUID player) {
        LAST_SENT.remove(player);
        UNREAD.remove(player);
    }
}
