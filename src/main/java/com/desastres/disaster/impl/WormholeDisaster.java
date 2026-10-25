package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import com.desastres.disaster.Fx;
import com.desastres.disaster.SafeSpot;
import com.desastres.disaster.TransportUtil;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Agujero de gusano: una entrada (azul-violeta, absorbe) y una salida (naranja-dorada, expulsa) unidas por un
 * tunel 3D. La salida se busca a ~100 bloques con una posicion segura. Las entidades absorbidas son las
 * MISMAS (no se copian ni se eliminan): se mueven al destino tras una breve animacion. Si no hay destino
 * seguro, la entidad no se toca.
 */
public class WormholeDisaster extends Disaster {
    private static final int TRANSIT_TICKS = 10;
    private static final int COOLDOWN_TICKS = 100;
    private static final int EXIT_SEARCH_LIMIT = 60;

    private static final class Transit {
        final Entity entity;
        final Vec3 dest;
        final int arriveAt;

        Transit(Entity entity, Vec3 dest, int arriveAt) {
            this.entity = entity;
            this.dest = dest;
            this.arriveAt = arriveAt;
        }
    }

    private final Vec3 entrance;
    private final int duration;
    private final double visRadius;
    private final double pullRadius;
    private final double absorbRadius;
    private final Map<UUID, Transit> transits = new HashMap<>();
    private final Map<UUID, Integer> cooldown = new HashMap<>();

    private Vec3 exitGround;
    private Vec3 exit;

    public WormholeDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.entrance = target.add(0.0D, 2.5D, 0.0D);
        this.duration = scaled(600 + 300 * intensity);
        this.visRadius = 3.0D + 0.7D * intensity;
        this.pullRadius = 9.0D + 3.0D * intensity;
        this.absorbRadius = visRadius * 0.8D;
        Fx.sound(level, entrance.x, entrance.y, entrance.z, SoundEvents.ENDERMAN_TELEPORT, 8.0F, 0.4F);
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        double scale = Math.max(0.05D, Math.min(1.0D, Math.min(age / 40.0D, (duration - age) / 40.0D)));
        showVisual(0, DisasterVisualEntity.Kind.WORMHOLE, entrance.x, entrance.y, entrance.z,
                (float) (visRadius * scale), 0.0F, 0.0F, 0.0D, 0.0D, 0.0D);

        if (exit == null) {
            Vec3 ground = SafeSpot.findAnchor(level, entrance.x, entrance.z, 85.0D, 115.0D, 6, random);
            if (ground != null) {
                exitGround = ground;
                exit = new Vec3(ground.x, ground.y + 2.5D, ground.z);
                Fx.sound(level, exit.x, exit.y, exit.z, SoundEvents.ENDERMAN_TELEPORT, 8.0F, 1.4F);
            } else if (age >= EXIT_SEARCH_LIMIT) {
                announceNoDestination();
                finish();
            }
            return;
        }
        showVisual(1, DisasterVisualEntity.Kind.WORMHOLE, exit.x, exit.y, exit.z,
                (float) (visRadius * scale), 0.0F, 1.0F, 0.0D, 0.0D, 0.0D);

