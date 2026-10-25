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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ola de congelacion: un frente azul y blanco (modelo 3D) se expande desde el centro. Lo que toca:
 *  - mobs y jugadores: congelacion + lentitud muy fuerte, UNA vez por entidad, con duracion configurable;
 *  - terreno natural y superficie de agua: hielo TEMPORAL registrado en {@link FrozenTerrainData}, que
 *    restaura el bloque original solo si sigue siendo el hielo del evento.
 */
public class FreezeWaveDisaster extends Disaster {
    private static final double SPEED = 0.9D;
    private static final int COLUMNS_PER_TICK = 2500;
    private static final int FLUSH_EVERY = 6;
    private static final int FADE_TICKS = 60;

    private static final class Column {
        final int x;
        final int z;
        final double dist;

        Column(int x, int z, double dist) {
            this.x = x;
            this.z = z;
            this.dist = dist;
        }
    }

    private final BlockPos center;
    private final int radius;
    private final int baseY;
    private final long restoreDelay;
    private final int freezeTicks;
    private final List<Column> columns = new ArrayList<>();
    private final Set<UUID> affected = new HashSet<>();

    // lote pendiente de registrar
    private final List<long[]> batchPos = new ArrayList<>();
    private final List<Byte> batchKind = new ArrayList<>();
    private final List<Byte> batchOrig = new ArrayList<>();
    private final Map<BlockState, Integer> palette = new LinkedHashMap<>();

    private int next = 0;
    private double front = 0.0D;
    private int fadeAge = 0;

