package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Ventisca: nieve horizontal, lentitud, congelacion para quien este a la intemperie y mundo helado. */
public class BlizzardDisaster extends Disaster {
    private final int duration;
    private final double radius;
    private double windAngle;

    public BlizzardDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.duration = scaled(1800 + 600 * intensity);
        this.radius = 32 + 16 * intensity;
        this.windAngle = random.nextDouble() * Math.PI * 2.0D;
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        if (loaded(Mth.floor(pos.x), Mth.floor(pos.z))) {
            double cloudY = Math.min(level.getMaxBuildHeight() - 20, surface(Mth.floor(pos.x), Mth.floor(pos.z)) + 55);
            showVisual(DisasterVisualEntity.Kind.STORM_CLOUD, pos.x, cloudY, pos.z,
                    (float) radius, 16.0F, 1.0F, 0.0D, 0.0D, 0.0D);
        }
        windAngle += (random.nextDouble() - 0.5D) * 0.04D;
        double wx = Math.cos(windAngle);
        double wz = Math.sin(windAngle);
        double fade;
        if (age < 60) {
            fade = age / 60.0D;
        } else if (age > duration - 100) {
            fade = Math.max(0.0D, (duration - age) / 100.0D);
        } else {
            fade = 1.0D;
        }

        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - pos.x;
            double dz = p.getZ() - pos.z;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            if (!level.canSeeSky(p.blockPosition())) {
                continue;
            }
            int count = (int) (28 * fade);
            for (int i = 0; i < count; i++) {
                double x = p.getX() + (random.nextDouble() - 0.5D) * 36.0D - wx * 8.0D;
                double y = p.getY() + random.nextDouble() * 14.0D - 3.0D;
                double z = p.getZ() + (random.nextDouble() - 0.5D) * 36.0D - wz * 8.0D;
                double s = 0.9D + random.nextDouble() * 0.8D;
                Fx.dirTo(level, p, ParticleTypes.SNOWFLAKE, x, y, z, wx * s, -0.12D - random.nextDouble() * 0.1D, wz * s);
            }
            if (age % 2 == 0) {
                for (int i = 0; i < 2; i++) {
                    double x = p.getX() + (random.nextDouble() - 0.5D) * 24.0D - wx * 6.0D;
                    double y = p.getY() + random.nextDouble() * 6.0D;
                    double z = p.getZ() + (random.nextDouble() - 0.5D) * 24.0D - wz * 6.0D;
                    Fx.dirTo(level, p, ParticleTypes.CLOUD, x, y, z, wx * 0.8D, 0.0D, wz * 0.8D);
                }
            }
        }

        if (age % 10 == 0) {
            affectEntities();
        }
        if (grief && age % 2 == 0) {
            freezeWorld();
        }
    }

    private void affectEntities() {
        AABB box = new AABB(pos.x - radius, level.getMinBuildHeight(), pos.z - radius,
                pos.x + radius, level.getMaxBuildHeight(), pos.z + radius);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (le.isSpectator()) {
                continue;
            }
            if (le instanceof Player p && p.isCreative()) {
                continue;
            }
            double dx = le.getX() - pos.x;
            double dz = le.getZ() - pos.z;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            if (!level.canSeeSky(le.blockPosition())) {
                continue;
            }
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, Math.min(1, intensity - 1), false, false, true));
            if (le.canFreeze()) {
                int frozen = le.getTicksFrozen() + 18 + 12 * intensity;
                le.setTicksFrozen(Math.min(frozen, le.getTicksRequiredToFreeze() + 40));
            }
        }
    }

    private void freezeWorld() {
        for (int i = 0; i < 20; i++) {
            double r = radius * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2.0D;
            int x = Mth.floor(pos.x + Math.cos(a) * r);
            int z = Mth.floor(pos.z + Math.sin(a) * r);
            if (!loaded(x, z)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos bp = new BlockPos(x, y, z);
            BlockPos below = bp.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(Blocks.WATER) && belowState.getFluidState().isSource()) {
                level.setBlockAndUpdate(below, Blocks.ICE.defaultBlockState());
                continue;
            }
            BlockState cur = level.getBlockState(bp);
            if (cur.is(Blocks.SNOW)) {
                int layers = cur.getValue(SnowLayerBlock.LAYERS);
                if (layers < 3) {
                    level.setBlockAndUpdate(bp, cur.setValue(SnowLayerBlock.LAYERS, layers + 1));
                }
            } else if (cur.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, bp)) {
                level.setBlockAndUpdate(bp, Blocks.SNOW.defaultBlockState());
            }
        }
    }
}
