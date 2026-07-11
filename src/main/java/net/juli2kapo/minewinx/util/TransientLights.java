package net.juli2kapo.minewinx.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Luces dinámicas" para 1.20.1 sin mods de terceros: bloques de luz invisibles
 * (minecraft:light) colocados temporalmente en aire y retirados solos. Los usan
 * los rayos de luz (estela) y los prismas (aura).
 */
public final class TransientLights {

    private static final Map<BlockPos, Long> ACTIVE = new ConcurrentHashMap<>();

    private TransientLights() {}

    /** Coloca (o refresca) una luz temporal si la posición es aire o ya es luz nuestra. */
    public static void place(ServerLevel level, BlockPos pos, int lightLevel, int durationTicks) {
        long expiry = level.getGameTime() + durationTicks;
        BlockPos immutable = pos.immutable();
        if (ACTIVE.containsKey(immutable)) {
            ACTIVE.put(immutable, expiry);
            return;
        }
        if (level.getBlockState(immutable).isAir()) {
            level.setBlock(immutable, Blocks.LIGHT.defaultBlockState()
                    .setValue(LightBlock.LEVEL, Math.min(15, Math.max(1, lightLevel))), 3);
            ACTIVE.put(immutable, expiry);
        }
    }

    /** Llamar cada tick del servidor: retira las luces vencidas. */
    public static void tick(ServerLevel level) {
        if (ACTIVE.isEmpty()) return;
        long now = level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) return false;
            if (level.getBlockState(entry.getKey()).is(Blocks.LIGHT)) {
                level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), 3);
            }
            return true;
        });
    }
}
