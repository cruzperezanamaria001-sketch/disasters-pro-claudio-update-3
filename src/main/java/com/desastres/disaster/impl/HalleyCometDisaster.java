package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cometa Halley: un cometa 3D (nucleo helado + coma + dos colas) cruza el cielo siguiendo una curva de Bezier
 * controlada y se estrella en el objetivo. El impacto abre un crater, genera hielo de forma controlada y
 * registra cada posicion en {@link CometIceData}, que lo convierte en agua real 20 s (400 ticks) despues.
 */
public class HalleyCometDisaster extends Disaster {
    private static final int IMPACT_ANIMATION_TICKS = 70;
    private static final int MAX_ICE_BLOCKS = 5000;

    private static final class Placed {
        private final long pos;
        private final byte kind;
        private final double dist;

        Placed(long pos, byte kind, double dist) {
            this.pos = pos;
            this.kind = kind;
            this.dist = dist;
        }

        long pos() {
            return pos;
        }

        byte kind() {
            return kind;
        }

        double dist() {
            return dist;
        }
    }

    private final Vec3 p0;
    private final Vec3 p1;
    private final Vec3 p2;
    private final int flightTicks;
    private final float nucleus;
    private final float tail;
    private final int craterRadius;
    private final int iceRadius;

    private Vec3 current;
    private Vec3 heading;
    private boolean impacted = false;
    private int impactAge = 0;
    private Vec3 impactPos;

