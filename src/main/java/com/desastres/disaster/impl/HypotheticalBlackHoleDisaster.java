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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Agujero negro HIPOTETICO conectado a un agujero BLANCO (sistema independiente del agujero negro original).
 *
 * 1. Aparece el agujero negro hipotetico y atrae con una fuerza que depende de la distancia (con tope).
 * 2. Lo que cruza el horizonte se REGISTRA como absorbido (misma entidad, sin copias) y queda retenido
 *    dentro del nucleo, sin dano.
 * 3. Aparece el agujero blanco en una posicion segura y aleatoria (cargada, dentro del borde del mundo).
 * 4. El agujero blanco expulsa lo absorbido con un impulso. Las expulsadas quedan marcadas y no se reabsorben.
 * 5. Al terminar (o al detener el desastre) todo lo pendiente se libera de forma segura.
 *
 * Si no se encuentra un destino valido NO se absorbe nada (las entidades solo son atraidas y repelidas del
 * horizonte): ninguna entidad se elimina ni se pierde.
 */
public class HypotheticalBlackHoleDisaster extends Disaster {
    private static final int WHITE_SEARCH_START = 20;
    private static final int WHITE_SEARCH_END = 220;

    private static final class Captured {
        final Entity entity;
        final Vec3 origin;
        int releaseAt;
        int attempts;

        Captured(Entity entity, Vec3 origin, int releaseAt) {
            this.entity = entity;
            this.origin = origin;
            this.releaseAt = releaseAt;
        }
    }

    private final Vec3 hole;
    private final int duration;
    private final double coreRadius;
    private final double pullRadius;
    private final double absorbRadius;
    private final Map<UUID, Captured> captured = new HashMap<>();
    private final Set<UUID> ejected = new HashSet<>();

    private Vec3 white;
    private Vec3 whiteGround;
    private int whiteAge = -1;
    private boolean noWhiteHole = false;

    public HypotheticalBlackHoleDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.hole = target.add(0.0D, 4.0D + intensity, 0.0D);
        this.duration = scaled(1000 + 400 * intensity);
        this.coreRadius = 2.0D + 0.5D * intensity;
        this.pullRadius = 16.0D + 6.0D * intensity;
        this.absorbRadius = coreRadius * 1.1D;
        Fx.sound(level, hole.x, hole.y, hole.z, SoundEvents.ENDER_DRAGON_GROWL, 8.0F, 0.4F);
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        double scale = Math.max(0.05D, Math.min(1.0D, Math.min(age / 50.0D, (duration - age) / 50.0D)));
        showVisual(0, DisasterVisualEntity.Kind.HYP_BLACK_HOLE, hole.x, hole.y, hole.z,
                (float) (coreRadius * scale), 0.0F, 0.0F, 0.0D, 0.0D, 0.0D);

        if (white == null && !noWhiteHole && age >= WHITE_SEARCH_START) {
            searchWhiteHole();
        }
        if (white != null) {
            double ws = Math.min(1.0D, Math.min((age - whiteAge) / 30.0D, (duration - age) / 50.0D));
            showVisual(1, DisasterVisualEntity.Kind.WHITE_HOLE, white.x, white.y, white.z,
                    (float) (coreRadius * 0.9D * Math.max(0.05D, ws)), 0.0F, 0.0F, 0.0D, 0.0D, 0.0D);
        }

