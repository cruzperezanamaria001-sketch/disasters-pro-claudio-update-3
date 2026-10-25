package com.desastres.disaster;

import com.desastres.config.DisasterConfig;
import com.desastres.entity.DisasterVisualEntity;
import com.desastres.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Clase base de todos los desastres. Se ejecutan en el tick del servidor. */
public abstract class Disaster {
    protected final ServerLevel level;
    protected final Vec3 pos;
    protected final int intensity;
    protected final RandomSource random = RandomSource.create();
    /** Si el desastre puede modificar bloques (config blockDamage). */
    protected final boolean grief = DisasterConfig.BLOCK_DAMAGE.get();
    protected int age = 0;
    /** Entidades 3D de este desastre (hasta 4 "slots": p. ej. embudo + nubes). */
    private final DisasterVisualEntity[] visuals = new DisasterVisualEntity[4];
    private boolean finished = false;

    protected Disaster(ServerLevel level, Vec3 pos, int intensity) {
        this.level = level;
        this.pos = pos;
        this.intensity = intensity;
    }

    final void doTick() {
        if (finished) {
            return;
        }
        tick();
        age++;
    }

    protected abstract void tick();

    /** Limpieza al terminar (tambien se llama si se detiene con el boton / comando). */
    protected void onEnd() {}

    public void finish() {
        this.finished = true;
    }

    public boolean isFinished() {
        return finished;
    }

    public ServerLevel getLevel() {
        return level;
    }

    /** Atajo: usa el slot 0. */
    protected void showVisual(DisasterVisualEntity.Kind kind, double x, double y, double z,
                              float s1, float s2, float s3, double dx, double dy, double dz) {
        showVisual(0, kind, x, y, z, s1, s2, s3, dx, dy, dz);
    }

    /**
     * Crea (si hace falta) y actualiza una entidad 3D del desastre.
     * s1..s3 son parametros de tamano que interpreta el renderizador segun el tipo; (dx,dy,dz) una direccion.
     */
    protected void showVisual(int slot, DisasterVisualEntity.Kind kind, double x, double y, double z,
                              float s1, float s2, float s3, double dx, double dy, double dz) {
        DisasterVisualEntity v = visuals[slot];
        if (v == null || v.isRemoved()) {
            if (!loaded(new BlockPos(Mth.floor(x), 0, Mth.floor(z)))) {
                return;
            }
            DisasterVisualEntity entity = ModEntities.VISUAL.get().create(level);
            if (entity == null) {
                return;
            }
            entity.setKind(kind);
            entity.setPos(x, y, z);
            entity.setParams(s1, s2, s3);
            entity.setDirection(dx, dy, dz);
            level.addFreshEntity(entity);
            visuals[slot] = entity;
            return;
        }
        v.setPos(x, y, z);
        v.setParams(s1, s2, s3);
        v.setDirection(dx, dy, dz);
    }

    protected DisasterVisualEntity getVisual(int slot) {
        DisasterVisualEntity v = visuals[slot];
        return v != null && !v.isRemoved() ? v : null;
    }

    protected void hideVisual(int slot) {
        if (visuals[slot] != null) {
            visuals[slot].discard();
            visuals[slot] = null;
        }
    }

    final void cleanupVisual() {
        for (int i = 0; i < visuals.length; i++) {
            hideVisual(i);
        }
    }

    /** Los desastres "menores" (meteoritos, bombas...) no cuentan para el limite maxActiveDisasters. */
    protected boolean isMinor() {
        return false;
    }

    protected int scaled(int ticks) {
        return Math.max(20, (int) (ticks * DisasterConfig.DURATION_MULTIPLIER.get()));
    }

    protected boolean loaded(BlockPos p) {
        return level.hasChunkAt(p);
    }

    protected boolean loaded(int x, int z) {
        return level.hasChunkAt(new BlockPos(x, 0, z));
    }

    /** Primer Y libre sobre el suelo (ignora hojas). Solo llamar si la chunk esta cargada. */
    protected int surface(int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }
}
