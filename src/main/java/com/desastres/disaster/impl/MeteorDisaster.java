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

/** Un meteorito que cae del cielo hacia el objetivo y explota al impactar. */
public class MeteorDisaster extends Disaster {
    private final Vec3 velocity;
    private final float power;
    private Vec3 current;

    public MeteorDisaster(ServerLevel level, Vec3 target, int intensity, float power) {
        super(level, target, intensity);
        this.power = power;
        double ang = random.nextDouble() * Math.PI * 2.0D;
        double horiz = 50.0D + random.nextDouble() * 60.0D;
        double startY = Math.min(level.getMaxBuildHeight() - 4, target.y + 150.0D);
        this.current = new Vec3(target.x + Math.cos(ang) * horiz, startY, target.z + Math.sin(ang) * horiz);
        this.velocity = target.subtract(current).normalize().scale(2.0D + 0.25D * intensity);
    }

    @Override
    protected boolean isMinor() {
        return true;
    }

    @Override
    protected void tick() {
        if (age > 600) {
            impact();
            return;
        }
        int steps = (int) Math.ceil(velocity.length());
        Vec3 step = velocity.scale(1.0D / steps);
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
        showVisual(DisasterVisualEntity.Kind.METEOR, current.x, current.y, current.z,
                0.8F + power * 0.22F, 0.0F, 0.0F, velocity.normalize().x, velocity.normalize().y, velocity.normalize().z);
        trail();
    }

    private void trail() {
        Fx.burst(level, ParticleTypes.FLAME, current.x, current.y, current.z, 5, 1.0D, 1.0D, 1.0D, 0.05D);
        Fx.burst(level, ParticleTypes.LARGE_SMOKE, current.x, current.y, current.z, 3, 1.4D, 1.4D, 1.4D, 0.02D);
        Vec3 back = velocity.normalize().scale(-2.0D);
        for (int i = 1; i <= 3; i++) {
            Vec3 q = current.add(back.scale(i));
            double spread = 0.5D + i * 0.15D;
            if (i % 2 == 0) {
                Fx.burst(level, ParticleTypes.FLAME, q.x, q.y, q.z, 4, spread, spread, spread, 0.01D);
            } else {
                Fx.burst(level, ParticleTypes.SMOKE, q.x, q.y, q.z, 4, spread, spread, spread, 0.01D);
            }
        }
        if (age % 5 == 0 && current.distanceToSqr(pos) < 100.0D * 100.0D) {
            Fx.sound(level, current.x, current.y, current.z, SoundEvents.FIRECHARGE_USE, 5.0F, 0.5F);
        }
    }

    private void impact() {
        if (!loaded(BlockPos.containing(current))) {
            finish();
            return;
        }
        Vec3 p = current;
        Level.ExplosionInteraction mode = grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(null, p.x, p.y, p.z, power, grief, mode);
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 4, power * 0.3D, power * 0.3D, power * 0.3D, 0.0D);
        Fx.burst(level, ParticleTypes.LAVA, p.x, p.y, p.z, 60, power * 0.5D, power * 0.3D, power * 0.5D, 0.0D);

        AABB box = new AABB(p, p).inflate(power * 2.0D);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            living.setSecondsOnFire(8);
            Vec3 away = living.position().subtract(p).normalize().scale(1.5D);
            living.setDeltaMovement(living.getDeltaMovement().add(away.x, 0.6D, away.z));
            living.hurtMarked = true;
        }

        if (grief) {
            leaveRemains(p);
        }
        finish();
    }

    /** Deja un "nucleo" del meteorito medio enterrado en el crater. */
    private void leaveRemains(Vec3 p) {
        int r = 1 + intensity;
        BlockPos c = BlockPos.containing(p);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= 0; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) {
                        continue;
                    }
                    BlockPos bp = c.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(bp) || !loaded(bp)) {
                        continue;
                    }
                    BlockState st = level.getBlockState(bp);
                    if (!st.isAir() && !st.canBeReplaced()) {
                        continue;
                    }
                    int roll = random.nextInt(10);
                    BlockState rock;
                    if (roll < 5) {
                        rock = Blocks.OBSIDIAN.defaultBlockState();
                    } else if (roll < 8) {
                        rock = Blocks.MAGMA_BLOCK.defaultBlockState();
                    } else {
                        rock = Blocks.BLACKSTONE.defaultBlockState();
                    }
                    level.setBlock(bp, rock, 3);
                }
            }
        }
    }
}
