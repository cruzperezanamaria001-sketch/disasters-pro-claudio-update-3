package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Granizada: una nube 3D grande y bolas de hielo 3D que caen DENTRO de la region de precipitacion.
 *
 * Correccion del error de duplicados: antes habia dos generadores. Uno repartia las bolas por la zona de la
 * tormenta y otro (35 % de las veces) las creaba sobre CUALQUIER jugador del mundo, sin comprobar que
 * estuviera dentro de la tormenta. Ahora hay UN solo punto de generacion, que solo muestrea posiciones dentro
 * de la huella de la nube, con tope de bolas vivas y sin generar nada si no hay jugadores cerca.
 */
public class HailstormDisaster extends Disaster {
    /** Maximo de bolas de granizo vivas a la vez por tormenta (controla entidades y render). */
    private static final int MAX_ALIVE = 70;
    /** Si ningun jugador esta a menos de (radio + esta distancia) de la tormenta, no se genera granizo. */
    private static final double RENDER_MARGIN = 96.0D;

    private final int duration;
    private final double radius;
    /** Radio de la region de precipitacion: la huella real de la nube (sus puffs llegan a ~0.92 del radio). */
    private final double rainRadius;
    private double windAngle;
    private int alive = 0;

    public HailstormDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.duration = scaled(900 + 400 * intensity);
        this.radius = 26 + 12 * intensity;
        this.rainRadius = radius * 0.9D;
        this.windAngle = random.nextDouble() * Math.PI * 2.0D;
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        int cx = Mth.floor(pos.x);
        int cz = Mth.floor(pos.z);
        if (!loaded(cx, cz)) {
            return;
        }
        double cloudY = Math.min(level.getMaxBuildHeight() - 20, surface(cx, cz) + 55);
        showVisual(DisasterVisualEntity.Kind.STORM_CLOUD, pos.x, cloudY, pos.z,
                (float) radius, 14.0F, 0.75F, 0.0D, 0.0D, 0.0D);

        // Solo hay granizo si algun jugador esta lo bastante cerca como para verlo
        List<ServerPlayer> inside = new ArrayList<>();
        boolean anyNear = false;
        double nearSq = (radius + RENDER_MARGIN) * (radius + RENDER_MARGIN);
        double rainSq = rainRadius * rainRadius;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - pos.x;
            double dz = p.getZ() - pos.z;
            double d2 = dx * dx + dz * dz;
            if (d2 <= nearSq) {
                anyNear = true;
            }
            if (d2 <= rainSq) {
                inside.add(p);
            }
        }
        if (!anyNear) {
            return;
        }

        windAngle += (random.nextDouble() - 0.5D) * 0.05D;
        double wx = Math.cos(windAngle) * 0.4D;
        double wz = Math.sin(windAngle) * 0.4D;

        double fade = Math.min(1.0D, Math.min(age / 60.0D, (duration - age) / 60.0D));
        double rate = (0.7D + 0.8D * intensity) * fade;
        int count = (int) rate + (random.nextDouble() < rate - (int) rate ? 1 : 0);
        double startY = cloudY - 6.0D;

        for (int i = 0; i < count && alive < MAX_ALIVE; i++) {
            double x;
            double z;
            if (!inside.isEmpty() && random.nextInt(100) < 30) {
                // Un poco mas de granizo sobre los jugadores QUE ESTAN DENTRO de la tormenta,
                // siempre recortado a la huella de la nube.
                ServerPlayer p = inside.get(random.nextInt(inside.size()));
                x = p.getX() + (random.nextDouble() - 0.5D) * 20.0D;
                z = p.getZ() + (random.nextDouble() - 0.5D) * 20.0D;
                double dx = x - pos.x;
                double dz = z - pos.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rainRadius) {
                    double k = rainRadius / d;
                    x = pos.x + dx * k;
                    z = pos.z + dz * k;
                }
            } else {
                double r = rainRadius * Math.sqrt(random.nextDouble());
                double a = random.nextDouble() * Math.PI * 2.0D;
                x = pos.x + Math.cos(a) * r;
                z = pos.z + Math.sin(a) * r;
            }
            int ix = Mth.floor(x);
            int iz = Mth.floor(z);
            if (!loaded(ix, iz)) {
                continue;
            }
            // No nace granizo dentro del terreno (montanas que llegan a la nube)
            if (surface(ix, iz) >= startY - 3.0D) {
                continue;
            }
            Vec3 start = new Vec3(x, startY, z);
            Vec3 vel = new Vec3(wx, -(1.5D + random.nextDouble()), wz);
            float size = 0.25F + 0.05F * intensity + random.nextFloat() * 0.25F;
            alive++;
            DisasterManager.add(new LavaBombDisaster(level, start, vel, intensity, 0.0F, size, true)
                    .withOwnerCallback(this::onHailGone));
        }
    }

    private void onHailGone() {
        if (alive > 0) {
            alive--;
        }
    }
}
