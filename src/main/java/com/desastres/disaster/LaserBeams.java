package com.desastres.disaster;

import com.desastres.config.DisasterConfig;
import com.desastres.entity.AlienEntity;
import com.desastres.entity.DisasterVisualEntity;
import com.desastres.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Rayo de energia instantaneo (hitscan) con un haz 3D de corta vida. Lo comparten la pistola laser,
 * los alienigenas y el OVNI.
 */
public final class LaserBeams {
    public static final int GREEN = 0;
    public static final int BLUE = 1;
    public static final int MAGENTA = 2;

    private LaserBeams() {}

    private static Vector3f colorOf(int color) {
        if (color == BLUE) {
            return new Vector3f(0.30F, 0.65F, 1.0F);
        }
        if (color == MAGENTA) {
            return new Vector3f(1.0F, 0.30F, 0.80F);
        }
        return new Vector3f(0.30F, 1.0F, 0.45F);
    }

    /**
     * Dispara un rayo. Devuelve el punto final (donde choca o el alcance maximo).
     *
     * @param context        entidad usada para el trazado de colisiones (nunca null)
     * @param attacker       quien dispara (no se golpea a si mismo ni, si es un alienigena, a otros alienigenas)
     * @param terrainEffect  si es true, al chocar con un bloque provoca una pequena explosion (respeta blockDamage)
     */
    public static Vec3 fire(ServerLevel level, Entity context, @Nullable LivingEntity attacker, Vec3 from, Vec3 dir,
                            double range, float damage, int color, boolean terrainEffect, DamageSource source) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, context));
        boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
        Vec3 end = hitBlock ? blockHit.getLocation() : to;

        AABB box = new AABB(from, end).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, context, from, end, box,
                e -> e.isPickable() && !e.isSpectator() && e != attacker
                        && !(e instanceof DisasterVisualEntity)
                        && !(attacker instanceof AlienEntity && e instanceof AlienEntity));
        Entity victim = null;
        if (entityHit != null) {
            end = entityHit.getLocation();
            victim = entityHit.getEntity();
            hitBlock = false;
        }

        spawnBeam(level, from, end, color);

        Vector3f c = colorOf(color);
        DustParticleOptions dust = new DustParticleOptions(c, 1.3F);
        Fx.burst(level, dust, end.x, end.y, end.z, 10, 0.25D, 0.25D, 0.25D, 0.05D);
        Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 6, 0.2D, 0.2D, 0.2D, 0.25D);
        Fx.sound(level, end.x, end.y, end.z, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 2.0F);

        if (victim != null && damage > 0.0F) {
            victim.hurt(source, damage);
        } else if (hitBlock && terrainEffect) {
            Level.ExplosionInteraction mode = DisasterConfig.BLOCK_DAMAGE.get()
                    ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
            level.explode(null, end.x, end.y, end.z, 1.8F, false, mode);
        }
        return end;
    }

    /** Haz visual 3D de unos pocos ticks entre dos puntos. */
    public static void spawnBeam(ServerLevel level, Vec3 from, Vec3 end, int color) {
        Vec3 d = end.subtract(from);
        double len = d.length();
        if (len < 0.2D) {
            return;
        }
        DisasterVisualEntity beam = ModEntities.VISUAL.get().create(level);
        if (beam == null) {
            return;
        }
        Vec3 n = d.scale(1.0D / len);
        beam.setKind(DisasterVisualEntity.Kind.LASER_BEAM);
        beam.setPos(from.x, from.y, from.z);
        beam.setParams((float) len, color, 0.0F);
        beam.setDirection(n.x, n.y, n.z);
        beam.setLifetime(5);
        level.addFreshEntity(beam);
    }
}