    public FreezeWaveDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.center = BlockPos.containing(target);
        this.radius = 45 + 20 * intensity;
        this.baseY = surface(center.getX(), center.getZ());
        this.restoreDelay = Math.max(100, (long) DisasterConfig.FREEZE_TERRAIN_SECONDS.get() * 20L);
        this.freezeTicks = DisasterConfig.FREEZE_EFFECT_SECONDS.get() * 20;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= radius && loaded(center.getX() + dx, center.getZ() + dz)) {
                    columns.add(new Column(center.getX() + dx, center.getZ() + dz, d));
                }
            }
        }
        columns.sort(Comparator.comparingDouble(c -> c.dist));
        Fx.sound(level, target.x, target.y, target.z, SoundEvents.GLASS_BREAK, 12.0F, 0.4F);
    }

    @Override
    protected void tick() {
        double cx = center.getX() + 0.5D;
        double cz = center.getZ() + 0.5D;

        if (front < radius) {
            front = Math.min(radius, front + SPEED);
        } else {
            // El frente llego al borde: se desvanece y termina
            fadeAge++;
            if (fadeAge >= FADE_TICKS) {
                finish();
                return;
            }
        }
        float fade = front < radius ? 1.0F : Math.max(0.0F, 1.0F - fadeAge / (float) FADE_TICKS);
        showVisual(DisasterVisualEntity.Kind.SHOCKWAVE, cx, baseY, cz, (float) front, fade, 3.0F, 0.0D, 0.0D, 0.0D);

        if (grief) {
            freezeTerrain();
        }
        freezeEntities(cx, cz);
        frontParticles(cx, cz);
        if (age % FLUSH_EVERY == 0) {
            flush();
        }
        if (age % 10 == 0) {
            Fx.sound(level, cx + Math.cos(age) * front, baseY, cz + Math.sin(age) * front, SoundEvents.PLAYER_HURT_FREEZE, 5.0F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ entidades

    private void freezeEntities(double cx, double cz) {
        AABB box = new AABB(cx - front, level.getMinBuildHeight(), cz - front, cx + front, level.getMaxBuildHeight(), cz + front);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (le.isSpectator() || (le instanceof Player p && p.isCreative())
                    || le.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)
                    || DisasterManager.isInTransit(le)) {
                continue;
            }
            double dx = le.getX() - cx;
            double dz = le.getZ() - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > front || !affected.add(le.getUUID())) {
                continue; // fuera del frente, o ya congelado por esta ola
            }
            boolean immuneToFrost = !le.canFreeze();
            int ticks = immuneToFrost ? freezeTicks / 2 : freezeTicks;
            if (!immuneToFrost) {
                // capa de escarcha de vanilla (decae sola 2 por tick)
                le.setTicksFrozen(le.getTicksRequiredToFreeze() + freezeTicks * 2);
            }
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, true, true));
            if (le instanceof Player) {
                le.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 2, false, true, true));
            }
            Fx.burst(level, ParticleTypes.SNOWFLAKE, le.getX(), le.getY() + le.getBbHeight() * 0.5D, le.getZ(),
                    25, 0.4D, 0.7D, 0.4D, 0.08D);
            Fx.sound(level, le.getX(), le.getY(), le.getZ(), SoundEvents.GLASS_BREAK, 1.0F, 0.7F);
        }
    }

    // ------------------------------------------------------------------ terreno

    private static boolean isNaturalGround(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.SAND)
                || st.is(Blocks.GRAVEL) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.CLAY)
                || st.is(Blocks.SANDSTONE) || st.is(Blocks.RED_SANDSTONE);
    }

    private void freezeTerrain() {
        int done = 0;
        while (next < columns.size() && columns.get(next).dist <= front && done < COLUMNS_PER_TICK) {
            Column c = columns.get(next++);
            done++;
            if (random.nextInt(100) >= 85) {
                continue;
            }
            int y = surface(c.x, c.z) - 1;
            BlockPos bp = new BlockPos(c.x, y, c.z);
            if (level.isOutsideBuildHeight(bp)) {
                continue;
            }
            BlockState st = level.getBlockState(bp);
            byte kind;
            if (st.is(Blocks.WATER) && st.getFluidState().isSource()) {
                kind = FrozenTerrainData.KIND_ICE;
            } else if (isNaturalGround(st)) {
                kind = FrozenTerrainData.KIND_PACKED;
            } else {
                continue; // no se toca nada mas (construcciones, minerales, hielo ya existente...)
            }
            Integer idx = palette.get(st);
            if (idx == null) {
                if (palette.size() >= 120) {
                    continue;
                }
                idx = palette.size();
                palette.put(st, idx);
            }
            level.setBlock(bp, FrozenTerrainData.blockOf(kind).defaultBlockState(), Block.UPDATE_CLIENTS);
            batchPos.add(new long[]{bp.asLong()});
            batchKind.add(kind);
            batchOrig.add(idx.byteValue());
        }
    }

    /** Registra lo colocado hasta ahora; el temporizador de restauracion empieza en este tick de juego. */
    private void flush() {
        if (batchPos.isEmpty()) {
            return;
        }
        long[] pos = new long[batchPos.size()];
        byte[] kinds = new byte[pos.length];
        byte[] orig = new byte[pos.length];
        for (int i = 0; i < pos.length; i++) {
            pos[i] = batchPos.get(i)[0];
            kinds[i] = batchKind.get(i);
            orig[i] = batchOrig.get(i);
        }
        List<BlockState> pal = new ArrayList<>(palette.keySet());
        FrozenTerrainData.get(level).addEvent(level.getGameTime() + restoreDelay, pos, kinds, orig, pal);
        batchPos.clear();
        batchKind.clear();
        batchOrig.clear();
    }

    private void frontParticles(double cx, double cz) {
        if (front < 2.0D) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double x = cx + Math.cos(a) * front;
            double z = cz + Math.sin(a) * front;
            int ix = Mth.floor(x);
            int iz = Mth.floor(z);
            if (!loaded(ix, iz)) {
                continue;
            }
            double y = surface(ix, iz) + 0.5D;
            if (i % 3 == 0) {
                Fx.burst(level, ParticleTypes.SNOWFLAKE, x, y + 1.0D, z, 4, 1.0D, 1.5D, 1.0D, 0.08D);
            } else if (i % 3 == 1) {
                Fx.dir(level, ParticleTypes.CLOUD, x, y, z, Math.cos(a) * 0.25D, 0.05D, Math.sin(a) * 0.25D);
            } else {
                Fx.burst(level, ParticleTypes.END_ROD, x, y + 0.5D, z, 1, 0.6D, 0.8D, 0.6D, 0.05D);
            }
        }
    }

    @Override
    protected void onEnd() {
        // Lo que quede por registrar se registra ahora: asi todo el hielo colocado se restaura siempre
        flush();
        affected.clear();
        columns.clear();
    }
}
