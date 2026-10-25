package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tsunami: una ola 3D (entidad visual) que avanza en la direccion en la que mira el jugador siguiendo el
 * terreno. NO coloca, reemplaza ni genera bloques de agua: el efecto es visual (modelo 3D + particulas) mas
 * empuje y dano a las entidades que toca. No deja nada en el mundo al terminar.
 */
public class TsunamiDisaster extends Disaster {
    private static final int BODY_LEN = 5;
    private static final double SPEED = 0.7D;

    private final Vec3 dir;
    private final Vec3 side;
    private final int halfWidth;
    private final int height;
    private final int range;

    private double front = 0.0D;
    private double smoothY = Double.NaN;

    public TsunamiDisaster(ServerLevel level, Vec3 start, int intensity, Vec3 look) {
        super(level, start, intensity);
        double lx = look.x;
        double lz = look.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 1.0E-4D) {
            lx = 1.0D;
            lz = 0.0D;
            len = 1.0D;
        }
        this.dir = new Vec3(lx / len, 0.0D, lz / len);
        this.side = new Vec3(-dir.z, 0.0D, dir.x);
        this.halfWidth = 14 + 8 * intensity;
        this.height = 5 + 3 * intensity;
        // La duracion (config durationMultiplier) escala el recorrido de la ola
        this.range = scaled(110 + 60 * intensity);
        Fx.sound(level, start.x, start.y, start.z, SoundEvents.AMBIENT_UNDERWATER_ENTER, 8.0F, 0.4F);
    }

    @Override
    protected void tick() {
        if (front > range) {
            finish();
            return;
        }
        front += SPEED;
        pushEntities();
        effects();
        updateVisual();
    }

    private double groundAt(double x, double z) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        return loaded(ix, iz) ? surface(ix, iz) : Double.NaN;
    }

    /** Coloca la ola 3D sobre el terreno: altura suavizada en el centro e inclinacion lateral segun el relieve. */
    private void updateVisual() {
        double vx = pos.x + dir.x * (front - 1.0D);
        double vz = pos.z + dir.z * (front - 1.0D);
        double gy = groundAt(vx, vz);
        if (Double.isNaN(gy)) {
            return;
        }
        smoothY = Double.isNaN(smoothY) ? gy : smoothY + (gy - smoothY) * 0.35D;

        // El eje local +X del modelo apunta hacia -side
        double reach = halfWidth * 0.7D;
        double hPlus = groundAt(vx - side.x * reach, vz - side.z * reach);
        double hMinus = groundAt(vx + side.x * reach, vz + side.z * reach);
        double tilt = 0.0D;
        if (!Double.isNaN(hPlus) && !Double.isNaN(hMinus)) {
            tilt = Mth.clamp((hPlus - hMinus) / (2.0D * reach), -0.6D, 0.6D);
        }
        showVisual(DisasterVisualEntity.Kind.TSUNAMI, vx, smoothY, vz,
                halfWidth, height * 1.15F, BODY_LEN, dir.x, tilt, dir.z);
    }

    private void pushEntities() {
        double cx = pos.x + dir.x * (front - BODY_LEN / 2.0D);
        double cz = pos.z + dir.z * (front - BODY_LEN / 2.0D);
        double rad = halfWidth + BODY_LEN + 4;
        AABB box = new AABB(cx - rad, pos.y - 64, cz - rad, cx + rad, pos.y + 96, cz + rad);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof DisasterVisualEntity || DisasterManager.isInTransit(e) || e.isSpectator()) {
                continue;
            }
            double rx = e.getX() - pos.x;
            double rz = e.getZ() - pos.z;
            double along = rx * dir.x + rz * dir.z;
            double lateral = rx * side.x + rz * side.z;
            if (along > front + 1.5D || along < front - BODY_LEN - 1 || Math.abs(lateral) > halfWidth + 1) {
                continue;
            }
            int gy = surface(e.getBlockX(), e.getBlockZ());
            if (e.getY() > gy + height + 3) {
                continue;
            }
            // Empuje coherente con la direccion de avance de la ola
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.4D + dir.x * SPEED * 0.9D,
                    Math.max(v.y, 0.2D),
                    v.z * 0.4D + dir.z * SPEED * 0.9D);
            e.hurtMarked = true;
            e.clearFire();
            if (e instanceof LivingEntity living && e.tickCount % 10 == 0) {
                living.hurt(level.damageSources().drown(), 1.5F + intensity);
            }
        }
    }

    private void effects() {
        double fx = pos.x + dir.x * front;
        double fz = pos.z + dir.z * front;
        for (int i = 0; i < 4; i++) {
            double w = (random.nextDouble() * 2.0D - 1.0D) * halfWidth;
            double px = fx + side.x * w;
            double pz = fz + side.z * w;
            double gy = groundAt(px, pz);
            if (Double.isNaN(gy)) {
                continue;
            }
            double py = gy + height * (0.6D + random.nextDouble() * 0.5D);
            Fx.burst(level, ParticleTypes.SPLASH, px, py, pz, 6, 1.2D, 1.0D, 1.2D, 0.2D);
            Fx.dir(level, ParticleTypes.CLOUD, px, py, pz, dir.x * 0.3D, 0.05D, dir.z * 0.3D);
        }
        double gc = groundAt(fx, fz);
        if (age % 8 == 0 && !Double.isNaN(gc)) {
            Fx.sound(level, fx, gc, fz, SoundEvents.GENERIC_SPLASH, 6.0F, 0.4F);
        }
    }
}
