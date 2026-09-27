package net.juli2kapo.factoryascent.ender;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * The bubble column inside an Ender Anchor. Vanilla's column bubble removes itself outside real
 * water, and the chamber's water is part of its model, so this one lives as long as it is inside
 * the chamber and pops when it reaches the surface.
 */
public class StasisBubbleParticle extends SingleQuadParticle {
    /** Height of the water surface above the lower half's floor, in blocks. */
    private static final double SURFACE = 1.0 + 13.0 / 16.0;
    private final double floorY;

    StasisBubbleParticle(ClientLevel level, double x, double y, double z, double floorY, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.floorY = floorY;
        this.gravity = -0.125F;
        this.friction = 0.85F;
        this.setSize(0.02F, 0.02F);
        // The chamber's halves are solid boxes: without this, bubbles hit the upper half's underside and pile up.
        this.hasPhysics = false;
        this.quadSize *= random.nextFloat() * 0.4F + 0.2F;
        this.xd = (random.nextFloat() * 2F - 1F) * 0.02F;
        this.yd = 0.05F + random.nextFloat() * 0.03F;
        this.zd = (random.nextFloat() * 2F - 1F) * 0.02F;
        this.lifetime = 120;
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        BlockPos at = BlockPos.containing(x, y, z);
        if (!level.getBlockState(at).is(EnderContent.ENDER_ANCHOR.get()) || y >= floorY + SURFACE) {
            level.addParticle(ParticleTypes.BUBBLE_POP, x, Math.min(y, floorY + SURFACE), z, 0, 0, 0);
            remove();
        }
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xa, double floorY, double za, RandomSource random) {
            // The spawner passes the chamber floor's Y in the y-speed slot.
            return new StasisBubbleParticle(level, x, y, z, floorY, sprites.get(random));
        }
    }
}
