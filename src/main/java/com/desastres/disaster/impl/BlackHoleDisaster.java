package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Agujero negro flotante: atrae entidades, devora bloques y mata lo que cae en el nucleo. */
public class BlackHoleDisaster extends Disaster {
    private final int duration;
    private final double pullRadius;
    private final double coreRadius;

    public BlackHoleDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target.add(0, 3 + intensity, 0), intensity);
        this.duration = scaled(800 + 400 * intensity);
        this.pullRadius = 14 + 8 * intensity;
        this.coreRadius = 2.0D + intensity;
        Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.ENDER_DRAGON_GROWL, 6.0F, 0.5F);
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }

        int fadeIn = 60;
        int fadeOut = Math.max(40, duration / 8);
        double scale;
        if (age < fadeIn) {
            scale = 0.3D + 0.7D * age / fadeIn;
        } else if (age > duration - fadeOut) {
            scale = Math.max(0.0D, (double) (duration - age) / fadeOut);
        } else {
            scale = 1.0D;
        }
        double core = coreRadius * scale;
        double reach = pullRadius * scale;
        showVisual(DisasterVisualEntity.Kind.BLACK_HOLE, pos.x, pos.y, pos.z,
                (float) core, (float) (core * 3.4D), 0.0F, 0.0D, 0.0D, 0.0D);
        if (reach < 1.0D) {
            return;
        }

        renderEffects(core, reach);
        pullEntities(core, reach);
        if (grief) {
            devour(core, reach);
        }
        if (age % 40 == 0) {
            Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.PORTAL_AMBIENT, 8.0F, 0.4F);
        }
    }

    private void renderEffects(double core, double reach) {
        // Nucleo oscuro
        Fx.burst(level, ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, core * 0.45D, core * 0.45D, core * 0.45D, 0.0D);

        // Disco de acrecion inclinado
        double tilt = 0.35D;
        double cosT = Math.cos(tilt);
        double sinT = Math.sin(tilt);
        int points = 6;
        for (int i = 0; i < points; i++) {
            double a = age * 0.25D + i * (Math.PI * 2.0D / points);
            double r = core * (1.6D + random.nextDouble() * 1.4D);
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            double px = pos.x + ca * r;
            double pz = pos.z + sa * r * cosT;
            double py = pos.y + sa * r * sinT;
            double vx = -sa * 0.35D;
            double vz = ca * 0.35D * cosT;
            double vy = ca * 0.35D * sinT;
            if (i % 2 == 0) {
                Fx.dir(level, ParticleTypes.FLAME, px, py, pz, vx, vy, vz);
            } else {
                Fx.dir(level, ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, vx, vy, vz);
            }
        }

        // Materia cayendo hacia el centro (las particulas PORTAL convergen al punto de aparicion)
        for (int i = 0; i < 4; i++) {
            double th = random.nextDouble() * Math.PI * 2.0D;
            double ph = Math.acos(2.0D * random.nextDouble() - 1.0D);
            double r = reach * (0.4D + 0.6D * random.nextDouble());
            Fx.dir(level, ParticleTypes.PORTAL, pos.x, pos.y, pos.z,
                    Math.sin(ph) * Math.cos(th) * r,
                    Math.cos(ph) * r,
                    Math.sin(ph) * Math.sin(th) * r);
        }
    }

    private void pullEntities(double core, double reach) {
        AABB box = new AABB(pos.x - reach, pos.y - reach, pos.z - reach,
                pos.x + reach, pos.y + reach, pos.z + reach);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof DisasterVisualEntity || DisasterManager.isInTransit(e) || e.isSpectator() || !e.isAlive()) {
                continue;
            }
            Vec3 center = e.position().add(0, e.getBbHeight() * 0.5D, 0);
            Vec3 d = pos.subtract(center);
            double dist = d.length();
            if (dist > reach || dist < 1.0E-4D) {
                continue;
            }
            if (dist <= core) {
                swallow(e);
                continue;
            }
            double ratio = 1.0D - dist / reach;
            double force = 0.04D + 0.35D * ratio * ratio;
            Vec3 pull = d.scale(force / dist);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.92D).add(pull));
            e.hurtMarked = true;
            if (e instanceof FallingBlockEntity fb) {
                fb.dropItem = false;
            }
        }
    }

    private void swallow(Entity e) {
        if (e instanceof LivingEntity living) {
            living.invulnerableTime = 0;
            living.hurt(level.damageSources().magic(), 6.0F + 4.0F * intensity);
        } else {
            e.discard();
        }
    }

    /** Destruye bloques en una esfera alrededor del agujero. Algunos salen volando como escombros. */
    private void devour(double core, double reach) {
        double eat = Math.max(core * 2.0D, reach * 0.4D);
        int attempts = 50 + 25 * intensity;
        int debris = 0;
        for (int i = 0; i < attempts; i++) {
            double r = eat * Math.cbrt(random.nextDouble());
            double th = random.nextDouble() * Math.PI * 2.0D;
            double ph = Math.acos(2.0D * random.nextDouble() - 1.0D);
            BlockPos bp = BlockPos.containing(
                    pos.x + r * Math.sin(ph) * Math.cos(th),
                    pos.y + r * Math.cos(ph),
                    pos.z + r * Math.sin(ph) * Math.sin(th));
            if (level.isOutsideBuildHeight(bp) || !loaded(bp)) {
                continue;
            }
            BlockState st = level.getBlockState(bp);
            if (st.isAir() || st.getDestroySpeed(level, bp) < 0.0F) {
                continue;
            }
            if (debris < 4 && st.getFluidState().isEmpty() && !st.hasBlockEntity() && random.nextInt(3) == 0) {
                FallingBlockEntity fb = FallingBlockEntity.fall(level, bp, st);
                fb.dropItem = false;
                debris++;
            } else {
                level.removeBlock(bp, false);
            }
        }
    }

    @Override
    protected void onEnd() {
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 3, 2.0D, 2.0D, 2.0D, 0.0D);
        Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, 8.0F, 0.5F);
    }
}
