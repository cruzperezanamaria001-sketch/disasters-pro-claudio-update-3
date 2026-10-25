package com.desastres.disaster.impl;

import com.desastres.config.DisasterConfig;
import com.desastres.disaster.Disaster;
import com.desastres.disaster.Fx;
import com.desastres.disaster.LaserBeams;
import com.desastres.disaster.SafeSpot;
import com.desastres.disaster.TransportUtil;
import com.desastres.entity.AlienEntity;
import com.desastres.entity.DisasterVisualEntity;
import com.desastres.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invasion alienigena: un OVNI 3D recorre el mundo alternando fases de CRUCERO (se mueve) y de PARADA, en las
 * que enciende el haz de abduccion (sube a las entidades y las suelta arriba con caida lenta, para que sea
 * seguro), dispara rayos de energia dentro del radio configurable y baja alienigenas que pelean contra los
 * jugadores. Al terminar, los alienigenas del evento se retiran y no queda ningun efecto activo.
 */
public class UfoInvasionDisaster extends Disaster {
    private static final int CRUISE_TICKS = 160;
    private static final int CYCLE_TICKS = 420;
    private static final double ALTITUDE = 32.0D;

    private final int duration;
    private final double effectRadius;
    private final double ufoScale;
    private final double beamRadius;
    private final int maxAliens;
    private final int rayInterval;
    private final Map<UUID, Integer> cooldown = new HashMap<>();
    private final List<UUID> aliens = new ArrayList<>();

    private Vec3 ufo;
    private double heading;
    private double smoothY = Double.NaN;
    private boolean hovering = false;

    public UfoInvasionDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.ufo = target.add(0.0D, ALTITUDE, 0.0D);
        this.heading = random.nextDouble() * Math.PI * 2.0D;
        this.duration = scaled(1800 + 600 * intensity);
        this.effectRadius = DisasterConfig.UFO_EFFECT_RADIUS.get() * (0.8D + 0.1D * intensity);
        this.ufoScale = 5.0D + 0.5D * intensity;
        this.beamRadius = 3.0D + ufoScale * 0.7D;
        this.maxAliens = 2 + 2 * intensity;
        this.rayInterval = Math.max(14, 44 - 8 * intensity);
        Fx.sound(level, ufo.x, ufo.y, ufo.z, SoundEvents.BEACON_ACTIVATE, 12.0F, 0.5F);
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        int cyc = age % CYCLE_TICKS;
        hovering = cyc >= CRUISE_TICKS;
        int hoverAge = cyc - CRUISE_TICKS;

        move();

        double scale = Math.max(0.05D, Math.min(1.0D, Math.min(age / 40.0D, (duration - age) / 40.0D)));
        double groundY = ground();
        float beamLen = hovering ? (float) Math.max(0.0D, ufo.y - groundY - 1.0D) : 0.0F;
        showVisual(0, DisasterVisualEntity.Kind.UFO, ufo.x, ufo.y, ufo.z,
                (float) (ufoScale * scale), beamLen, hovering ? (float) (beamRadius * scale) : 0.0F, 0.0D, 0.0D, 0.0D);

