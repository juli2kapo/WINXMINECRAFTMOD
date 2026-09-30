package net.juli2kapo.factoryascent.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A tiny client API for decorating the sun in the mod's own skies: Earth orbit
 * ({@code factoryascent:orbit}) and the planets ({@code factoryascent:moon}, {@code mars}, {@code io}).
 * Content that wants to draw something around the sun there (a Dyson swarm, a solar shell...)
 * registers a {@link SunDecorator} once during client setup; every frame, right after the vanilla
 * sky and the mod's own sky objects (Earth, moons, satellites' layer) are drawn, each of those
 * skies calls {@link #drawAroundSun} with a pose already turned towards the sun.
 *
 * <p>The Overworld sky is <em>not</em> covered: vanilla draws it, and other content decorates it
 * through NeoForge's own events.
 */
public final class SkyHooks {
    /**
     * Something drawn around the sun of a mod sky.
     *
     * <p>{@code pose.last().pose()} is the level's model-view matrix (camera rotation only, the
     * camera sits at the origin) followed by exactly the transform vanilla uses for the sun:
     * {@code rotY(-90°)} then {@code rotX(sunAngle)}. In that frame the sun is the quad
     * {@code x, z ∈ [-size, size]} at {@code y = 100} (see {@link #sunHalfSize}), so "towards the
     * sun" is +Y and anything placed at {@code y ≈ 100} and scaled like the sun sits in the sky
     * layer at the sun's distance. Draw with a render pass on the main render target (colour +
     * depth, no depth write needed); the terrain is drawn afterwards and hides whatever is behind
     * it. Push/pop the pose if you change it. Called on the render thread only.
     */
    @FunctionalInterface
    public interface SunDecorator {
        void drawAroundSun(PoseStack pose, float partialTick, ResourceKey<Level> dimension);
    }

    private static final List<SunDecorator> DECORATORS = new CopyOnWriteArrayList<>();

    private SkyHooks() {}

    /** Adds a decorator (call once, e.g. from FMLClientSetupEvent or a client mod constructor). */
    public static void register(SunDecorator decorator) {
        DECORATORS.add(decorator);
    }

    public static void unregister(SunDecorator decorator) {
        DECORATORS.remove(decorator);
    }

    /** Whether anyone registered (skies skip building the pose otherwise). */
    public static boolean any() {
        return !DECORATORS.isEmpty();
    }

    /**
     * Half-size, in sky units at distance 100, of the sun disc in a mod sky (the vanilla sun
     * quad: 30 in every mod sky today; ask here rather than hard-coding it).
     */
    public static float sunHalfSize(ResourceKey<Level> dimension) {
        return SunSizes.halfSize(dimension);
    }

    /** Called by the mod skies; never throws out of one decorator into the next. */
    public static void drawAroundSun(PoseStack pose, float partialTick, ResourceKey<Level> dimension) {
        for (SunDecorator d : DECORATORS) {
            pose.pushPose();
            try {
                d.drawAroundSun(pose, partialTick, dimension);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().error("Sun decorator {} failed; removing it", d, e);
                DECORATORS.remove(d);
            } finally {
                pose.popPose();
            }
        }
    }

    /** Sun sizes per sky (kept apart so the planets can extend the table). */
    static final class SunSizes {
        private SunSizes() {}

        static float halfSize(ResourceKey<Level> dimension) {
            return 30f;
        }
    }
}
