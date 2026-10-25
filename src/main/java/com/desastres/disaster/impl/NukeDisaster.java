package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Explosion masiva: destello, onda expansiva 3D, crater enorme, escombros y un hongo de humo 3D.
 * ATENCION: destruye muchisimo terreno y puede provocar lag unos segundos.
 */
public class NukeDisaster extends Disaster {
    private static final int BLAST_TICKS = 50;

    private static final class Column {
        final int x;
        final int z;
        final double dist;
        final int ground;

        Column(int x, int z, double dist, int ground) {
            this.x = x;
            this.z = z;
            this.dist = dist;
            this.ground = ground;
        }
    }

    private final BlockPos center;
    private final int radius;
    private final int baseY;
    private final int total;
    private final List<Column> columns = new ArrayList<>();
    private int next = 0;

    public NukeDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.center = BlockPos.containing(target);
        this.radius = 12 + 7 * intensity;
        this.baseY = surface(center.getX(), center.getZ());
        this.total = scaled(BLAST_TICKS + 700);
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
        columns.sort(Comparator.comparingDouble(c -> c.dist));
        Fx.sound(level, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE, 40.0F, 0.35F);
        Fx.sound(level, target.x, target.y, target.z, SoundEvents.LIGHTNING_BOLT_THUNDER, 40.0F, 0.5F);
    }

    @Override
    protected void tick() {
        if (age >= total) {
            finish();
            return;
        }
        double cx = center.getX() + 0.5D;
        double cz = center.getZ() + 0.5D;
        double progress = Math.min(1.0D, age / (double) BLAST_TICKS);

        // Onda expansiva 3D
        if (age < BLAST_TICKS + 20) {
            float fade = (float) Math.max(0.0D, 1.0D - age / (BLAST_TICKS + 20.0D));
            showVisual(0, DisasterVisualEntity.Kind.SHOCKWAVE, cx, baseY, cz,
                    (float) (radius * 2.4D * progress), fade, 0.0F, 0.0D, 0.0D, 0.0D);
            Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER,
                    cx + (random.nextDouble() - 0.5D) * radius * progress * 1.5D, baseY + 1.0D,
                    cz + (random.nextDouble() - 0.5D) * radius * progress * 1.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        } else {
            hideVisual(0);
        }

        if (grief) {
            carve(radius * progress);
            if (age % 3 == 0 && age < BLAST_TICKS + 240) {
                igniteRing();
            }
        }
        blastEntities(progress, cx, cz);

        // Hongo: columna + sombrero
        double grow = Math.min(1.0D, age / 160.0D);
        double fade = Math.min(1.0D, (total - age) / 150.0D);
        double power = Math.max(0.02D, Math.min(grow, fade));
        double height = 46.0D + 14.0D * intensity;
        showVisual(1, DisasterVisualEntity.Kind.ASH_PLUME, cx, baseY, cz,
                (float) height, (float) (radius * 0.55D), (float) power, 0.0D, 0.0D, 0.0D);
        showVisual(2, DisasterVisualEntity.Kind.STORM_CLOUD, cx, baseY + height * power + 4.0D, cz,
                (float) (radius * 1.7D * power), 11.0F, 0.1F, 0.0D, 0.0D, 0.0D);
    }

    /** Excava el crater de dentro hacia fuera siguiendo la onda. */
    private void carve(double reach) {
        int debris = 0;
        while (next < columns.size() && columns.get(next).dist <= reach) {
            Column c = columns.get(next++);
            double ext = Math.sqrt(Math.max(0.0D, (double) radius * radius - c.dist * c.dist));
            int bottom = baseY - (int) (ext * 0.55D);
            int top = c.ground + 28;
            for (int y = top; y >= bottom; y--) {
                BlockPos bp = new BlockPos(c.x, y, c.z);
                if (level.isOutsideBuildHeight(bp)) {
                    continue;
                }
                BlockState st = level.getBlockState(bp);
                if (st.isAir() || st.getDestroySpeed(level, bp) < 0.0F) {
                    continue;
                }
                if (debris < 14 && y >= c.ground - 2 && random.nextInt(60) == 0
                        && st.getFluidState().isEmpty() && !st.hasBlockEntity()) {
                    FallingBlockEntity fb = FallingBlockEntity.fall(level, bp, st);
                    fb.dropItem = false;
                    double dx = c.x - center.getX();
                    double dz = c.z - center.getZ();
                    double len = Math.max(0.5D, Math.sqrt(dx * dx + dz * dz));
                    fb.setDeltaMovement(dx / len * 0.8D, 1.0D + random.nextDouble() * 1.2D, dz / len * 0.8D);
                    fb.hurtMarked = true;
                    debris++;
                } else {
                    level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            // suelo del crater vitrificado
            BlockPos floor = new BlockPos(c.x, bottom - 1, c.z);
            if (!level.isOutsideBuildHeight(floor)) {
                BlockState fs = level.getBlockState(floor);
                if (!fs.isAir() && fs.getDestroySpeed(level, floor) >= 0.0F) {
                    int r = random.nextInt(10);
                    if (r < 2) {
                        level.setBlock(floor, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else if (r < 3) {
                        level.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private void igniteRing() {
        for (int i = 0; i < 10; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = radius * (1.0D + random.nextDouble() * 0.9D);
            int x = Mth.floor(center.getX() + Math.cos(a) * r);
            int z = Mth.floor(center.getZ() + Math.sin(a) * r);
            if (!loaded(x, z)) {
                continue;
            }
            BlockPos bp = new BlockPos(x, surface(x, z), z);
            if (level.getBlockState(bp).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(level, bp)) {
                level.setBlock(bp, Blocks.FIRE.defaultBlockState(), 3);
            }
        }
    }

    /** La onda expansiva empuja y quema a lo que toca el frente. */
    private void blastEntities(double progress, double cx, double cz) {
        if (age > BLAST_TICKS + 15) {
            return;
        }
        double maxR = radius * 2.4D;
        double shock = maxR * progress;
        AABB box = new AABB(cx - shock, baseY - shock, cz - shock, cx + shock, baseY + shock, cz + shock);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof DisasterVisualEntity || DisasterManager.isInTransit(e) || e.isSpectator()) {
                continue;
            }
            Vec3 d = new Vec3(e.getX() - cx, e.getY() - baseY, e.getZ() - cz);
            double dist = d.length();
            if (dist < 0.01D || dist > shock || Math.abs(dist - shock) > 9.0D) {
                continue;
            }
            Vec3 push = d.scale(1.0D / dist);
            e.setDeltaMovement(push.x * 2.0D, 0.8D + random.nextDouble() * 0.5D, push.z * 2.0D);
            e.hurtMarked = true;
            if (e instanceof LivingEntity living) {
                living.setSecondsOnFire(10);
                living.hurt(level.damageSources().magic(), (float) Math.max(4.0D, 60.0D * (1.0D - dist / maxR)));
            }
        }
    }
}