        if (hovering) {
            abduct(groundY);
            if (hoverAge % rayInterval == 0) {
                shoot();
            }
            if (hoverAge % 120 == 20) {
                spawnAlien();
            }
        }
        if (age % 40 == 0) {
            Fx.sound(level, ufo.x, ufo.y, ufo.z, SoundEvents.BEACON_AMBIENT, 8.0F, 0.6F);
        }
        if (age % 3 == 0) {
            Fx.burst(level, ParticleTypes.END_ROD, ufo.x, ufo.y - ufoScale * 0.25D, ufo.z, 2, ufoScale * 0.6D, 0.2D, ufoScale * 0.6D, 0.03D);
        }
    }

    private double ground() {
        int ix = Mth.floor(ufo.x);
        int iz = Mth.floor(ufo.z);
        return loaded(ix, iz) ? surface(ix, iz) : ufo.y - ALTITUDE;
    }

    private void move() {
        if (!hovering) {
            heading += (random.nextDouble() - 0.5D) * 0.08D;
            double speed = 0.45D + 0.1D * intensity;
            double nx = ufo.x + Math.cos(heading) * speed;
            double nz = ufo.z + Math.sin(heading) * speed;
            if (!loaded(Mth.floor(nx), Mth.floor(nz))) {
                heading += Math.PI; // no abandona la zona cargada
                return;
            }
            double gy = surface(Mth.floor(nx), Mth.floor(nz));
            smoothY = Double.isNaN(smoothY) ? gy : smoothY + (gy - smoothY) * 0.08D;
            ufo = new Vec3(nx, smoothY + ALTITUDE, nz);
        } else if (!Double.isNaN(smoothY)) {
            ufo = new Vec3(ufo.x, smoothY + ALTITUDE + Math.sin(age * 0.1D) * 0.4D, ufo.z);
        }
    }

    // ------------------------------------------------------------------ abduccion

    private void abduct(double groundY) {
        AABB box = new AABB(ufo.x - beamRadius, groundY - 2.0D, ufo.z - beamRadius,
                ufo.x + beamRadius, ufo.y, ufo.z + beamRadius);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (!TransportUtil.eligible(e) || e instanceof AlienEntity) {
                continue;
            }
            UUID id = e.getUUID();
            Integer cd = cooldown.get(id);
            if (cd != null && cd > age) {
                continue;
            }
            double dx = e.getX() - ufo.x;
            double dz = e.getZ() - ufo.z;
            if (dx * dx + dz * dz > beamRadius * beamRadius) {
                continue;
            }
            if (e.getY() > ufo.y - 3.0D) {
                // Llego arriba: se le suelta con caida lenta (nunca muere por la altura)
                double a = random.nextDouble() * Math.PI * 2.0D;
                TransportUtil.kick(e, Math.cos(a) * 0.6D, 0.3D, Math.sin(a) * 0.6D);
                if (e instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false, true));
                }
                cooldown.put(id, age + 400);
                Fx.burst(level, ParticleTypes.END_ROD, e.getX(), e.getY() + 1.0D, e.getZ(), 15, 0.4D, 0.6D, 0.4D, 0.15D);
                continue;
            }
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.8D - dx * 0.03D, Math.min(v.y + 0.12D, 0.35D), v.z * 0.8D - dz * 0.03D);
            e.hurtMarked = true;
            e.resetFallDistance();
        }
        if (age % 100 == 0) {
            cooldown.values().removeIf(t -> t <= age);
        }
    }

    // ------------------------------------------------------------------ rayos

    private void shoot() {
        DisasterVisualEntity context = getVisual(0);
        if (context == null) {
            return;
        }
        Vec3 from = ufo.add(0.0D, -ufoScale * 0.3D, 0.0D);
        Vec3 target = pickTarget();
        if (target == null) {
            return;
        }
        Vec3 dir = target.subtract(from).normalize();
        double range = from.distanceTo(target) + 8.0D;
        float damage = DisasterConfig.UFO_RAY_DAMAGE.get().floatValue();
        LaserBeams.fire(level, context, null, from, dir, range, damage, LaserBeams.MAGENTA, true,
                level.damageSources().magic());
        Fx.sound(level, from.x, from.y, from.z, SoundEvents.BEACON_POWER_SELECT, 4.0F, 0.6F);
    }

    /** 60 % una criatura o jugador dentro del radio; si no, un punto del terreno dentro del radio. */
    private Vec3 pickTarget() {
        if (random.nextInt(100) < 60) {
            AABB box = new AABB(ufo.x - effectRadius, level.getMinBuildHeight(), ufo.z - effectRadius,
                    ufo.x + effectRadius, ufo.y, ufo.z + effectRadius);
            List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box, le ->
                    !(le instanceof AlienEntity) && !le.isSpectator() && !(le instanceof Player p && p.isCreative()));
            if (!list.isEmpty()) {
                LivingEntity le = list.get(random.nextInt(list.size()));
                return le.getBoundingBox().getCenter();
            }
        }
        double a = random.nextDouble() * Math.PI * 2.0D;
        double r = effectRadius * Math.sqrt(random.nextDouble());
        int x = Mth.floor(ufo.x + Math.cos(a) * r);
        int z = Mth.floor(ufo.z + Math.sin(a) * r);
        if (!loaded(x, z)) {
            return null;
        }
        return new Vec3(x + 0.5D, surface(x, z), z + 0.5D);
    }

    // ------------------------------------------------------------------ alienigenas

    private void spawnAlien() {
        aliens.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        if (aliens.size() >= maxAliens) {
            return;
        }
        AlienEntity alien = ModEntities.ALIEN.get().create(level);
        if (alien == null) {
            return;
        }
        Vec3 g = SafeSpot.findNear(level, alien, ufo.x, ufo.z, 8, 20, random);
        if (g == null) {
            return;
        }
        alien.moveTo(g.x, g.y, g.z, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(alien);
        aliens.add(alien.getUUID());
        LaserBeams.spawnBeam(level, ufo.add(0.0D, -ufoScale * 0.3D, 0.0D), g.add(0.0D, 1.0D, 0.0D), LaserBeams.GREEN);
        Fx.burst(level, ParticleTypes.END_ROD, g.x, g.y + 1.0D, g.z, 30, 0.4D, 1.0D, 0.4D, 0.2D);
        Fx.sound(level, g.x, g.y, g.z, SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.8F);
    }

    @Override
    protected void onEnd() {
        // Los alienigenas del evento se retiran (sin dejar entidades huerfanas)
        for (UUID id : aliens) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) {
                Fx.burst(level, ParticleTypes.END_ROD, e.getX(), e.getY() + 1.0D, e.getZ(), 20, 0.4D, 0.8D, 0.4D, 0.2D);
                e.discard();
            }
        }
        aliens.clear();
        cooldown.clear();
    }
}
