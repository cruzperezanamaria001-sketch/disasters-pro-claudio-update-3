package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Tormenta electrica: cielo oscuro y rayos constantes, con preferencia por jugadores y mobs. */
public class ThunderstormDisaster extends Disaster {
    private final int duration;
    private final double radius;

    public ThunderstormDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.duration = scaled(1200 + 600 * intensity);
        this.radius = 40 + 20 * intensity;
        level.setWeatherParameters(0, duration + 200, true, true);
    }

    @Override
    protected void tick() {
        if (age >= duration) {
            finish();
            return;
        }
        if (loaded(Mth.floor(pos.x), Mth.floor(pos.z))) {
            double cloudY = Math.min(level.getMaxBuildHeight() - 20, surface(Mth.floor(pos.x), Mth.floor(pos.z)) + 55);
            showVisual(DisasterVisualEntity.Kind.STORM_CLOUD, pos.x, cloudY, pos.z,
                    (float) radius, 18.0F, 0.0F, 0.0D, 0.0D, 0.0D);
        }
        int interval = Math.max(5, 30 - 8 * intensity);
        if (age % interval == 0) {
            strike();
        }
        if (intensity >= 3 && age % 40 == 0) {
            strike();
            strike();
        }
    }

    private void strike() {
        LivingEntity victim = null;
        if (random.nextInt(100) < 40) {
            AABB box = new AABB(pos.x - radius, level.getMinBuildHeight(), pos.z - radius,
                    pos.x + radius, level.getMaxBuildHeight(), pos.z + radius);
            List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isSpectator());
            if (!list.isEmpty()) {
                victim = list.get(random.nextInt(list.size()));
            }
        }
        double x;
        double z;
        if (victim != null) {
            x = victim.getX() + (random.nextDouble() - 0.5D) * 4.0D;
            z = victim.getZ() + (random.nextDouble() - 0.5D) * 4.0D;
        } else {
            double r = radius * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2.0D;
            x = pos.x + Math.cos(a) * r;
            z = pos.z + Math.sin(a) * r;
        }
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        if (!loaded(ix, iz)) {
            return;
        }
        int y = surface(ix, iz);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(ix + 0.5D, y, iz + 0.5D);
        bolt.setVisualOnly(!grief);
        level.addFreshEntity(bolt);
    }
}
