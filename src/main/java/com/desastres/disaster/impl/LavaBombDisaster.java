package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Proyectil balistico (con gravedad) dibujado como roca 3D.
 * Se usa para las bombas del volcan, la lluvia de fuego (ice = false) y el granizo (ice = true).
 */
public class LavaBombDisaster extends Disaster {
    private static final double GRAVITY = 0.05D;

    private Vec3 current;
    private Vec3 vel;
    private final float power;
    private final float size;
    private final boolean ice;
    /** Se avisa al "dueno" (p. ej. la granizada) cuando esta bola desaparece. */
    private Runnable onFinished;

    public LavaBombDisaster(ServerLevel level, Vec3 start, Vec3 velocity, int intensity,
                            float power, float size, boolean ice) {
        super(level, start, intensity);
        this.current = start;
        this.vel = velocity;
        this.power = power;
        this.size = size;
        this.ice = ice;
    }

    public LavaBombDisaster withOwnerCallback(Runnable callback) {
        this.onFinished = callback;
        return this;
    }

    @Override
    protected void onEnd() {
        if (onFinished != null) {
            onFinished.run();
            onFinished = null;
        }
    }

    @Override
    protected boolean isMinor() {
        return true;
    }

    @Override
    protected void tick() {
        if (age > 500) {
            impact();
            return;
        }
        vel = vel.scale(0.995D).add(0.0D, -GRAVITY, 0.0D);
        int steps = Math.max(1, (int) Math.ceil(vel.length()));
        Vec3 step = vel.scale(1.0D / steps);
        for (int i = 0; i < steps; i++) {
            current = current.add(step);
            if (current.y <= level.getMinBuildHeight() + 1) {
                impact();
                return;
            }
            if (current.y >= level.getMaxBuildHeight()) {
                continue;
            }
            BlockPos bp = BlockPos.containing(current);
            if (!loaded(bp)) {
                continue;
            }
            BlockState st = level.getBlockState(bp);
            if (!st.getCollisionShape(level, bp).isEmpty() || !st.getFluidState().isEmpty()) {
                impact();
                return;
            }
        }
        Vec3 dir = vel.normalize();
        showVisual(DisasterVisualEntity.Kind.METEOR, current.x, current.y, current.z,
                size, ice ? 1.0F : 0.0F, 0.0F, dir.x, dir.y, dir.z);
        if (!ice) {
            Fx.burst(level, ParticleTypes.FLAME, current.x, current.y, current.z, 2, 0.3D, 0.3D, 0.3D, 0.02D);
            Fx.burst(level, ParticleTypes.SMOKE, current.x, current.y, current.z, 1, 0.3D, 0.3D, 0.3D, 0.01D);
        }
    }

    private void impact() {
        // Si cae en una zona sin cargar, no hacemos nada (evita generar chunks lejanos)
        if (!loaded(BlockPos.containing(current))) {
            finish();
            return;
        }
        if (ice) {
            hailImpact();
        } else {
            fireImpact();
        }
        finish();
    }

    private void fireImpact() {
        Vec3 p = current;
        Level.ExplosionInteraction mode = grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(null, p.x, p.y, p.z, power, grief, mode);
        Fx.burst(level, ParticleTypes.LAVA, p.x, p.y, p.z, 20, power * 0.4D, 0.4D, power * 0.4D, 0.0D);
        Fx.burst(level, ParticleTypes.LARGE_SMOKE, p.x, p.y + 1.0D, p.z, 6, 0.8D, 0.8D, 0.8D, 0.03D);

        AABB box = new AABB(p, p).inflate(power * 1.6D);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            living.setSecondsOnFire(6);
        }
        if (grief) {
            leaveRemains(BlockPos.containing(p));
        }
    }

    /** Charco de magma / lava donde cayo la bomba. */
    private void leaveRemains(BlockPos c) {
        if (!loaded(c)) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 0; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean center = dx == 0 && dy == 0 && dz == 0;
                    if (!center && random.nextInt(3) != 0) {
                        continue;
                    }
                    BlockPos bp = c.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(bp)) {
                        continue;
                    }
                    BlockState st = level.getBlockState(bp);
                    if (!st.isAir() && !st.canBeReplaced()) {
                        continue;
                    }
                    if (center && random.nextInt(100) < 40) {
                        level.setBlock(bp, Blocks.LAVA.defaultBlockState(), 3);
                    } else {
                        level.setBlock(bp, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private void hailImpact() {
        Vec3 p = current;
        Fx.burst(level, ParticleTypes.SNOWFLAKE, p.x, p.y + 0.3D, p.z, 12, 0.4D, 0.3D, 0.4D, 0.05D);
        Fx.burst(level, ParticleTypes.POOF, p.x, p.y + 0.2D, p.z, 3, 0.3D, 0.1D, 0.3D, 0.02D);
        Fx.sound(level, p.x, p.y, p.z, SoundEvents.GLASS_BREAK, 1.5F, 1.4F + random.nextFloat() * 0.4F);

        AABB box = new AABB(p, p).inflate(2.0D);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            living.hurt(level.damageSources().magic(), 2.0F + 1.5F * intensity);
        }
        if (grief && random.nextInt(4) == 0) {
            BlockPos above = BlockPos.containing(p).above();
            if (loaded(above) && level.getBlockState(above).isAir()
                    && Blocks.SNOW.defaultBlockState().canSurvive(level, above)) {
                level.setBlock(above, Blocks.SNOW.defaultBlockState(), 3);
            }
        }
    }
}
