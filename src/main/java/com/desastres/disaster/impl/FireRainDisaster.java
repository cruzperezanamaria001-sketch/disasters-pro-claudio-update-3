package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Lluvia de fuego: nubarron rojo y bolas de fuego/roca ardiente 3D que explotan al caer. */
public class FireRainDisaster extends Disaster {
    private final int duration;
    private final double radius;

    public FireRainDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.duration = scaled(700 + 300 * intensity);
        this.radius = 30 + 12 * intensity;
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
                (float) radius, 16.0F, 2.0F, 0.0D, 0.0D, 0.0D);

        if (age < 40 || age > duration - 40 || age % 4 != 0) {
            return;
        }
        int count = intensity + (intensity == 3 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double r = radius * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2.0D;
            double x = pos.x + Math.cos(a) * r;
            double z = pos.z + Math.sin(a) * r;
            if (!loaded(Mth.floor(x), Mth.floor(z))) {
                continue;
            }
            Vec3 start = new Vec3(x, cloudY - 4.0D, z);
            Vec3 vel = new Vec3((random.nextDouble() - 0.5D) * 0.5D, -(1.2D + random.nextDouble() * 0.9D),
                    (random.nextDouble() - 0.5D) * 0.5D);
            float size = 0.45F + random.nextFloat() * 0.35F;
            DisasterManager.add(new LavaBombDisaster(level, start, vel, intensity, 1.5F + 0.3F * intensity, size, false));
        }
    }
}
