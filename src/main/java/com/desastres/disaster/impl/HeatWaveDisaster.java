package com.desastres.disaster.impl;

import com.desastres.config.DisasterConfig;
import com.desastres.disaster.Disaster;
import com.desastres.disaster.Fx;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ola de calor: sobre una region aparece un domo anaranjado y un sol extremo (modelo 3D) y el ambiente se
 * llena de humo de calor. La exposicion se acumula POR ENTIDAD (cielo abierto, sin agua, sin resistencia al
 * fuego): primero hambre/debilidad, luego lentitud, luego dano de calor y por ultimo ignicion.
 * Opcionalmente prende fuego a bloques inflamables, con limites estrictos (config + doFireTick + sin lluvia,
 * solo cerca de jugadores, nunca pegado a ellos, maximo de fuegos por evento).
 */
public class HeatWaveDisaster extends Disaster {
    private final int duration;
    private final int ramp;
    private final double radius;
    private final int maxFires;
    private final Map<UUID, Integer> heat = new HashMap<>();
    private int fires = 0;

    public HeatWaveDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.duration = scaled(DisasterConfig.HEAT_WAVE_SECONDS.get() * 20);
        this.ramp = Math.max(40, duration / 4);
        this.radius = 60.0D + 20.0D * intensity;
        this.maxFires = 15 * intensity;
        Fx.sound(level, target.x, target.y, target.z, SoundEvents.FIRE_AMBIENT, 8.0F, 0.5F);
    }

    /** 0..1: sube progresivamente al empezar y baja al terminar. */
    private double progress() {
        return Math.max(0.0D, Math.min(Math.min(1.0D, age / (double) ramp), (duration - age) / 100.0D));
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        double prog = progress();
        int cx = Mth.floor(pos.x);
        int cz = Mth.floor(pos.z);
        if (loaded(cx, cz)) {
            showVisual(DisasterVisualEntity.Kind.HEAT_SKY, pos.x, surface(cx, cz), pos.z,
                    (float) radius, (float) prog, 0.0F, 0.0D, 0.0D, 0.0D);
        }
        heatParticles(prog);
        if (age % 20 == 0) {
            exposeEntities(prog);
        }
        if (age % 10 == 0) {
            tryIgnite(prog);
        }
        if (age % 200 == 0) {
            heat.keySet().removeIf(id -> level.getEntity(id) == null);
        }
        if (age % 100 == 0) {
            Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.FIRE_AMBIENT, 8.0F, 0.6F);
        }
    }

    private void heatParticles(double prog) {
        if (prog < 0.05D) {
            return;
        }
        double r2 = (radius * 1.2D) * (radius * 1.2D);
        int n = (int) (3 + 6 * prog);
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - pos.x;
            double dz = p.getZ() - pos.z;
            if (dx * dx + dz * dz > r2) {
                continue;
            }
            for (int i = 0; i < n; i++) {
                double x = p.getX() + (random.nextDouble() - 0.5D) * 32.0D;
                double z = p.getZ() + (random.nextDouble() - 0.5D) * 32.0D;
                double y = p.getY() + random.nextDouble() * 6.0D - 1.0D;
                // humo que sube = aire distorsionado por el calor
                Fx.dirTo(level, p, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0.0D, 0.05D + random.nextDouble() * 0.04D, 0.0D);
            }
            if (prog > 0.5D && age % 4 == 0) {
                Fx.dirTo(level, p, ParticleTypes.FLAME, p.getX() + (random.nextDouble() - 0.5D) * 20.0D, p.getY() + 0.5D,
                        p.getZ() + (random.nextDouble() - 0.5D) * 20.0D, 0.0D, 0.04D, 0.0D);
            }
        }
    }

    private void exposeEntities(double prog) {
        if (prog < 0.1D) {
            return;
        }
        AABB box = new AABB(pos.x - radius, level.getMinBuildHeight(), pos.z - radius,
                pos.x + radius, level.getMaxBuildHeight(), pos.z + radius);
        double r2 = radius * radius;
        int gain = intensity >= 3 ? 2 : 1;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (le.isSpectator() || (le instanceof Player p && p.isCreative())) {
                continue;
            }
            double dx = le.getX() - pos.x;
            double dz = le.getZ() - pos.z;
            if (dx * dx + dz * dz > r2) {
                continue;
            }
            UUID id = le.getUUID();
            boolean proof = le.fireImmune() || le.hasEffect(MobEffects.FIRE_RESISTANCE);
            boolean exposed = level.canSeeSky(le.blockPosition()) && !le.isInWaterOrRain();
            int h = heat.getOrDefault(id, 0);
            if (exposed && !proof) {
                h += gain;
            } else {
                h = Math.max(0, h - 3);
            }
            heat.put(id, h);
            if (proof) {
                continue;
            }
            // Efectos graduales segun la exposicion acumulada (todos de corta duracion: se desvanecen solos)
            if (h >= 4) {
                le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
                if (le instanceof Player) {
                    le.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 0, false, false, true));
                }
            }
            if (h >= 8) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
            }
            if (h >= 12) {
                le.hurt(level.damageSources().inFire(), 1.0F * intensity);
            }
            if (h >= 20) {
                le.setSecondsOnFire(3);
            }
        }
    }

    private void tryIgnite(double prog) {
        if (!grief || prog < 0.5D || fires >= maxFires || !DisasterConfig.HEAT_WAVE_FIRES.get()) {
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            return;
        }
        if (level.players().isEmpty()) {
            return;
        }
        for (int i = 0; i < 3 * intensity; i++) {
            ServerPlayer p = level.players().get(random.nextInt(level.players().size()));
            double pdx = p.getX() - pos.x;
            double pdz = p.getZ() - pos.z;
            if (pdx * pdx + pdz * pdz > radius * radius) {
                continue;
            }
            // cerca del jugador (para que se note) pero nunca pegado a el, y siempre dentro de la region
            double a = random.nextDouble() * Math.PI * 2.0D;
            double d = 8.0D + random.nextDouble() * 32.0D;
            int x = Mth.floor(p.getX() + Math.cos(a) * d);
            int z = Mth.floor(p.getZ() + Math.sin(a) * d);
            double ex = x - pos.x;
            double ez = z - pos.z;
            if (ex * ex + ez * ez > radius * radius || !loaded(x, z)) {
                continue;
            }
            BlockPos fp = new BlockPos(x, surface(x, z), z);
            if (level.isOutsideBuildHeight(fp) || level.isRainingAt(fp) || random.nextInt(100) >= 30) {
                continue;
            }
            BlockState here = level.getBlockState(fp);
            BlockState below = level.getBlockState(fp.below());
            boolean target = here.isAir()
                    ? below.isFlammable(level, fp.below(), Direction.UP)
                    : here.isFlammable(level, fp, Direction.UP);
            if (!target || !Blocks.FIRE.defaultBlockState().canSurvive(level, fp)) {
                continue;
            }
            if (!here.isAir() && !here.canBeReplaced()) {
                continue;
            }
            level.setBlock(fp, Blocks.FIRE.defaultBlockState(), 3);
            fires++;
            if (fires >= maxFires) {
                return;
            }
        }
    }

    @Override
    protected void onEnd() {
        heat.clear(); // los efectos aplicados duran unos segundos y se apagan solos; no queda nada permanente
    }
}
