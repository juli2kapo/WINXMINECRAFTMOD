package io.github.mishkis.orbital_railgun.client;

import net.minecraft.world.phys.Vec3;
import io.github.mishkis.orbital_railgun.client.rendering.OrbitalRailgunShader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class OrbitalRailgunClientHooks {
    public static void onStrikeSync(BlockPos blockPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        OrbitalRailgunShader.INSTANCE.start(Vec3.atCenterOf(blockPos).toVector3f(), minecraft.level.dimension());
    }
}
