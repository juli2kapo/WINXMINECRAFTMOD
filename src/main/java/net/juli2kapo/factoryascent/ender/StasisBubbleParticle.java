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
    /** World Y of the water surface in the chamber this bubble rose from. */
    private final double surfaceY;

    StasisBubbleParticle(ClientLevel level, double x, double y, double z, double surfaceY, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.surfaceY = surfaceY;
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
        var block = level.getBlockState(at).getBlock();
        boolean inChamber = block == EnderContent.ENDER_ANCHOR.get() || block == EnderContent.ENDER_BEACON.get();
        if (!inChamber || y >= surfaceY) {
            level.addParticle(ParticleTypes.BUBBLE_POP, x, Math.min(y, surfaceY), z, 0, 0, 0);
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
                                       double xa, double surfaceY, double za, RandomSource random) {
            // The spawner passes the water surface's Y in the y-speed slot.
            return new StasisBubbleParticle(level, x, y, z, surfaceY, sprites.get(random));
        }
    }
}
