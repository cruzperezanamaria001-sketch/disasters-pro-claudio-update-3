package com.desastres.disaster.impl;

import com.desastres.config.DisasterConfig;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Colision planetaria con Marte.
 *
 * Fase 1 (aproximacion): Marte (esfera 3D con textura) aparece en el cielo y se acerca por una trayectoria
 * recta con aceleracion; su radio y su brillo atmosferico crecen. El cliente anade vibracion de camara
 * (CameraShakeHandler) segun el progreso. Hay lluvia de brasas, humo y retumbos.
 *
 * Fase 2 (impacto): destello, onda expansiva 3D y destruccion del terreno SOLO dentro del radio configurable
 * (marsImpactRadius): se excava columna a columna, de dentro hacia fuera, con un presupuesto de bloques por
 * tick para no congelar el servidor. Los escombros que salen volando tienen poca velocidad horizontal para
 * caer tambien dentro del area. Fuera del radio no se toca ningun bloque (las entidades si pueden recibir la onda).
 */
public class MarsCollisionDisaster extends Disaster {
    private static final int POST_IMPACT_TICKS = 480;
    private static final int BLOCK_BUDGET_PER_TICK = 6000;
    private static final int SHOCK_TICKS = 90;

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
    private final int approachTicks;
    private final float marsEnd;
    private final int baseY;
    private final Vec3 p0;
    private final Vec3 pEnd;
    private final List<Column> columns = new ArrayList<>();

    private int next = 0;
    private boolean impacted = false;
    private int impactAge = 0;

