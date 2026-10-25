package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Huracan gigante: un embudo 3D enorme con nubarrones que recorre el mundo. Su velocidad y rumbo varian
 * (rafagas, giros bruscos), levanta y arrastra entidades y objetos, arranca bloques debiles lanzandolos como
 * bloques en caida, y trae lluvia, viento, truenos y rayos ocasionales.
 */
public class HurricaneDisaster extends Disaster {
    private final int duration;
    private final double height;
    private final double baseRadius;
    private final double topRadius;
    private final double pullRadius;
    private final double speedBase;
    private final double phase;

    private Vec3 center;
    private double heading;
    private double smoothY = Double.NaN;

    public HurricaneDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.center = target;
        this.heading = random.nextDouble() * Math.PI * 2.0D;
        this.phase = random.nextDouble() * Math.PI * 2.0D;
        this.duration = scaled(2400 + 800 * intensity);
        this.height = 80.0D + 20.0D * intensity;
        this.baseRadius = 6.0D + 2.0D * intensity;
        this.topRadius = 26.0D + 8.0D * intensity;
        this.pullRadius = 40.0D + 10.0D * intensity;
        this.speedBase = 0.20D + 0.08D * intensity;
        level.setWeatherParameters(0, duration + 200, true, true);
        Fx.sound(level, target.x, target.y, target.z, SoundEvents.ENDER_DRAGON_GROWL, 12.0F, 0.3F);
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        move();

        double sc = Math.max(0.05D, Math.min(1.0D, Math.min(age / 100.0D, (duration - age) / 100.0D)));
        double y = center.y - 1.0D;
        showVisual(0, DisasterVisualEntity.Kind.TORNADO, center.x, y, center.z,
                (float) (height * sc), (float) (baseRadius * 1.2D * sc), (float) (topRadius * sc), 0.0D, 0.0D, 0.0D);
        double cloudY = center.y + height * sc;
        showVisual(1, DisasterVisualEntity.Kind.STORM_CLOUD, center.x, cloudY + 4.0D, center.z,
                (float) (topRadius * 3.2D * sc), 12.0F, 0.0F, 0.0D, 0.0D, 0.0D);
        showVisual(2, DisasterVisualEntity.Kind.STORM_CLOUD, center.x, cloudY + 16.0D, center.z,
                (float) (topRadius * 2.0D * sc), 9.0F, 0.12F, 0.0D, 0.0D, 0.0D);

