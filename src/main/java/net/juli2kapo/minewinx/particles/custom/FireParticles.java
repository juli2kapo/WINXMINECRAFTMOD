package net.juli2kapo.minewinx.particles.custom;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;

public class FireParticles extends TextureSheetParticle {

    protected FireParticles(ClientLevel level, double x, double y, double z,
                            double dx, double dy, double dz) {
        super(level, x, y, z, dx, dy, dz);

        this.friction = 0.96F;
        this.gravity = 0.01f;
        this.xd = dx * 0.01D;
        this.yd = dy * 0.01D;
        this.zd = dz * 0.01D;
        this.yd += 0.1D;
        this.lifetime = (int)(8.0D / (this.random.nextDouble() * 0.8D + 0.3D));
        this.lifetime = Math.max(this.lifetime, 1);
        this.quadSize *= 0.5f;
    }

    @Override
    public void tick() {
        super.tick();
        fadeOut();
        this.quadSize *= 0.96F;
    }

    private void fadeOut() {
        this.alpha = (-(1/(float)lifetime) * age + 1);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<net.minecraft.core.particles.SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Factory(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        public Particle createParticle(net.minecraft.core.particles.SimpleParticleType particleType, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            FireParticles fireParticle = new FireParticles(level, x, y, z, dx, dy, dz);
            fireParticle.pickSprite(this.spriteSet);
            return fireParticle;
        }
    }
}