        scanEntities(scale);
        processTransits();
        effects();
        if (age % 100 == 0) {
            int now = age;
            cooldown.values().removeIf(t -> t <= now);
        }
    }

    private void announceNoDestination() {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(entrance) < 80.0D * 80.0D) {
                p.displayClientMessage(Component.translatable("message.disasters.no_safe_destination"), true);
            }
        }
    }

    private void scanEntities(double scale) {
        AABB box = new AABB(entrance, entrance).inflate(pullRadius);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (!TransportUtil.eligible(e)) {
                continue;
            }
            UUID id = e.getUUID();
            Integer cd = cooldown.get(id);
            if (cd != null && cd > age) {
                continue;
            }
            Vec3 center = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
            double dist = center.distanceTo(entrance);
            if (dist <= absorbRadius * scale) {
                beginTransit(e);
            } else if (dist <= pullRadius) {
                double ratio = 1.0D - dist / pullRadius;
                double f = 0.02D + 0.10D * ratio * ratio;
                Vec3 pull = entrance.subtract(center).scale(f / Math.max(dist, 1.0E-4D));
                e.setDeltaMovement(e.getDeltaMovement().scale(0.95D).add(pull));
                e.hurtMarked = true;
            }
        }
    }

    private void beginTransit(Entity e) {
        UUID id = e.getUUID();
        Vec3 dest = SafeSpot.findNear(level, e, exitGround.x, exitGround.z, 5, 20, random);
        if (dest == null) {
            // Sin destino seguro: la entidad no se toca. Se reintenta mas tarde.
            cooldown.put(id, age + 200);
            return;
        }
        transits.put(id, new Transit(e, dest, age + TRANSIT_TICKS));
        DisasterManager.markTransit(e);
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(),
                20, 0.4D, 0.6D, 0.4D, 0.15D);
        Fx.sound(level, e.getX(), e.getY(), e.getZ(), SoundEvents.ENDERMAN_TELEPORT, 1.5F, 0.6F);
    }

    private void processTransits() {
        Iterator<Map.Entry<UUID, Transit>> it = transits.entrySet().iterator();
        while (it.hasNext()) {
            Transit t = it.next().getValue();
            Entity e = t.entity;
            if (e.isRemoved() || e.getCommandSenderWorld() != level) {
                DisasterManager.unmarkTransit(e);
                it.remove();
                continue;
            }
            if (age < t.arriveAt) {
                // Animacion de absorcion: la entidad es arrastrada al centro del tunel
                Vec3 center = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
                TransportUtil.kick(e, (entrance.x - center.x) * 0.25D, (entrance.y - center.y) * 0.25D, (entrance.z - center.z) * 0.25D);
                Fx.burst(level, ParticleTypes.PORTAL, center.x, center.y, center.z, 4, 0.3D, 0.3D, 0.3D, 0.4D);
                continue;
            }
            complete(t);
            it.remove();
        }
    }

    private void complete(Transit t) {
        Entity e = t.entity;
        DisasterManager.unmarkTransit(e);
        if (e.isRemoved()) {
            return;
        }
        Vec3 dest = t.dest;
        if (!SafeSpot.isSafe(level, e, dest.x, dest.y, dest.z)) {
            dest = SafeSpot.findNear(level, e, exitGround.x, exitGround.z, 6, 24, random);
        }
        if (dest == null) {
            cooldown.put(e.getUUID(), age + 200); // se queda donde esta, sin perderse
            return;
        }
        TransportUtil.teleport(level, e, dest);
        double a = random.nextDouble() * Math.PI * 2.0D;
        TransportUtil.kick(e, Math.cos(a) * 0.25D, 0.2D, Math.sin(a) * 0.25D);
        cooldown.put(e.getUUID(), age + COOLDOWN_TICKS);
        Fx.burst(level, ParticleTypes.END_ROD, dest.x, dest.y + 1.0D, dest.z, 25, 0.5D, 0.8D, 0.5D, 0.2D);
        Fx.sound(level, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, 1.5F, 1.3F);
    }

    private void effects() {
        if (age % 2 == 0) {
            // materia cayendo hacia la entrada (las particulas PORTAL convergen al punto de aparicion)
            for (int i = 0; i < 3; i++) {
                double th = random.nextDouble() * Math.PI * 2.0D;
                double ph = Math.acos(2.0D * random.nextDouble() - 1.0D);
                double r = pullRadius * (0.3D + 0.7D * random.nextDouble());
                Fx.dir(level, ParticleTypes.PORTAL, entrance.x, entrance.y, entrance.z,
                        Math.sin(ph) * Math.cos(th) * r, Math.cos(ph) * r, Math.sin(ph) * Math.sin(th) * r);
            }
            Fx.burst(level, ParticleTypes.END_ROD, exit.x, exit.y, exit.z, 2, visRadius * 0.5D, visRadius * 0.5D, visRadius * 0.5D, 0.08D);
        }
        if (age % 40 == 0) {
            Fx.sound(level, entrance.x, entrance.y, entrance.z, SoundEvents.PORTAL_AMBIENT, 6.0F, 0.6F);
            Fx.sound(level, exit.x, exit.y, exit.z, SoundEvents.PORTAL_AMBIENT, 6.0F, 1.2F);
        }
    }

    @Override
    protected void onEnd() {
        // Nada se pierde: lo que estaba a medio camino se completa (o se queda donde esta)
        for (Transit t : new ArrayList<>(transits.values())) {
            if (exit != null) {
                complete(t);
            } else {
                DisasterManager.unmarkTransit(t.entity);
            }
        }
        transits.clear();
        cooldown.clear();
    }
}
