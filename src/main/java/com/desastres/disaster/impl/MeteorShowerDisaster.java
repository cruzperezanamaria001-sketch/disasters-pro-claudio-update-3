package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.DisasterManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Lluvia de meteoritos sobre una zona. */
public class MeteorShowerDisaster extends Disaster {
    private final int duration;
    private final double radius;
    private final int interval;

    public MeteorShowerDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.duration = scaled(300 + 200 * intensity);
        this.radius = 30 + 15 * intensity;
        this.interval = Math.max(4, 14 - 3 * intensity);
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        if (age % interval != 0) {
            return;
        }
        double ang = random.nextDouble() * Math.PI * 2.0D;
        double r = radius * Math.sqrt(random.nextDouble());
        double x = pos.x + Math.cos(ang) * r;
        double z = pos.z + Math.sin(ang) * r;
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        if (!loaded(ix, iz)) {
            return;
        }
        double y = surface(ix, iz);
        DisasterManager.add(new MeteorDisaster(level, new Vec3(x, y, z), 1, 3.0F + 1.5F * intensity));
    }
}