        entities();
        if (grief) {
            tearBlocks();
        }
        atmosphere();
    }

    /** Movimiento dinamico: velocidad que oscila y rafagas, rumbo que deriva y gira de golpe de vez en cuando. */
    private void move() {
        double speed = speedBase * (0.65D + 0.35D * Math.sin(age * 0.011D + phase));
        if (age % 200 < 25) {
            speed *= 1.6D; // rafaga
        }
        heading += (random.nextDouble() - 0.5D) * 0.06D;
        if (random.nextInt(450) == 0) {
            heading += (random.nextBoolean() ? 1.0D : -1.0D) * (0.6D + random.nextDouble() * 0.7D);
        }
        double nx = center.x + Math.cos(heading) * speed;
        double nz = center.z + Math.sin(heading) * speed;
        int ix = Mth.floor(nx);
        int iz = Mth.floor(nz);
        if (!loaded(ix, iz)) {
            heading += Math.PI; // no se sale de la zona cargada
            return;
        }
        double gy = surface(ix, iz);
        smoothY = Double.isNaN(smoothY) ? gy : smoothY + (gy - smoothY) * 0.1D;
        center = new Vec3(nx, smoothY, nz);
    }

    private void entities() {
        double vr = pullRadius;
        AABB box = new AABB(center.x - vr, center.y - 4, center.z - vr, center.x + vr, center.y + height, center.z + vr);
        double core = topRadius * 0.55D;
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof DisasterVisualEntity || DisasterManager.isInTransit(e) || e.isSpectator()) {
                continue;
            }
            double dx = e.getX() - center.x;
            double dz = e.getZ() - center.z;
            double hd = Math.sqrt(dx * dx + dz * dz);
            if (hd > vr) {
                continue;
            }
            double len = Math.max(hd, 0.001D);
            double tx = -dz / len;
            double tz = dx / len;
            double w = 1.0D - hd / vr; // 1 en el centro, 0 en el borde
            Vec3 v = e.getDeltaMovement();
            double nx;
            double nz;
            double ny = v.y;
            if (hd <= core) {
                // dentro del cuerpo del huracan: remolino fuerte, atraccion y ascenso
                nx = v.x * 0.5D + tx * 0.55D - (dx / len) * 0.14D;
                nz = v.z * 0.5D + tz * 0.55D - (dz / len) * 0.14D;
                ny = Math.min(v.y + 0.10D + 0.04D * intensity, 0.8D + 0.2D * intensity);
            } else {
                // viento exterior: empuja en sentido del giro y hacia dentro, mas debil cuanto mas lejos
                double f = 0.10D * w + 0.015D;
                nx = v.x * 0.92D + tx * f - (dx / len) * f * 0.5D;
                nz = v.z * 0.92D + tz * f - (dz / len) * f * 0.5D;
            }
            e.setDeltaMovement(nx, ny, nz);
            e.hurtMarked = true;
        }
    }

    /** Arrastra objetos: bloques debiles junto al embudo salen despedidos como bloques en caida (con tope por tick). */
    private void tearBlocks() {
        double maxHardness = 0.8D + 0.4D * intensity;
        double reach = baseRadius * 3.0D + 6.0D + 2.0D * intensity;
        int spawned = 0;
        for (int i = 0; i < 24; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0D;
            double r = random.nextDouble() * reach;
            int x = Mth.floor(center.x + Math.cos(ang) * r);
            int z = Mth.floor(center.z + Math.sin(ang) * r);
            if (!loaded(x, z)) {
                continue;
            }
            int y = surface(x, z) - 1 + random.nextInt(6);
            BlockPos bp = new BlockPos(x, y, z);
            if (level.isOutsideBuildHeight(bp)) {
                continue;
            }
            BlockState st = level.getBlockState(bp);
            if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) {
                continue;
            }
            float hard = st.getDestroySpeed(level, bp);
            if (hard < 0.0F || hard > maxHardness) {
                continue;
            }
            if (hard <= 0.3F || spawned >= 5) {
                level.destroyBlock(bp, false);
            } else {
                FallingBlockEntity fb = FallingBlockEntity.fall(level, bp, st);
                fb.dropItem = false;
                double a2 = Math.atan2(bp.getZ() - center.z, bp.getX() - center.x) + Math.PI / 2.0D;
                fb.setDeltaMovement(Math.cos(a2) * 0.9D, 0.8D + random.nextDouble() * 0.6D, Math.sin(a2) * 0.9D);
                fb.hurtMarked = true;
                spawned++;
            }
        }
    }

    private void atmosphere() {
        // lluvia horizontal y rafagas alrededor de cada jugador cercano (solo se les envia a ellos)
        double near = pullRadius * 2.2D;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - center.x;
            double dz = p.getZ() - center.z;
            if (dx * dx + dz * dz > near * near) {
                continue;
            }
            double len = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
            double tx = -dz / len;
            double tz = dx / len;
            for (int i = 0; i < 14; i++) {
                double x = p.getX() + (random.nextDouble() - 0.5D) * 30.0D;
                double y = p.getY() + random.nextDouble() * 12.0D - 2.0D;
                double z = p.getZ() + (random.nextDouble() - 0.5D) * 30.0D;
                Fx.dirTo(level, p, ParticleTypes.RAIN, x, y, z, tx * 1.2D, -0.2D, tz * 1.2D);
            }
            if (age % 2 == 0) {
                Fx.dirTo(level, p, ParticleTypes.CLOUD, p.getX() + (random.nextDouble() - 0.5D) * 20.0D,
                        p.getY() + random.nextDouble() * 5.0D, p.getZ() + (random.nextDouble() - 0.5D) * 20.0D,
                        tx * 1.4D, 0.0D, tz * 1.4D);
            }
        }
        // viento intenso, truenos y rayos
        if (age % 10 == 0) {
            Fx.sound(level, center.x, center.y + 5.0D, center.z, SoundEvents.ELYTRA_FLYING, 10.0F, 0.4F);
        }
        if (age % 160 == 0) {
            Fx.sound(level, center.x, center.y + 20.0D, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, 10.0F, 0.6F);
        }
        if (age % 120 == 60) {
            strike();
        }
    }

    private void strike() {
        double a = random.nextDouble() * Math.PI * 2.0D;
        double r = topRadius * (0.5D + random.nextDouble() * 1.5D);
        int x = Mth.floor(center.x + Math.cos(a) * r);
        int z = Mth.floor(center.z + Math.sin(a) * r);
        if (!loaded(x, z)) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(x + 0.5D, surface(x, z), z + 0.5D);
        bolt.setVisualOnly(!grief);
        level.addFreshEntity(bolt);
    }
}
