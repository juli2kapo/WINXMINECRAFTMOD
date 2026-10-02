package io.github.mishkis.orbital_railgun.client.rendering;

import io.github.mishkis.orbital_railgun.OrbitalRailgun;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * The strike effect. Several strikes can play at once (two guns, or other players firing): each
 * keeps its own position and timer and is drawn over the previous ones, oldest first. Every active
 * strike costs a full-screen pass, so at most {@link #MAX_STRIKES} are kept (the oldest goes first).
 */
public class OrbitalRailgunShader extends AbstractOrbitalRailgunShader {
    public static final Identifier ORBITAL_RAILGUN_SHADER = Identifier.fromNamespaceAndPath(OrbitalRailgun.MOD_ID, "orbital_railgun");
    public static final OrbitalRailgunShader INSTANCE = new OrbitalRailgunShader();

    /** Strikes drawn at the same time. */
    public static final int MAX_STRIKES = 4;
    /**
     * Length of one strike: the aftermath glow fades out 4 s after the explosion (36 s + 4 s, see
     * afterTime in strike.fsh); the original faded over 25 s and ran for 1600 ticks.
     */
    private static final int STRIKE_TICKS = 820;

    private static final class Strike {
        final Vector3f position;
        final ResourceKey<Level> dimension;
        int ticks;

        Strike(Vector3f position, ResourceKey<Level> dimension) {
            this.position = position;
            this.dimension = dimension;
        }
    }

    private final List<Strike> strikes = new ArrayList<>();

    /** Starts a strike effect at a position (the centre of the struck block) in a dimension. */
    public void start(Vector3f position, ResourceKey<Level> dimension) {
        // The shooter starts the effect locally and then also gets the server's sync for the same strike.
        for (Strike strike : strikes) {
            if (strike.dimension == dimension && strike.ticks < 100 && strike.position.distanceSquared(position) < 1f) return;
        }
        if (strikes.size() >= MAX_STRIKES) strikes.remove(0);
        strikes.add(new Strike(position, dimension));
    }

    @Override
    protected Identifier getIdentifier() {
        return ORBITAL_RAILGUN_SHADER;
    }

    @Override
    protected boolean shouldRender() {
        var level = client != null ? client.level : null;
        if (level == null) return false;
        for (Strike strike : strikes) {
            if (strike.dimension == level.dimension()) return true;
        }
        return false;
    }

    @Override
    protected void tickExtra() {
        var level = client != null ? client.level : null;
        strikes.removeIf(s -> ++s.ticks >= STRIKE_TICKS || level == null || s.dimension != level.dimension());
    }

    @Override
    protected List<Instance> instances() {
        List<Instance> out = new ArrayList<>(strikes.size());
        for (Strike strike : strikes) out.add(new Instance(strike.position, strike.ticks));
        return out;
    }
}