    public MarsCollisionDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.center = BlockPos.containing(target);
        double factor = 0.6D + 0.2D * intensity; // intensidad 2 = radio configurado
        this.radius = Mth.clamp((int) Math.round(DisasterConfig.MARS_IMPACT_RADIUS.get() * factor), 12, 110);
        this.approachTicks = scaled(900 + 300 * intensity);
        this.marsEnd = (float) Math.max(18.0D, radius * 0.9D);
        this.baseY = surface(center.getX(), center.getZ());
        double ang = random.nextDouble() * Math.PI * 2.0D;
        double maxY = level.getMaxBuildHeight() - 6;
        this.p0 = new Vec3(target.x + Math.cos(ang) * 130.0D, Math.min(maxY, baseY + 120.0D), target.z + Math.sin(ang) * 130.0D);
        this.pEnd = new Vec3(target.x, baseY + marsEnd * 0.45D, target.z);
        Fx.sound(level, target.x, target.y, target.z, SoundEvents.ENDER_DRAGON_GROWL, 12.0F, 0.3F);
    }

    @Override
    protected void tick() {
        if (!impacted) {
            approach();
        } else {
            afterImpact();
        }
    }

    // ------------------------------------------------------------------ aproximacion

    private void approach() {
        double u = Math.min(1.0D, (age + 1.0D) / approachTicks);
        double e = Math.pow(u, 2.2D);           // lento al principio, acelera al final
        Vec3 c = p0.add(pEnd.subtract(p0).scale(e));
        float r = (float) Mth.lerp(Math.pow(e, 0.6D), 5.0D, marsEnd);
        showVisual(0, DisasterVisualEntity.Kind.MARS, c.x, c.y, c.z, r, (float) u, 0.0F, 0.0D, 0.0D, 0.0D);

        double cx = center.getX() + 0.5D;
        double cz = center.getZ() + 0.5D;
        if (u > 0.25D) {
            // lluvia de brasas y cenizas sobre la zona de impacto
            int n = (int) (2 + 8 * u);
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double d = radius * 1.6D * Math.sqrt(random.nextDouble());
                double x = cx + Math.cos(a) * d;
                double z = cz + Math.sin(a) * d;
                double y = baseY + 25.0D + random.nextDouble() * 55.0D;
                Fx.dir(level, ParticleTypes.FLAME, x, y, z, (random.nextDouble() - 0.5D) * 0.1D, -0.9D - random.nextDouble() * 0.5D, (random.nextDouble() - 0.5D) * 0.1D);
            }
            Fx.burst(level, ParticleTypes.ASH, cx, baseY + 12.0D, cz, 10, radius * 1.2D, 12.0D, radius * 1.2D, 0.0D);
        }
        if (age % 15 == 0) {
            Fx.sound(level, cx, baseY, cz, SoundEvents.GENERIC_EXPLODE, (float) (3.0D + 9.0D * u), 0.25F);
        }
        if (u > 0.6D && age % 40 == 0) {
            Fx.burst(level, ParticleTypes.FLASH, cx, baseY + 30.0D, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (u >= 1.0D) {
            impact();
        }
    }

    // ------------------------------------------------------------------ impacto

    private void impact() {
        impacted = true;
        hideVisual(0);
        double cx = center.getX() + 0.5D;
        double cz = center.getZ() + 0.5D;

        // Columnas de la zona de destruccion (solo chunks ya cargados, solo dentro del radio)
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

        // Explosion SIN dano a bloques (solo dano/empuje a entidades): el terreno lo gestiona carve()
        level.explode(null, cx, baseY, cz, 14.0F, false, Level.ExplosionInteraction.NONE);
        Fx.burst(level, ParticleTypes.FLASH, cx, baseY + 2.0D, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, cx, baseY + 1.0D, cz, 8, radius * 0.4D, 2.0D, radius * 0.4D, 0.0D);
        Fx.sound(level, cx, baseY, cz, SoundEvents.GENERIC_EXPLODE, 60.0F, 0.3F);
        Fx.sound(level, cx, baseY, cz, SoundEvents.LIGHTNING_BOLT_THUNDER, 60.0F, 0.4F);
    }

    private void afterImpact() {
        impactAge++;
        double cx = center.getX() + 0.5D;
        double cz = center.getZ() + 0.5D;

        // Onda expansiva 3D (s3 = 2: explosion pesada, que tambien sacude la camara)
        if (impactAge <= SHOCK_TICKS) {
            float prog = Math.min(1.0F, impactAge / 70.0F);
            float fade = Math.max(0.0F, 1.0F - impactAge / (float) SHOCK_TICKS);
            showVisual(0, DisasterVisualEntity.Kind.SHOCKWAVE, cx, baseY, cz,
                    (float) (radius * 2.4D * prog), fade, 2.0F, 0.0D, 0.0D, 0.0D);
            blast(prog, cx, cz);
        } else {
            hideVisual(0);
        }

        if (grief) {
            carve();
        }

        // Hongo de humo y ceniza
        double grow = Math.min(1.0D, impactAge / 120.0D);
        double fadeOut = Math.min(1.0D, (POST_IMPACT_TICKS - impactAge) / 140.0D);
        double power = Math.max(0.02D, Math.min(grow, fadeOut));
        double height = 70.0D + 14.0D * intensity;
        showVisual(1, DisasterVisualEntity.Kind.ASH_PLUME, cx, baseY, cz,
                (float) height, (float) (radius * 0.5D), (float) power, 0.0D, 0.0D, 0.0D);
        showVisual(2, DisasterVisualEntity.Kind.STORM_CLOUD, cx, baseY + height * power + 4.0D, cz,
                (float) (radius * 1.6D * power), 12.0F, 0.1F, 0.0D, 0.0D, 0.0D);

        if (impactAge % 20 == 0 && impactAge < POST_IMPACT_TICKS - 100) {
            Fx.sound(level, cx, baseY, cz, SoundEvents.GENERIC_EXPLODE, 8.0F, 0.35F);
        }
        if (impactAge >= POST_IMPACT_TICKS) {
            finish();
        }
    }

    /** La onda empuja y quema a las entidades que toca el frente (no modifica bloques). */
    private void blast(float prog, double cx, double cz) {
        double maxR = radius * 2.4D;
        double shock = maxR * prog;
        AABB box = new AABB(cx - shock, baseY - shock, cz - shock, cx + shock, baseY + shock, cz + shock);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof DisasterVisualEntity || DisasterManager.isInTransit(e) || e.isSpectator()) {
                continue;
            }
            Vec3 d = new Vec3(e.getX() - cx, e.getY() - baseY, e.getZ() - cz);
            double dist = d.length();
            if (dist < 0.01D || dist > shock || Math.abs(dist - shock) > 10.0D) {
                continue;
            }
            Vec3 push = d.scale(1.0D / dist);
            e.setDeltaMovement(push.x * 2.2D, 0.9D + random.nextDouble() * 0.5D, push.z * 2.2D);
            e.hurtMarked = true;
            if (e instanceof LivingEntity living) {
                living.setSecondsOnFire(10);
                living.hurt(level.damageSources().magic(), (float) Math.max(4.0D, 70.0D * (1.0D - dist / maxR)));
            }
        }
    }

    /**
     * Excava el crater de dentro hacia fuera con un presupuesto de bloques por tick. Solo se tocan columnas con
     * dist <= radius y alturas entre el fondo del crater y 40 bloques sobre el suelo.
     */
    private void carve() {
        int budget = BLOCK_BUDGET_PER_TICK;
        int debris = 0;
        while (next < columns.size() && budget > 0) {
            Column c = columns.get(next++);
            double ext = Math.sqrt(Math.max(0.0D, (double) radius * radius - c.dist * c.dist));
            int bottom = baseY - (int) (ext * 0.6D);
            int top = c.ground + 40;
            for (int y = top; y >= bottom; y--) {
                BlockPos bp = new BlockPos(c.x, y, c.z);
                if (level.isOutsideBuildHeight(bp)) {
                    continue;
                }
                BlockState st = level.getBlockState(bp);
                if (st.isAir() || st.getDestroySpeed(level, bp) < 0.0F) {
                    continue;
                }
                budget--;
                // escombros: solo cerca del centro y con poca velocidad horizontal, para caer dentro del area
                if (debris < 12 && c.dist < radius * 0.5D && y >= c.ground - 2 && random.nextInt(80) == 0
                        && st.getFluidState().isEmpty() && !st.hasBlockEntity()) {
                    FallingBlockEntity fb = FallingBlockEntity.fall(level, bp, st);
                    fb.dropItem = false;
                    double a = random.nextDouble() * Math.PI * 2.0D;
                    fb.setDeltaMovement(Math.cos(a) * 0.3D, 1.0D + random.nextDouble() * 1.0D, Math.sin(a) * 0.3D);
                    fb.hurtMarked = true;
                    debris++;
                } else {
                    level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            // fondo del crater calcinado
            BlockPos floor = new BlockPos(c.x, bottom - 1, c.z);
            if (!level.isOutsideBuildHeight(floor)) {
                BlockState fs = level.getBlockState(floor);
                if (!fs.isAir() && fs.getDestroySpeed(level, floor) >= 0.0F && fs.getFluidState().isEmpty()) {
                    int r = random.nextInt(10);
                    if (r < 2) {
                        level.setBlock(floor, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else if (r < 4) {
                        level.setBlock(floor, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        if (impactAge % 4 == 0 && next < columns.size()) {
            Column c = columns.get(Math.min(next, columns.size() - 1));
            Fx.burst(level, ParticleTypes.LARGE_SMOKE, c.x + 0.5D, baseY + 2.0D, c.z + 0.5D, 6, 3.0D, 2.0D, 3.0D, 0.05D);
        }
    }

    @Override
    protected void onEnd() {
        // Las entidades visuales las elimina la base; no queda nada temporal que limpiar
        columns.clear();
    }
}