    public HalleyCometDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        double ang = random.nextDouble() * Math.PI * 2.0D;
        double horiz = 170.0D;
        double maxY = level.getMaxBuildHeight() - 8;
        double startY = Math.min(maxY, target.y + 170.0D);
        this.p0 = new Vec3(target.x + Math.cos(ang) * horiz, startY, target.z + Math.sin(ang) * horiz);
        this.p2 = target;
        Vec3 mid = p0.add(p2).scale(0.5D);
        // Curva suave: primero cruza el cielo y luego cae sobre el objetivo
        this.p1 = new Vec3(mid.x, Math.min(maxY, mid.y + 55.0D), mid.z);
        this.flightTicks = scaled(220 + 40 * intensity);
        this.nucleus = 2.6F + 0.6F * intensity;
        this.tail = 70.0F + 20.0F * intensity;
        this.craterRadius = 5 + 3 * intensity;
        this.iceRadius = (int) (craterRadius * 1.9D);
        this.current = p0;
        this.heading = p2.subtract(p0).normalize();
        Fx.sound(level, target.x, target.y, target.z, SoundEvents.ENDER_DRAGON_GROWL, 10.0F, 1.4F);
    }

    @Override
    protected void tick() {
        if (impacted) {
            impactAnimation();
            return;
        }
        double t = Math.min(1.0D, (age + 1.0D) / flightTicks);
        double u = Math.pow(t, 1.15D);
        Vec3 next = bezier(u);
        Vec3 seg = next.subtract(current);
        int steps = Math.max(1, (int) Math.ceil(seg.length()));
        for (int i = 0; i < steps; i++) {
            Vec3 pt = current.add(seg.scale((i + 1.0D) / steps));
            if (hitsGround(pt)) {
                current = pt;
                impact();
                return;
            }
        }
        if (seg.lengthSqr() > 1.0E-6D) {
            heading = seg.normalize();
        }
        current = next;
        showVisual(DisasterVisualEntity.Kind.COMET, current.x, current.y, current.z,
                nucleus, tail, 0.0F, heading.x, heading.y, heading.z);

        // Chispas a lo largo de la cola (pocas: la cola 3D hace el trabajo)
        Vec3 back = heading.scale(-1.0D);
        for (int i = 0; i < 4; i++) {
            double d = random.nextDouble() * tail * 0.7D;
            Vec3 q = current.add(back.scale(d));
            Fx.burst(level, ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 1.2D, 1.2D, 1.2D, 0.02D);
        }
        if (age % 10 == 0 && current.distanceToSqr(p2) < 140.0D * 140.0D) {
            Fx.sound(level, current.x, current.y, current.z, SoundEvents.FIRECHARGE_USE, 6.0F, 0.35F);
        }
        if (t >= 1.0D) {
            impact();
        }
    }

    private Vec3 bezier(double u) {
        double a = (1.0D - u) * (1.0D - u);
        double b = 2.0D * (1.0D - u) * u;
        double c = u * u;
        return new Vec3(a * p0.x + b * p1.x + c * p2.x, a * p0.y + b * p1.y + c * p2.y, a * p0.z + b * p1.z + c * p2.z);
    }

    private boolean hitsGround(Vec3 pt) {
        if (pt.y <= level.getMinBuildHeight() + 1) {
            return true;
        }
        if (pt.y >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos bp = BlockPos.containing(pt);
        if (!loaded(bp)) {
            return false;
        }
        BlockState st = level.getBlockState(bp);
        return !st.getCollisionShape(level, bp).isEmpty() || !st.getFluidState().isEmpty();
    }

    // ------------------------------------------------------------------ impacto

    private void impact() {
        impacted = true;
        impactPos = current;
        BlockPos c = BlockPos.containing(impactPos);
        if (!loaded(c)) {
            finish();
            return;
        }
        hideVisual(0);
        double x = impactPos.x;
        double y = impactPos.y;
        double z = impactPos.z;

        // Explosion + luz + particulas
        Level.ExplosionInteraction mode = grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(null, x, y, z, 3.5F + 1.5F * intensity, false, mode);
        Fx.burst(level, ParticleTypes.FLASH, x, y + 1.0D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 3, craterRadius * 0.3D, 1.0D, craterRadius * 0.3D, 0.0D);
        Fx.burst(level, ParticleTypes.SNOWFLAKE, x, y + 1.0D, z, 120, craterRadius * 0.6D, 2.0D, craterRadius * 0.6D, 0.3D);
        Fx.burst(level, ParticleTypes.END_ROD, x, y + 1.0D, z, 60, craterRadius * 0.5D, 3.0D, craterRadius * 0.5D, 0.35D);
        Fx.sound(level, x, y, z, SoundEvents.GENERIC_EXPLODE, 30.0F, 0.6F);
        Fx.sound(level, x, y, z, SoundEvents.GLASS_BREAK, 30.0F, 0.5F);

        // Onda de impacto 3D azulada (s3 = 1 -> colores de hielo)
        showVisual(0, DisasterVisualEntity.Kind.SHOCKWAVE, x, y, z, 1.0F, 1.0F, 1.0F, 0.0D, 0.0D, 0.0D);

        // Dano y congelacion a los cercanos
        AABB box = new AABB(impactPos, impactPos).inflate(craterRadius * 2.0D);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            living.setTicksFrozen(living.getTicksRequiredToFreeze() + 60);
            Vec3 away = living.position().subtract(impactPos).normalize().scale(1.4D);
            living.setDeltaMovement(living.getDeltaMovement().add(away.x, 0.6D, away.z));
            living.hurtMarked = true;
        }

        if (grief) {
            int groundY = surface(c.getX(), c.getZ()) - 1;
            carveCrater(c, groundY);
            generateIce(c, groundY);
        }
    }

    private void carveCrater(BlockPos c, int groundY) {
        double depth = 3.0D + 1.5D * intensity;
        double rr = (double) craterRadius * craterRadius;
        for (int dx = -craterRadius; dx <= craterRadius; dx++) {
            for (int dz = -craterRadius; dz <= craterRadius; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 > rr) {
                    continue;
                }
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                if (!loaded(x, z)) {
                    continue;
                }
                int floorY = groundY - (int) (depth * (1.0D - d2 / rr));
                int top = Math.max(surface(x, z) - 1, groundY) + 6;
                for (int y = top; y > floorY; y--) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (level.isOutsideBuildHeight(bp)) {
                        continue;
                    }
                    BlockState st = level.getBlockState(bp);
                    if (!st.isAir() && st.getDestroySpeed(level, bp) >= 0.0F) {
                        level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /** Terreno natural: unico tipo de bloque que el impacto puede sustituir por hielo. */
    private static boolean isNaturalGround(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.SAND)
                || st.is(Blocks.GRAVEL) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.CLAY)
                || st.is(Blocks.SANDSTONE) || st.is(Blocks.RED_SANDSTONE);
    }

    private static boolean isIce(BlockState st) {
        return st.is(Blocks.ICE) || st.is(Blocks.PACKED_ICE) || st.is(Blocks.BLUE_ICE) || st.is(Blocks.FROSTED_ICE);
    }

    /**
     * Genera hielo de forma controlada: solo sustituye terreno natural (nunca construcciones) y solo coloca
     * puas de hielo sobre aire libre. Cada posicion se guarda para derretirla despues.
     */
    private void generateIce(BlockPos c, int groundY) {
        double depth = 3.0D + 1.5D * intensity;
        double rr = (double) craterRadius * craterRadius;
        List<Placed> placed = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        for (int dx = -iceRadius; dx <= iceRadius && placed.size() < MAX_ICE_BLOCKS; dx++) {
            for (int dz = -iceRadius; dz <= iceRadius && placed.size() < MAX_ICE_BLOCKS; dz++) {
                double d2 = dx * dx + dz * dz;
                double d = Math.sqrt(d2);
                if (d > iceRadius) {
                    continue;
                }
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                if (!loaded(x, z)) {
                    continue;
                }
                int topY = surface(x, z) - 1;
                if (d <= craterRadius) {
                    int bowlFloor = groundY - (int) (depth * (1.0D - d2 / rr));
                    topY = Math.min(topY, bowlFloor);
                }
                BlockPos top = new BlockPos(x, topY, z);
                if (level.isOutsideBuildHeight(top)) {
                    continue;
                }
                BlockState st = level.getBlockState(top);
                if (isIce(st) || !isNaturalGround(st)) {
                    continue; // no tocar hielo previo ni construcciones
                }
                double p = d <= craterRadius ? 1.0D : Math.max(0.0D, 1.0D - (d - craterRadius) / (iceRadius - craterRadius));
                if (random.nextDouble() > 0.05D + 0.9D * p) {
                    continue;
                }
                byte kind = random.nextInt(100) < 25 ? CometIceData.KIND_PACKED : CometIceData.KIND_ICE;
                level.setBlock(top, CometIceData.blockOf(kind).defaultBlockState(), Block.UPDATE_ALL);
                if (used.add(top.asLong())) {
                    placed.add(new Placed(top.asLong(), kind, d));
                }

                // Puas de hielo: solo sobre AIRE (no reemplazan plantas ni nada)
                if (d > craterRadius * 0.5D && random.nextInt(100) < 6) {
                    int h = 1 + random.nextInt(2 + intensity);
                    for (int k = 1; k <= h && placed.size() < MAX_ICE_BLOCKS; k++) {
                        BlockPos sp = top.above(k);
                        if (level.isOutsideBuildHeight(sp) || !level.getBlockState(sp).isAir()) {
                            break;
                        }
                        byte sk = (k == h && h > 1) ? CometIceData.KIND_BLUE : CometIceData.KIND_ICE;
                        level.setBlock(sp, CometIceData.blockOf(sk).defaultBlockState(), Block.UPDATE_ALL);
                        if (used.add(sp.asLong())) {
                            placed.add(new Placed(sp.asLong(), sk, d));
                        }
                    }
                }
            }
        }
        if (placed.isEmpty()) {
            return;
        }
        // De dentro hacia fuera: asi el derretimiento avanza desde el crater
        placed.sort(Comparator.comparingDouble(Placed::dist));
        long[] pos = new long[placed.size()];
        byte[] kinds = new byte[placed.size()];
        for (int i = 0; i < pos.length; i++) {
            pos[i] = placed.get(i).pos();
            kinds[i] = placed.get(i).kind();
        }
        // El conteo de 20 s empieza AHORA (tick de juego en el que se genero el hielo)
        CometIceData.get(level).addEvent(level.getGameTime(), pos, kinds);
    }

    private void impactAnimation() {
        impactAge++;
        double x = impactPos.x;
        double y = impactPos.y;
        double z = impactPos.z;
        float prog = Math.min(1.0F, impactAge / 40.0F);
        float fade = Math.max(0.0F, 1.0F - impactAge / (float) IMPACT_ANIMATION_TICKS);
        showVisual(0, DisasterVisualEntity.Kind.SHOCKWAVE, x, y, z,
                (float) (craterRadius * 2.2D * prog), fade, 1.0F, 0.0D, 0.0D, 0.0D);
        if (impactAge % 3 == 0) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = random.nextDouble() * craterRadius;
            Fx.burst(level, ParticleTypes.SNOWFLAKE, x + Math.cos(a) * r, y + 1.0D, z + Math.sin(a) * r, 10, 0.8D, 1.5D, 0.8D, 0.1D);
        }
        if (impactAge >= IMPACT_ANIMATION_TICKS) {
            finish();
        }
    }
}