        attract(scale);
        holdCaptured();
        releaseDue();
        effects();
    }

    // ------------------------------------------------------------ agujero blanco

    private void searchWhiteHole() {
        Vec3 g = SafeSpot.findAnchor(level, hole.x, hole.z, 60.0D, 140.0D, 6, random);
        if (g != null) {
            whiteGround = g;
            white = new Vec3(g.x, g.y + 3.5D, g.z);
            whiteAge = age;
            Fx.sound(level, white.x, white.y, white.z, SoundEvents.ENDERMAN_TELEPORT, 10.0F, 1.5F);
            Fx.burst(level, ParticleTypes.END_ROD, white.x, white.y, white.z, 60, 2.0D, 2.0D, 2.0D, 0.3D);
        } else if (age >= WHITE_SEARCH_END) {
            noWhiteHole = true; // sin destino seguro: no se absorbe nada
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(hole) < 96.0D * 96.0D) {
                    p.displayClientMessage(Component.translatable("message.disasters.no_safe_destination"), true);
                }
            }
        }
    }

    private boolean canAbsorb() {
        return white != null && age >= whiteAge + 20 && age < duration - 100;
    }

    // ------------------------------------------------------------ atraccion y absorcion

    private void attract(double scale) {
        double pull = pullRadius * scale;
        double absorb = absorbRadius * scale;
        AABB box = new AABB(hole, hole).inflate(pull);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (!TransportUtil.eligible(e) || ejected.contains(e.getUUID())) {
                continue;
            }
            Vec3 center = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
            double dist = center.distanceTo(hole);
            if (dist > pull || dist < 1.0E-4D) {
                continue;
            }
            if (dist <= absorb) {
                if (canAbsorb()) {
                    capture(e);
                } else {
                    Vec3 away = center.subtract(hole).scale(0.5D / dist);
                    TransportUtil.kick(e, away.x, away.y + 0.1D, away.z);
                }
                continue;
            }
            double ratio = 1.0D - dist / pull;
            double force = Math.min(0.22D, 0.02D + 0.16D * ratio * ratio * (0.8D + 0.2D * intensity));
            if (e instanceof Player) {
                force *= 0.7D;
            }
            Vec3 d = hole.subtract(center).scale(force / dist);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.94D).add(d));
            e.hurtMarked = true;
        }
    }

    private void capture(Entity e) {
        UUID id = e.getUUID();
        if (captured.containsKey(id)) {
            return;
        }
        captured.put(id, new Captured(e, e.position(), age + 50 + random.nextInt(40)));
        DisasterManager.markTransit(e);
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(),
                25, 0.4D, 0.6D, 0.4D, 0.2D);
        Fx.sound(level, e.getX(), e.getY(), e.getZ(), SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.5F);
    }

    /** Lo absorbido permanece en el nucleo (oculto por la esfera negra) hasta que el agujero blanco lo expulse. */
    private void holdCaptured() {
        Iterator<Map.Entry<UUID, Captured>> it = captured.entrySet().iterator();
        while (it.hasNext()) {
            Captured c = it.next().getValue();
            Entity e = c.entity;
            if (e.isRemoved() || e.getCommandSenderWorld() != level) {
                DisasterManager.unmarkTransit(e);
                it.remove();
                continue;
            }
            Vec3 hold = new Vec3(hole.x, hole.y - e.getBbHeight() * 0.5D, hole.z);
            TransportUtil.teleport(level, e, hold);
            TransportUtil.kick(e, 0.0D, 0.0D, 0.0D);
        }
    }

    // ------------------------------------------------------------ expulsion

    private void releaseDue() {
        Iterator<Map.Entry<UUID, Captured>> it = captured.entrySet().iterator();
        while (it.hasNext()) {
            Captured c = it.next().getValue();
            if (age >= c.releaseAt && release(c, false)) {
                it.remove();
            }
        }
    }

    /**
     * Expulsa una entidad por el agujero blanco. Si no hay sitio seguro, reintenta; si sigue sin haberlo (o si
     * se esta cerrando el desastre) la devuelve a donde estaba al ser absorbida, que era una posicion valida.
     */
    private boolean release(Captured c, boolean force) {
        Entity e = c.entity;
        if (e.isRemoved()) {
            DisasterManager.unmarkTransit(e);
            return true;
        }
        Vec3 dest = white != null ? SafeSpot.findNear(level, e, whiteGround.x, whiteGround.z, 5, 20, random) : null;
        boolean viaWhite = dest != null;
        if (dest == null) {
            c.attempts++;
            if (!force && c.attempts < 3) {
                c.releaseAt = age + 10;
                return false;
            }
            dest = SafeSpot.findNear(level, e, c.origin.x, c.origin.z, 6, 30, random);
            if (dest == null) {
                dest = c.origin;
            }
        }
        TransportUtil.teleport(level, e, dest);
        DisasterManager.unmarkTransit(e);
        ejected.add(e.getUUID());
        if (viaWhite) {
            double ox = dest.x - white.x;
            double oz = dest.z - white.z;
            double len = Math.sqrt(ox * ox + oz * oz);
            if (len < 1.0E-3D) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                ox = Math.cos(a);
                oz = Math.sin(a);
                len = 1.0D;
            }
            TransportUtil.kick(e, ox / len * 0.7D, 0.45D, oz / len * 0.7D);
            Fx.burst(level, ParticleTypes.END_ROD, white.x, white.y, white.z, 30, 1.0D, 1.0D, 1.0D, 0.35D);
            Fx.burst(level, ParticleTypes.FLASH, white.x, white.y, white.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            Fx.sound(level, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, 2.0F, 1.5F);
        } else {
            TransportUtil.kick(e, 0.0D, 0.1D, 0.0D);
        }
        return true;
    }

    // ------------------------------------------------------------ efectos

    private void effects() {
        if (age % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double th = random.nextDouble() * Math.PI * 2.0D;
                double ph = Math.acos(2.0D * random.nextDouble() - 1.0D);
                double r = pullRadius * (0.3D + 0.7D * random.nextDouble());
                Fx.dir(level, ParticleTypes.PORTAL, hole.x, hole.y, hole.z,
                        Math.sin(ph) * Math.cos(th) * r, Math.cos(ph) * r, Math.sin(ph) * Math.sin(th) * r);
            }
            if (white != null) {
                Fx.burst(level, ParticleTypes.END_ROD, white.x, white.y, white.z, 3,
                        coreRadius * 0.6D, coreRadius * 0.6D, coreRadius * 0.6D, 0.12D);
            }
        }
        if (age % 40 == 0) {
            Fx.sound(level, hole.x, hole.y, hole.z, SoundEvents.PORTAL_AMBIENT, 8.0F, 0.4F);
            if (white != null) {
                Fx.sound(level, white.x, white.y, white.z, SoundEvents.BEACON_AMBIENT, 8.0F, 1.5F);
            }
        }
    }

    @Override
    protected void onEnd() {
        // Cierre de la conexion: nada pendiente se pierde
        for (Captured c : new ArrayList<>(captured.values())) {
            release(c, true);
        }
        captured.clear();
        ejected.clear();
    }
}
