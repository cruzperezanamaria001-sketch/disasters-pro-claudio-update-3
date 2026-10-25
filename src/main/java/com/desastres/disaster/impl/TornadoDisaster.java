package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Tornado que vaga por el terreno, levanta entidades y arranca bloques debiles. */
public class TornadoDisaster extends Disaster {
    private final int duration;
    private final double height;
    private final double baseRadius;
    private final double topRadius;
    private final double pullRadius;
    private final double speed;

    private Vec3 center;
    private double heading;

    public TornadoDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.center = target;
        this.heading = random.nextDouble() * Math.PI * 2.0D;
        this.duration = scaled(1200 + 600 * intensity);
        this.height = 28 + 10 * intensity;
        this.baseRadius = 2.0D;
        this.topRadius = 6 + 2 * intensity;
        this.pullRadius = 9 + 3 * intensity;
        this.speed = 0.12D + 0.04D * intensity;
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        move();
        double grow = Math.min(1.0D, age / 80.0D);
        double shrink = Math.min(1.0D, (duration - age) / 80.0D);
        double sc = Math.max(0.05D, Math.min(grow, shrink));
        showVisual(DisasterVisualEntity.Kind.TORNADO, center.x, center.y - 1.0D, center.z,
                (float) (height * sc), (float) (baseRadius * 1.2D * sc), (float) (topRadius * 1.15D * sc), 0.0D, 0.0D, 0.0D);
        double cloudY = center.y + height * sc;
        showVisual(1, DisasterVisualEntity.Kind.STORM_CLOUD, center.x, cloudY + 3.0D, center.z,
                (float) (topRadius * 3.8D * sc), 9.0F, 0.0F, 0.0D, 0.0D, 0.0D);
        showVisual(2, DisasterVisualEntity.Kind.STORM_CLOUD, center.x, cloudY + 10.0D, center.z,
                (float) (topRadius * 2.4D * sc), 7.0F, 0.18F, 0.0D, 0.0D, 0.0D);
        visuals();
        entities();
        if (grief) {
            tearBlocks();
        }
        if (age % 10 == 0) {
            Fx.sound(level, center.x, center.y + 5, center.z, SoundEvents.ELYTRA_FLYING, 5.0F, 0.5F);
        }
    }

    private void move() {
        heading += (random.nextDouble() - 0.5D) * 0.12D;
        double nx = center.x + Math.cos(heading) * speed;
        double nz = center.z + Math.sin(heading) * speed;
        int ix = Mth.floor(nx);
        int iz = Mth.floor(nz);
        if (!loaded(ix, iz)) {
            heading += Math.PI;
            return;
        }
        double gy = surface(ix, iz);
        double ny = center.y + (gy - center.y) * 0.25D;
        center = new Vec3(nx, ny, nz);
    }

    private void visuals() {
        for (int i = 0; i < 5; i++) {
            double h = random.nextDouble() * height;
            double t = h / height;
            double r = baseRadius + (topRadius - baseRadius) * Math.pow(t, 1.3D) + random.nextDouble();
            double ang = age * 0.45D + h * 0.22D + random.nextDouble() * 0.5D;
            double px = center.x + Math.cos(ang) * r;
            double pz = center.z + Math.sin(ang) * r;
            double py = center.y + h;
            double vx = -Math.sin(ang) * 0.45D;
            double vz = Math.cos(ang) * 0.45D;
            if (i % 3 == 0) {
                Fx.dir(level, ParticleTypes.LARGE_SMOKE, px, py, pz, vx, 0.1D, vz);
            } else {
                Fx.dir(level, ParticleTypes.CLOUD, px, py, pz, vx, 0.1D, vz);
            }
        }
        Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                center.x, center.y + 0.3D, center.z, 10, baseRadius + 1.0D, 0.5D, baseRadius + 1.0D, 0.15D);
    }

    private void entities() {
        AABB box = new AABB(center.x - pullRadius, center.y - 2, center.z - pullRadius,
                center.x + pullRadius, center.y + height, center.z + pullRadius);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof DisasterVisualEntity || DisasterManager.isInTransit(e) || e.isSpectator()) {
                continue;
            }
            double dx = e.getX() - center.x;
            double dz = e.getZ() - center.z;
            double hd = Math.sqrt(dx * dx + dz * dz);
            if (hd > pullRadius) {
                continue;
            }
            double len = Math.max(hd, 0.001D);
            double tx = -dz / len;
            double tz = dx / len;
            Vec3 v = e.getDeltaMovement();
            double nx = v.x * 0.5D + tx * 0.5D - (dx / len) * 0.12D;
            double nz = v.z * 0.5D + tz * 0.5D - (dz / len) * 0.12D;
            double ny = Math.min(v.y + 0.10D + 0.04D * intensity, 0.7D + 0.2D * intensity);
            e.setDeltaMovement(nx, ny, nz);
            e.hurtMarked = true;
        }
    }

    private void tearBlocks() {
        double maxHardness = 0.6D + 0.6D * intensity;
        double reach = baseRadius + 2.0D + intensity * 1.5D;
        int spawned = 0;
        for (int i = 0; i < 14; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0D;
            double r = random.nextDouble() * reach;
            int x = Mth.floor(center.x + Math.cos(ang) * r);
            int z = Mth.floor(center.z + Math.sin(ang) * r);
            if (!loaded(x, z)) {
                continue;
            }
            int y = surface(x, z) - 1 + random.nextInt(5);
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
            if (hard <= 0.3F || spawned >= 3) {
                level.destroyBlock(bp, false);
            } else {
                FallingBlockEntity fb = FallingBlockEntity.fall(level, bp, st);
                fb.dropItem = false;
                double a2 = Math.atan2(bp.getZ() - center.z, bp.getX() - center.x) + Math.PI / 2.0D;
                fb.setDeltaMovement(Math.cos(a2) * 0.7D, 0.7D + random.nextDouble() * 0.5D, Math.sin(a2) * 0.7D);
                fb.hurtMarked = true;
                spawned++;
            }
        }
    }
}
