package net.juli2kapo.minewinx.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cabeza de dragón del Puñetazo de Dragón: la cabeza voxel extraída del modelo
 * de MineBench, renderizada como MODELO real (no partículas), volando por
 * delante de la embestida y desvaneciéndose.
 */
public class DragonHeadEntity extends Entity {

    public static final int LIFETIME = 14;

    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(DragonHeadEntity.class, EntityDataSerializers.INT);

    private java.util.UUID ownerId;
    // Cada víctima recibe el mordisco UNA sola vez por cabeza
    private final java.util.Set<Integer> hitIds = new java.util.HashSet<>();

    public DragonHeadEntity(EntityType<? extends DragonHeadEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void init(int stage, Vec3 velocity, net.minecraft.world.entity.player.Player owner) {
        this.entityData.set(DATA_STAGE, Math.max(1, stage));
        this.ownerId = owner == null ? null : owner.getUUID();
        this.setDeltaMovement(velocity);
        // Orientación fija según el vector inicial (el renderer la usa)
        this.setYRot((float) (net.minecraft.util.Mth.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
        this.setXRot((float) (net.minecraft.util.Mth.atan2(velocity.y, velocity.horizontalDistance()) * (180.0 / Math.PI)));
    }

    public int getStage() {
        return this.entityData.get(DATA_STAGE);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STAGE, 1);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 delta = this.getDeltaMovement();
        this.setPos(this.getX() + delta.x, this.getY() + delta.y, this.getZ() + delta.z);
        if (!this.level().isClientSide()) {
            bite();
            if (this.tickCount >= LIFETIME) {
                this.discard();
            }
        }
    }

    /** La cabeza muerde a su paso: daño extra + prende fuego (una vez por víctima). */
    private void bite() {
        int stage = this.getStage();
        float damage = 5.0F + 2.0F * stage; // 7 / 9 / 11 (extra al golpe del puñetazo)
        net.minecraft.world.entity.player.Player owner =
                this.ownerId == null ? null : this.level().getPlayerByUUID(this.ownerId);
        // expandTowards(delta): barre TODO el tramo recorrido este tick — a la
        // velocidad de stage 3 (casi 3 bloques/tick) un box quieto se saltea mobs
        net.minecraft.world.phys.AABB box = this.getBoundingBox()
                .expandTowards(this.getDeltaMovement())
                .inflate(0.4 + 0.3 * stage);

        // Muerde a CUALQUIER ser vivo no aliado (igual que el puñetazo), no solo
        // monstruos: isValidTarget es la regla de auto-apuntado, acá no aplica
        for (net.minecraft.world.entity.LivingEntity target : this.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && e.isPickable()
                        && !this.hitIds.contains(e.getId())
                        && !(e instanceof net.minecraft.world.entity.player.Player p && p.isCreative())
                        && !net.juli2kapo.minewinx.util.Targeting.isAlly(e, this.ownerId))) {
            this.hitIds.add(target.getId());
            // Sin esto los i-frames del golpe del puñetazo (que pega 1-3 ticks
            // antes y más fuerte) se tragaban el mordisco entero
            target.invulnerableTime = 0;
            target.hurt(owner != null
                    ? this.damageSources().playerAttack(owner)
                    : this.damageSources().generic(), damage);
            target.setSecondsOnFire(3 + stage);
            Vec3 push = this.getDeltaMovement().normalize().scale(0.6);
            target.push(push.x, push.y * 0.5 + 0.2, push.z);
        }
    }

    @Override
    public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        // Entity base no aplica la velocidad del paquete de spawn; sin esto la
        // copia cliente se queda quieta mientras el server la mueve
        this.setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
        this.setYRot(packet.getYRot());
        this.setXRot(packet.getXRot());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
