package com.desastres.entity;

import com.desastres.disaster.DisasterManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Entidad puramente visual: la controla un Disaster en el servidor (posicion y parametros)
 * y el cliente la dibuja como un objeto 3D real (ver DisasterVisualRenderer).
 */
public class DisasterVisualEntity extends Entity {

    public enum Kind {
        BLACK_HOLE, TORNADO, TSUNAMI, METEOR, STORM_CLOUD, ASH_PLUME, SHOCKWAVE,
        COMET, WORMHOLE, HYP_BLACK_HOLE, WHITE_HOLE,
        MARS, UFO, LASER_BEAM, HEAT_SKY;

        public static Kind byOrdinal(int i) {
            Kind[] all = values();
            return i >= 0 && i < all.length ? all[i] : BLACK_HOLE;
        }
    }

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(DisasterVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> S1 =
            SynchedEntityData.defineId(DisasterVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> S2 =
            SynchedEntityData.defineId(DisasterVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> S3 =
            SynchedEntityData.defineId(DisasterVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DX =
            SynchedEntityData.defineId(DisasterVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY =
            SynchedEntityData.defineId(DisasterVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ =
            SynchedEntityData.defineId(DisasterVisualEntity.class, EntityDataSerializers.FLOAT);

    /** Ticks de vida (solo servidor). -1 = sin limite (la controla un desastre). */
    private int lifetime = -1;

    public DisasterVisualEntity(EntityType<? extends DisasterVisualEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(KIND, 0);
        this.entityData.define(S1, 1.0F);
        this.entityData.define(S2, 1.0F);
        this.entityData.define(S3, 1.0F);
        this.entityData.define(DX, 0.0F);
        this.entityData.define(DY, 0.0F);
        this.entityData.define(DZ, 1.0F);
    }

    /** Entidades de corta vida (rayos laser): se eliminan solas pasados estos ticks. */
    public void setLifetime(int ticks) {
        this.lifetime = ticks;
    }

    public Kind getKind() {
        return Kind.byOrdinal(this.entityData.get(KIND));
    }

    public void setKind(Kind kind) {
        this.entityData.set(KIND, kind.ordinal());
    }

    public float getS1() {
        return this.entityData.get(S1);
    }

    public float getS2() {
        return this.entityData.get(S2);
    }

    public float getS3() {
        return this.entityData.get(S3);
    }

    public void setParams(float s1, float s2, float s3) {
        this.entityData.set(S1, s1);
        this.entityData.set(S2, s2);
        this.entityData.set(S3, s3);
    }

    public float getDirX() {
        return this.entityData.get(DX);
    }

    public float getDirY() {
        return this.entityData.get(DY);
    }

    public float getDirZ() {
        return this.entityData.get(DZ);
    }

    public void setDirection(double x, double y, double z) {
        this.entityData.set(DX, (float) x);
        this.entityData.set(DY, (float) y);
        this.entityData.set(DZ, (float) z);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getCommandSenderWorld().isClientSide && this.lifetime > 0 && this.tickCount >= this.lifetime) {
            this.discard();
            return;
        }
        // Si no queda ningun desastre activo, esta entidad esta huerfana: se elimina.
        if (!this.getCommandSenderWorld().isClientSide && this.tickCount > 40 && !DisasterManager.hasActive()) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
