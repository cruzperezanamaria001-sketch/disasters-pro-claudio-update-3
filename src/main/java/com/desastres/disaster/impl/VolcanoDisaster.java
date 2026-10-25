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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Un volcan que crece desde el suelo, abre un crater con lava y erupciona
 * lanzando bombas de magma, humo, ceniza y rios de lava.
 */
public class VolcanoDisaster extends Disaster {
    private static final int BUILD_TICKS = 260;

    private static final class Column {
        final int x;
        final int z;
        final double dist;
        final double fullHeight;
        int filled;

        Column(int x, int z, double dist, double fullHeight, int ground) {
            this.x = x;
            this.z = z;
            this.dist = dist;
            this.fullHeight = fullHeight;
            this.filled = ground;
        }
    }

    private final BlockPos center;
    private final int height;
    private final int baseRadius;
    private final int craterRadius;
    private final int baseY;
    private final int eruptTicks;
    private final double rimHeight;
    private final int lavaY;
    private final List<Column> columns = new ArrayList<>();
    private boolean lavaFilled = false;

    public VolcanoDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.center = BlockPos.containing(target);
        this.height = 12 + 4 * intensity;
        this.baseRadius = (int) (height * 1.6D);
        this.craterRadius = 2 + intensity;
        this.eruptTicks = scaled(1200 + 600 * intensity);
        this.baseY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, center.getX(), center.getZ());
        this.rimHeight = height * Math.pow(1.0D - (double) craterRadius / baseRadius, 1.4D);
        this.lavaY = grief ? baseY + (int) Math.floor(rimHeight) - 1 : baseY;

        double craterDepth = 3 + intensity;
        for (int dx = -baseRadius; dx <= baseRadius; dx++) {
            for (int dz = -baseRadius; dz <= baseRadius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > baseRadius) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!loaded(x, z)) {
                    continue;
                }
                double h;
                if (d <= craterRadius) {
                    h = rimHeight - craterDepth * (1.0D - (d * d) / ((double) craterRadius * craterRadius));
                } else {
                    h = height * Math.pow(1.0D - d / baseRadius, 1.4D);
                }
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                columns.add(new Column(x, z, d, h, ground));
            }
        }
        Fx.sound(level, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE, 6.0F, 0.3F);
    }

    @Override
    protected void tick() {
        if (age < BUILD_TICKS) {
            buildPhase();
        } else {
            erupt(age - BUILD_TICKS);
        }
    }

    // ---------------------------------------------------------------- construccion

    private void buildPhase() {
        double scale = Math.min(1.0D, (age + 1.0D) / BUILD_TICKS);
        if (grief && age % 4 == 0) {
            build(scale);
        }
        double px = center.getX() + 0.5D;
        double pz = center.getZ() + 0.5D;

        // polvo y temblor alrededor de la base
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = random.nextDouble() * baseRadius;
            double x = px + Math.cos(a) * r;
            double z = pz + Math.sin(a) * r;
            if (!loaded(Mth.floor(x), Mth.floor(z))) {
                continue;
            }
            double y = surface(Mth.floor(x), Mth.floor(z));
            Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    x, y + 0.2D, z, 6, 0.6D, 0.2D, 0.6D, 0.05D);
        }
        Fx.burst(level, ParticleTypes.LARGE_SMOKE, px, baseY + height * scale + 1.0D, pz, 3, 1.0D, 0.5D, 1.0D, 0.05D);
        if (age % 20 == 0) {
            Fx.sound(level, px, baseY, pz, SoundEvents.GENERIC_EXPLODE, 3.0F, 0.25F);
        }
        if (age == BUILD_TICKS - 1 && grief) {
            build(1.0D);
            fillLava();
        }
    }

    private void build(double scale) {
        for (Column c : columns) {
            int top = baseY + (int) Math.floor(c.fullHeight * scale);
            for (int y = c.filled; y < top; y++) {
                BlockPos bp = new BlockPos(c.x, y, c.z);
                BlockState st = level.getBlockState(bp);
                if (st.isAir() || st.canBeReplaced()) {
                    level.setBlock(bp, pickRock(), Block.UPDATE_CLIENTS);
                }
            }
            if (top > c.filled) {
                c.filled = top;
            }
        }
    }

    private BlockState pickRock() {
        int r = random.nextInt(20);
        if (r < 9) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        if (r < 15) {
            return Blocks.BASALT.defaultBlockState();
        }
        if (r < 18) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        return Blocks.MAGMA_BLOCK.defaultBlockState();
    }

    private void fillLava() {
        if (lavaFilled) {
            return;
        }
        lavaFilled = true;
        for (Column c : columns) {
            if (c.dist > craterRadius) {
                continue;
            }
            int surfaceTop = baseY + (int) Math.floor(c.fullHeight);
            for (int y = surfaceTop; y <= lavaY; y++) {
                BlockPos bp = new BlockPos(c.x, y, c.z);
                BlockState st = level.getBlockState(bp);
                if (st.isAir() || st.canBeReplaced()) {
                    level.setBlock(bp, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // ---------------------------------------------------------------- erupcion

    private void erupt(int t) {
        if (t >= eruptTicks) {
            finish();
            return;
        }
        double power;
        if (t < 100) {
            power = t / 100.0D;
        } else if (t > eruptTicks - 200) {
            power = Math.max(0.2D, (eruptTicks - t) / 200.0D);
        } else {
            power = 1.0D;
        }

        double px = center.getX() + 0.5D;
        double pz = center.getZ() + 0.5D;
        double top = lavaY + 1.0D;
        showVisual(DisasterVisualEntity.Kind.ASH_PLUME, px, top, pz,
                50.0F + 14.0F * intensity, 11.0F + 4.0F * intensity, (float) power, 0.0D, 0.0D, 0.0D);

        if (age % 6 == 0) {
            int n = (int) Math.ceil((1 + intensity) * power * 0.5D);
            for (int i = 0; i < n; i++) {
                launchBomb(px, pz);
            }
        }

        // columna de humo y ceniza
        double windX = 0.03D;
        double windZ = 0.015D;
        int plumes = (int) (3 * power) + 1;
        for (int i = 0; i < plumes; i++) {
            double ox = (random.nextDouble() - 0.5D) * craterRadius;
            double oz = (random.nextDouble() - 0.5D) * craterRadius;
            Fx.dir(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px + ox, top, pz + oz,
                    windX, 0.35D + random.nextDouble() * 0.25D, windZ);
            Fx.dir(level, ParticleTypes.LARGE_SMOKE, px + ox, top, pz + oz,
                    windX, 0.25D + random.nextDouble() * 0.25D, windZ);
        }
        Fx.burst(level, ParticleTypes.LAVA, px, top + 1.0D, pz, (int) (6 * power) + 1, craterRadius * 0.5D, 1.0D, craterRadius * 0.5D, 0.0D);
        Fx.burst(level, ParticleTypes.ASH, px, top + 8.0D, pz, 20, 20.0D, 10.0D, 20.0D, 0.0D);

        // rios de lava
        if (grief && t % 12 == 0 && power > 0.3D) {
            lavaFlow(px, pz);
        }

        if (t % 30 == 0) {
            Fx.sound(level, px, top, pz, SoundEvents.GENERIC_EXPLODE, 4.0F, 0.4F);
        }
    }

    /**
     * Lanza una bomba volcanica en una direccion aleatoria. La velocidad horizontal sigue una
     * distribucion que da muchas bombas cerca y algunas que caen a 100 bloques o mas del volcan.
     */
    private void launchBomb(double px, double pz) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double reach = random.nextDouble();
        double hs = 0.35D + reach * reach * (1.2D + 0.2D * intensity);
        double vy = 1.0D + random.nextDouble() * 1.0D + hs * 0.25D;
        Vec3 start = new Vec3(px + (random.nextDouble() - 0.5D) * craterRadius, lavaY + 2.0D,
                pz + (random.nextDouble() - 0.5D) * craterRadius);
        Vec3 vel = new Vec3(Math.cos(angle) * hs, vy, Math.sin(angle) * hs);
        float size = 0.55F + random.nextFloat() * 0.5F + 0.1F * intensity;
        DisasterManager.add(new LavaBombDisaster(level, start, vel, intensity, 1.8F + 0.6F * intensity, size, false));
    }

    private void lavaFlow(double px, double pz) {
        double a = random.nextDouble() * Math.PI * 2.0D;
        double r = craterRadius + 0.5D + random.nextDouble() * (baseRadius * 0.3D);
        int x = Mth.floor(px + Math.cos(a) * r);
        int z = Mth.floor(pz + Math.sin(a) * r);
        if (!loaded(x, z)) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos bp = new BlockPos(x, y, z);
        if (level.isOutsideBuildHeight(bp)) {
            return;
        }
        if (level.getBlockState(bp).isAir() && !level.getBlockState(bp.below()).is(Blocks.LAVA)) {
            level.setBlock(bp, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
