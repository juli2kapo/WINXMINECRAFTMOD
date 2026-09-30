package net.juli2kapo.factoryascent.space.planet;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * An impact crater (Moon, Mars): a round bowl dug into the ground, lined with the surface block it
 * was dug into, a raised rim around it and a spray of ejecta. The radius (3..12 blocks) stays small
 * enough for the bowl and rim to fit in the chunks a feature may touch.
 */
public class CraterFeature extends Feature<NoneFeatureConfiguration> {
    public static final int MAX_RADIUS = 12;

    public CraterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int radius = 3 + random.nextInt(random.nextInt(4) == 0 ? MAX_RADIUS - 2 : 6);
        double depth = radius * (0.35 + random.nextDouble() * 0.2);
        int cy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ()) - 1;
        if (cy <= level.getMinY() + 8) return false;
        BlockState lining = level.getBlockState(new BlockPos(origin.getX(), cy, origin.getZ()));
        if (lining.isAir() || !lining.getFluidState().isEmpty()) return false;
        BlockState rock = level.getBlockState(new BlockPos(origin.getX(), cy - 6, origin.getZ()));
        if (rock.isAir()) rock = lining;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int rim = radius + 2;
        for (int dx = -rim; dx <= rim; dx++) {
            for (int dz = -rim; dz <= rim; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.6 - 0.3;
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (d < radius) {
                    // the bowl: clear down to a parabolic floor, line it with the surface block
                    double t = d / radius;
                    int floor = (int) Math.round(cy - depth * (1 - t * t));
                    for (int y = top; y > floor; y--) level.setBlock(p.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    for (int y = Math.min(floor, top); y > floor - 2; y--) level.setBlock(p.set(x, y, z), lining, 2);
                } else if (d < rim) {
                    // the raised rim, highest right at the edge
                    int raise = (int) Math.round(2.2 * (1 - (d - radius) / (rim - radius)) * Math.min(1.0, radius / 5.0));
                    for (int y = top + 1; y <= top + raise; y++) level.setBlock(p.set(x, y, z), y == top + raise ? lining : rock, 2);
                }
            }
        }
        // ejecta: a few boulders of rock thrown out around the crater
        int boulders = random.nextInt(2 + radius / 3);
        for (int i = 0; i < boulders; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = rim + random.nextDouble() * 1.5;
            if (r > MAX_RADIUS + 2) r = MAX_RADIUS + 2;
            int x = origin.getX() + (int) Math.round(Math.cos(a) * r), z = origin.getZ() + (int) Math.round(Math.sin(a) * r);
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            level.setBlock(p.set(x, top, z), rock, 2);
        }
        return true;
    }
}
