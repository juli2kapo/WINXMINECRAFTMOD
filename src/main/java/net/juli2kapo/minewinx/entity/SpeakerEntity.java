package net.juli2kapo.minewinx.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SpeakerEntity extends Entity {
    private static final int ANIMATION_DURATION = 40;
    private final AnimationState animationState = new AnimationState();
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(SpeakerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VOICE = SynchedEntityData.defineId(SpeakerEntity.class, EntityDataSerializers.INT);
    private static final int MAX_LIFETIME = 600; // 30 segundos (20 ticks por segundo)
    private static final double DAMAGE_RADIUS = 12.0;
    private static final float DAMAGE_AMOUNT = 4.0F; // 6 apilaba demasiado con varios parlantes
    private static final int DAMAGE_INTERVAL = 20; // 2 segundos

    private Player owner;
    private int damageTimer = 0;

    public SpeakerEntity(EntityType<? extends SpeakerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true; // No cae al suelo
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIFETIME, 0);
        this.entityData.define(VOICE, 0);
    }

    /** Voz asignada al invocar (0 melodía, 1 bajo, 2 acordes) — antes salía del
     *  id de entidad y podías quedar sin melodía por pura mala suerte. */
    public void setVoice(int voice) {
        this.entityData.set(VOICE, Math.floorMod(voice, 3));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.animationState.startIfStopped(this.tickCount);
        }
        else {
            // Incrementar tiempo de vida
            int currentLifetime = this.entityData.get(LIFETIME);
            currentLifetime++;
            this.entityData.set(LIFETIME, currentLifetime);

            // Eliminar después de tiempo máximo
            if (currentLifetime >= MAX_LIFETIME) {
                this.destroySpeaker();
                return;
            }

            // Sistema de daño por intervalos
            damageTimer++;
            if (damageTimer >= DAMAGE_INTERVAL) {
                this.dealDamageToNearbyEntities();
                damageTimer = 0;
            }

            // Música real: cada parlante toca una voz distinta de la misma
            // melodía, sincronizados por el reloj del mundo
            if (this.level() instanceof ServerLevel serverLevel) {
                this.playMusicTick(serverLevel);
            }
        }
    }

    private void dealDamageToNearbyEntities() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 speakerPos = this.position();
        AABB damageArea = this.getBoundingBox().inflate(DAMAGE_RADIUS);
        List<LivingEntity> nearbyEntities = this.level().getEntitiesOfClass(LivingEntity.class, damageArea);

        for (LivingEntity entity : nearbyEntities) {
            if (entity == this.owner) continue; // No dañar al propietario

            double distanceToSpeaker = speakerPos.distanceTo(entity.position());
            if (distanceToSpeaker <= DAMAGE_RADIUS) {
                // Aplicar daño con reducción por distancia
                double distanceFactor = 1.0 - (distanceToSpeaker / DAMAGE_RADIUS);
                float finalDamage = DAMAGE_AMOUNT * (float) distanceFactor;

                DamageSource damageSource = this.level().damageSources().indirectMagic(this, this.owner);
                entity.hurt(damageSource, finalDamage);

                // Efectos de partículas en la entidad dañada
                serverLevel.sendParticles(ParticleTypes.NOTE,
                        entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                        5, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    // ---- Himno de la Alegría (Beethoven, 9ª) — las 4 frases completas -------
    // Grilla de CORCHEAS (8 por compás, 4 compases por frase, 4 frases = 128
    // pasos). Con el ritmo real: negras, las figuras punteadas de fin de frase
    // y las corcheas E-F de la frase 3. Semitonos: C4=6 D4=8 E4=10 F4=11 G4=13.
    private static final int STEP_TICKS = 4;   // corchea = 4 ticks → negra a 150 bpm
    private static final int TOTAL_STEPS = 128;
    private static final int[] MELODY_AT = new int[TOTAL_STEPS]; // nota que ARRANCA en cada paso, -1 = nada
    private static final int[] BASS_AT = new int[TOTAL_STEPS];
    private static final int[] CHORD_AT = new int[TOTAL_STEPS];

    static {
        java.util.Arrays.fill(MELODY_AT, -1);
        java.util.Arrays.fill(BASS_AT, -1);
        java.util.Arrays.fill(CHORD_AT, -1);

        // Melodía como pares {nota, duración en corcheas}
        int[][] fraseA = {{10,2},{10,2},{11,2},{13,2},{13,2},{11,2},{10,2},{8,2},
                          {6,2},{6,2},{8,2},{10,2},{10,3},{8,1},{8,4}};
        int[][] fraseB = {{10,2},{10,2},{11,2},{13,2},{13,2},{11,2},{10,2},{8,2},
                          {6,2},{6,2},{8,2},{10,2},{8,3},{6,1},{6,4}};
        int[][] fraseC = {{8,2},{8,2},{10,2},{6,2},
                          {8,2},{10,1},{11,1},{10,2},{6,2},
                          {8,2},{10,1},{11,1},{10,2},{8,2},
                          {6,2},{8,2},{1,4}};
        int step = 0;
        for (int[][] frase : new int[][][]{fraseA, fraseB, fraseC, fraseB}) {
            for (int[] nota : frase) {
                MELODY_AT[step] = nota[0];
                step += nota[1];
            }
        }

        // Bajo: raíz por medio compás (C=6, G=1 — el timbre "bass" ya suena
        // dos octavas abajo). 8 raíces por frase.
        int[] rootsA = {6, 6, 1, 1, 6, 6, 1, 1};
        int[] rootsB = {6, 6, 1, 1, 6, 6, 1, 6};
        int[] rootsC = {1, 1, 6, 6, 1, 1, 6, 1};
        int half = 0;
        for (int[] roots : new int[][]{rootsA, rootsB, rootsC, rootsB}) {
            for (int root : roots) {
                int at = half * 4; // el medio compás dura 4 corcheas
                BASS_AT[at] = root;
                // Acordes a contratiempo (corchea 3 del medio compás): tercera
                // o quinta del acorde, alternando — relleno armónico suave
                CHORD_AT[at + 2] = root == 6
                        ? (half % 2 == 0 ? 10 : 13)   // C: E4 / G4
                        : (half % 2 == 0 ? 5 : 8);    // G: B3 / D4
                half++;
            }
        }
    }

    /**
     * Cada parlante toca SU voz (asignada al invocar): melodía, bajo+bombo o
     * acordes. Todos comparten el reloj del mundo, así que suenan en sincronía
     * y entre varios arman el arreglo completo.
     */
    private void playMusicTick(ServerLevel serverLevel) {
        long time = serverLevel.getGameTime();
        if (time % STEP_TICKS != 0) return;
        int step = (int) ((time / STEP_TICKS) % TOTAL_STEPS);

        Vec3 pos = this.position();
        switch (this.entityData.get(VOICE)) {
            case 0 -> playNote(serverLevel, pos, SoundEvents.NOTE_BLOCK_HARP.value(), MELODY_AT[step], 1.6F);
            case 1 -> {
                int root = BASS_AT[step];
                if (root >= 0) {
                    playNote(serverLevel, pos, SoundEvents.NOTE_BLOCK_BASS.value(), root, 1.3F);
                    // Bombo suave pegado al bajo: marca el pulso sin taparlo
                    serverLevel.playSound(null, pos.x, pos.y, pos.z,
                            SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.RECORDS, 0.7F, 0.8F);
                }
            }
            default -> playNote(serverLevel, pos, SoundEvents.NOTE_BLOCK_PLING.value(), CHORD_AT[step], 0.9F);
        }
    }

    private void playNote(ServerLevel serverLevel, Vec3 pos, net.minecraft.sounds.SoundEvent sound, int note, float volume) {
        if (note < 0) return;
        float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
        serverLevel.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.RECORDS, volume, pitch);
        // Nota visual con el tono como color (mismo truco que los note blocks)
        serverLevel.sendParticles(ParticleTypes.NOTE,
                pos.x, pos.y + 1.2, pos.z, 0, note / 24.0, 0.0, 0.0, 1.0);
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public Player getOwner() {
        return this.owner;
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (!this.level().isClientSide()) {
            this.destroySpeaker();
        }
        return true;
    }

    private void destroySpeaker() {
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = this.position();

            // Efectos de destrucción
            serverLevel.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);

            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    pos.x, pos.y + 0.5, pos.z,
                    10, 0.3, 0.3, 0.3, 0.1);
        }

        this.discard();
    }

    @Override
    public boolean isPickable() {
        return true; // Permite que sea seleccionable para ataques
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Lifetime", this.entityData.get(LIFETIME));
        tag.putInt("DamageTimer", this.damageTimer);

        if (this.owner != null) {
            tag.putUUID("Owner", this.owner.getUUID());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(LIFETIME, tag.getInt("Lifetime"));
        this.damageTimer = tag.getInt("DamageTimer");

        if (tag.hasUUID("Owner")) {
            // El owner se restablecerá cuando el jugador esté disponible
        }
    }

    public AnimationState getAnimationState() {
        return animationState;
    }

    public float getAnimationProgress() {
        if (this.tickCount <= ANIMATION_DURATION) {
            return Math.min(1.0f, (float) this.tickCount / ANIMATION_DURATION);
        }
        return 1.0f;
    }
    
}