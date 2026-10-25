package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Sumidero: el suelo se hunde en un embudo profundo, tragandose arboles y casas. */
public class SinkholeDisaster extends Disaster {
    private static final int GROW_TICKS = 200;
    private static final int TOTAL_TICKS = 320;

    private static final class Column {
        final int x;
        final int z;
        final double dist;
        final int ground;
        int floor;

        Column(int x, int z, double dist, int ground) {
            this.x = x;
            this.z = z;
            this.dist = dist;
            this.ground = ground;
            this.floor = ground;
        }
    }

    private final BlockPos center;
    private final int radius;
    private final int depth;
    private final int baseY;
    private final List<Column> columns = new ArrayList<>();

    public SinkholeDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.center = BlockPos.containing(target);
        this.radius = 6 + 4 * intensity;
        this.depth = 10 + 7 * intensity;
        this.baseY = surface(center.getX(), center.getZ());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (loaded(x, z)) {
                    columns.add(new Column(x, z, d, surface(x, z)));
                }
            }
        }
    }

    @Override
    protected void tick() {
        if (age >= TOTAL_TICKS) {
            finish();
            return;
        }
        double s = Math.min(1.0D, age / (double) GROW_TICKS);
        if (grief) {
            if (age == 0) {
                clearAboveGround();
            }
            deepen(s);
            if (age == GROW_TICKS && intensity >= 3) {
                fillLava();
            }
        }
        effects(s);
    }

    /** Arboles, casas y todo lo que haya sobre el suelo dentro del radio desaparece. */
    private void clearAboveGround() {
        for (Column c : columns) {
            for (int y = c.ground; y < c.ground + 26; y++) {
                BlockPos bp = new BlockPos(c.x, y, c.z);
                if (level.isOutsideBuildHeight(bp)) {
                    break;
                }
                BlockState st = level.getBlockState(bp);
                if (!st.isAir() && st.getDestroySpeed(level, bp) >= 0.0F) {
                    level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private void deepen(double s) {
        for (Column c : columns) {
            double k = 1.0D - (c.dist * c.dist) / ((double) radius * radius);
            int target = baseY - (int) (depth * s * k);
            if (target >= c.floor) {
                continue;
            }
            for (int y = c.floor - 1; y >= target; y--) {
                BlockPos bp = new BlockPos(c.x, y, c.z);
                if (level.isOutsideBuildHeight(bp)) {
                    break;
                }
                BlockState st = level.getBlockState(bp);
                if (st.isAir()) {
                    continue;
                }
                if (st.getDestroySpeed(level, bp) < 0.0F) {
                    break;
                }
                level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            c.floor = target;
        }
    }

    private void fillLava() {
        for (Column c : columns) {
            if (c.dist < radius * 0.4D) {
                BlockPos bp = new BlockPos(c.x, c.floor, c.z);
                if (!level.isOutsideBuildHeight(bp) && level.getBlockState(bp).isAir()) {
                    level.setBlock(bp, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private void effects(double s) {
        double cx = center.getX() + 0.5D;
        double cz = center.getZ() + 0.5D;
        // nube de polvo 3D (s3 negativo = modo polvo en el renderizador)
        double power = Math.sin(Math.min(1.0D, age / (double) TOTAL_TICKS) * Math.PI);
        showVisual(DisasterVisualEntity.Kind.ASH_PLUME, cx, baseY, cz,
                22.0F + 6.0F * intensity, radius * 0.9F, (float) -Math.max(0.05D, power), 0.0D, 0.0D, 0.0D);

        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = radius * (0.5D + random.nextDouble() * 0.7D);
            double x = cx + Math.cos(a) * r;
            double z = cz + Math.sin(a) * r;
            if (!loaded(Mth.floor(x), Mth.floor(z))) {
                continue;
            }
            Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    x, surface(Mth.floor(x), Mth.floor(z)) + 0.3D, z, 8, 0.8D, 0.3D, 0.8D, 0.06D);
        }
        if (age % 10 == 0) {
            Fx.sound(level, cx, baseY, cz, SoundEvents.GENERIC_EXPLODE, 4.0F, 0.25F);
        }
    }
}
