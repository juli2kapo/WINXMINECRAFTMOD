package net.juli2kapo.factoryascent.phone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every team's chat history (the phone's Team app), by team key: the last
 * {@link PhoneConfig#CHAT_HISTORY} messages each. Stored in the overworld's data storage.
 */
public final class TeamChat extends SavedData {
    /** One message: who sent it (id and name at the time), the text, and the world day/time it was sent. */
    public record Message(UUID sender, String name, String text, long time) {
        static final Codec<Message> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("sender").forGetter(Message::sender),
                Codec.STRING.fieldOf("name").forGetter(Message::name),
                Codec.STRING.fieldOf("text").forGetter(Message::text),
                Codec.LONG.fieldOf("time").forGetter(Message::time)
        ).apply(i, Message::new));
    }

    static final Codec<TeamChat> CODEC = Codec.unboundedMap(Codec.STRING, Message.CODEC.listOf())
            .xmap(TeamChat::new, c -> c.chats);
    private static final SavedDataType<TeamChat> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "team_chat"), () -> new TeamChat(Map.of()), CODEC);

    private final Map<String, List<Message>> chats = new HashMap<>();

    private TeamChat(Map<String, List<Message>> stored) {
        stored.forEach((team, list) -> chats.put(team, new ArrayList<>(list)));
    }

    public static TeamChat get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The team's messages, oldest first. */
    public List<Message> messages(String team) {
        return List.copyOf(chats.getOrDefault(team, List.of()));
    }

    public void add(String team, Message message) {
        List<Message> list = chats.computeIfAbsent(team, k -> new ArrayList<>());
        list.add(message);
        int max = PhoneConfig.CHAT_HISTORY.get();
        while (list.size() > max) list.removeFirst();
        setDirty();
    }
}
