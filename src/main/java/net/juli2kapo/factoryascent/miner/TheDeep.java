package net.juli2kapo.factoryascent.miner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * The Deep: a sealed mining dimension (data pack {@code factoryascent:the_deep}) of solid stone and
 * deepslate, richer in ore than the overworld. Every Ore Miner claims its own chunk column here
 * and digs out the real ore blocks in it, so a miner's output always comes from somewhere and a
 * claim eventually runs dry (the miner then moves on to a fresh one).
 */
public final class TheDeep {
    public static final ResourceKey<Level> LEVEL =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "the_deep"));

    /** Claims sit on a grid with one untouched chunk between them, so veins rarely cross claims. */
    public static final int CLAIM_SPACING = 2;

    private static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, FactoryAscent.MOD_ID);
    /**
     * Keeps a claim loaded while its miner works. It expires 10 s after the miner stops refreshing
     * it; nothing is loaded for idle miners and nothing persists across restarts.
     */
    public static final net.neoforged.neoforge.registries.DeferredHolder<TicketType, TicketType> MINER_TICKET =
            TICKETS.register("deep_miner", () -> new TicketType(200L, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    private TheDeep() {}

    public static void register(IEventBus modBus) {
        TICKETS.register(modBus);
    }

    public static @Nullable ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    /** Hands out the next unused claim number (never reused, stored with the overworld data). */
    public static int allocateClaim(MinecraftServer server) {
        Claims claims = server.getDataStorage().computeIfAbsent(Claims.TYPE);
        int index = claims.next;
        claims.next++;
        claims.setDirty();
        return index;
    }

    /** Claim number → chunk, walking a square spiral out from the origin. */
    public static ChunkPos claimChunk(int index) {
        int x = 0;
        int z = 0;
        if (index > 0) {
            // Ring k holds 8k cells; find the ring, then the position along it.
            int k = (int) Math.ceil((Math.sqrt(index + 1) - 1) / 2);
            int side = 2 * k;
            int start = (2 * k - 1) * (2 * k - 1);
            int offset = index - start;
            int leg = offset / side;
            int along = offset % side;
            switch (leg) {
                case 0 -> { x = k; z = -k + 1 + along; }
                case 1 -> { x = k - 1 - along; z = k; }
                case 2 -> { x = -k; z = k - 1 - along; }
                default -> { x = -k + 1 + along; z = -k; }
            }
        }
        return new ChunkPos(x * CLAIM_SPACING, z * CLAIM_SPACING);
    }

    static final class Claims extends SavedData {
        static final Codec<Claims> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("next").forGetter(c -> c.next)
        ).apply(i, Claims::new));
        static final SavedDataType<Claims> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "deep_claims"), () -> new Claims(0), CODEC);

        int next;

        Claims(int next) {
            this.next = next;
        }
    }
}
