package net.juli2kapo.factoryascent.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Whether a position is open to the sky, decided from the blocks themselves.
 *
 * <p>Vanilla's {@code Level.canSeeSky} reads the sky light level, and the server's light engine
 * applies block changes asynchronously, a tick or several later under load. Machines that must react
 * to a block placed over them in the same tick (a Mass Driver whose muzzle was just covered must not
 * fire) would see the stale light and act on it. This check walks the column from {@code pos} up to
 * the {@link Heightmap.Types#WORLD_SURFACE} height (updated synchronously with every block change)
 * and passes only if every block on the way lets full sky light straight down, which is exactly when
 * the light engine settles on sky light 15 there.
 */
public final class SkyAccess {
    private SkyAccess() {}

    public static boolean canSeeSky(Level level, BlockPos pos) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int y = pos.getY(); y < top; y++) {
            p.setY(y);
            if (!level.getBlockState(p).propagatesSkylightDown()) return false;
        }
        return true;
    }
}
