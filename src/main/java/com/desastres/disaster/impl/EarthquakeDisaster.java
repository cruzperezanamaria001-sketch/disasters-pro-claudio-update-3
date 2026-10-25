package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Terremoto: sacude entidades, agrieta el suelo, derrumba estructuras debiles y abre fisuras profundas. */
public class EarthquakeDisaster extends Disaster {
    private static final class Fissure {
        double x;
        double z;
        double angle;
        int steps;
    }

    private final int duration;
    private final int radius;
    private final List<Fissure> fissures = new ArrayList<>();

    public EarthquakeDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.duration = scaled(260 + 120 * intensity);
        this.radius = 18 + 10 * intensity;
        int n = 1 + intensity;
        for (int i = 0; i < n; i++) {
            Fissure f = new Fissure();
            f.x = target.x;
            f.z = target.z;
            f.angle = (Math.PI * 2.0D / n) * i + random.nextDouble() * 0.8D;
            fissures.add(f);
        }
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        if (age % 3 == 0) {
            shakeEntities();
        }
        if (grief) {
            crumble();
            if (age > 20) {
                growFissures();
            }
        }
        effects();
    }

    private boolean grounded(Entity e) {
        BlockPos below = BlockPos.containing(e.getX(), e.getY() - 0.2D, e.getZ());
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private void shakeEntities() {
        AABB box = new AABB(pos.x - radius, pos.y - 64, pos.z - radius, pos.x + radius, pos.y + 64, pos.z + radius);
        double k = 0.10D + 0.06D * intensity;
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof DisasterVisualEntity || DisasterManager.isInTransit(e) || e.isSpectator() || e.isPassenger()) {
                continue;
            }
            if (e instanceof Player p && p.getAbilities().flying) {
                continue;
            }
            double dx = e.getX() - pos.x;
            double dz = e.getZ() - pos.z;
            if (dx * dx + dz * dz > (double) radius * radius) {
                continue;
            }
            if (!grounded(e)) {
                continue;
            }
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x + (random.nextDouble() - 0.5D) * k * 2.0D,
                    0.12D + 0.05D * intensity,
                    v.z + (random.nextDouble() - 0.5D) * k * 2.0D);
            e.hurtMarked = true;
        }
    }

    /** Agrieta el suelo y tira abajo bloques debiles de estructuras. */
    private void crumble() {
        int attempts = 6 + 3 * intensity;
        for (int i = 0; i < attempts; i++) {
            double r = radius * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2.0D;
            int x = Mth.floor(pos.x + Math.cos(a) * r);
            int z = Mth.floor(pos.z + Math.sin(a) * r);
            if (!loaded(x, z)) {
                continue;
            }
            int y = surface(x, z) - 1;
            BlockPos bp = new BlockPos(x, y, z);
            if (level.isOutsideBuildHeight(bp)) {
                continue;
            }
            BlockState st = level.getBlockState(bp);
            if (st.isAir() || !st.getFluidState().isEmpty()) {
                continue;
            }
            if (st.is(BlockTags.DIRT) && random.nextInt(10) < 6) {
                level.setBlock(bp, Blocks.COARSE_DIRT.defaultBlockState(), 2);
            } else if (st.is(BlockTags.BASE_STONE_OVERWORLD) && random.nextInt(10) < 6) {
                level.setBlock(bp, random.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), 2);
            } else {
                continue;
            }
            Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, st), x + 0.5D, y + 1.1D, z + 0.5D, 10, 0.5D, 0.2D, 0.5D, 0.05D);
        }

        // estructuras: bloques debiles sobre el suelo se desprenden
        int structural = 3 + intensity * 2;
        double maxHardness = 0.5D + 0.7D * intensity;
        for (int i = 0; i < structural; i++) {
            double r = radius * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2.0D;
            int x = Mth.floor(pos.x + Math.cos(a) * r);
            int z = Mth.floor(pos.z + Math.sin(a) * r);
            if (!loaded(x, z)) {
                continue;
            }
            int y = surface(x, z) + random.nextInt(8);
            BlockPos bp = new BlockPos(x, y, z);
            if (level.isOutsideBuildHeight(bp)) {
                continue;
            }
            BlockState st = level.getBlockState(bp);
            if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) {
                continue;
            }
            float hard = st.getDestroySpeed(level, bp);
            if (hard >= 0.0F && hard <= maxHardness) {
                FallingBlockEntity fb = FallingBlockEntity.fall(level, bp, st);
                fb.dropItem = false;
            }
        }
    }

    private void growFissures() {
        for (Fissure f : fissures) {
            if (f.steps >= radius) {
                continue;
            }
            f.angle += (random.nextDouble() - 0.5D) * 0.25D;
            f.x += Math.cos(f.angle);
            f.z += Math.sin(f.angle);
            f.steps++;
            carve(f);
        }
    }

    private void carve(Fissure f) {
        double nx = -Math.sin(f.angle);
        double nz = Math.cos(f.angle);
        int half = intensity >= 2 ? 1 : 0;
        int depth = 5 + 3 * intensity;
        for (int w = -half; w <= half; w++) {
            int x = Mth.floor(f.x + nx * w);
            int z = Mth.floor(f.z + nz * w);
            if (!loaded(x, z)) {
                continue;
            }
            int top = surface(x, z) - 1;
            BlockState topState = level.getBlockState(new BlockPos(x, top, z));
            if (!topState.getFluidState().isEmpty()) {
                continue;
            }
            for (int d = 0; d <= depth; d++) {
                BlockPos bp = new BlockPos(x, top - d, z);
                if (level.isOutsideBuildHeight(bp)) {
                    break;
                }
                BlockState st = level.getBlockState(bp);
                if (st.isAir()) {
                    continue;
                }
                if (st.getDestroySpeed(level, bp) < 0.0F || !st.getFluidState().isEmpty()) {
                    break;
                }
                if (d == depth && intensity >= 3) {
                    level.setBlock(bp, Blocks.LAVA.defaultBlockState(), 3);
                } else {
                    level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        Fx.burst(level, ParticleTypes.POOF, f.x, top(f), f.z, 4, 0.6D, 0.3D, 0.6D, 0.05D);
    }

    private double top(Fissure f) {
        int x = Mth.floor(f.x);
        int z = Mth.floor(f.z);
        return loaded(x, z) ? surface(x, z) + 0.5D : pos.y;
    }

    private void effects() {
        if (age % 8 == 0) {
            Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, 3.0F, 0.3F + random.nextFloat() * 0.2F);
        }
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - pos.x;
            double dz = p.getZ() - pos.z;
            if (dx * dx + dz * dz > (double) (radius + 10) * (radius + 10)) {
                continue;
            }
            Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    p.getX(), p.getY() + 0.2D, p.getZ(), 12, 4.0D, 0.3D, 4.0D, 0.08D);
        }
    }
}
