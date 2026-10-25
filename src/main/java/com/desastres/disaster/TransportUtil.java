package com.desastres.disaster;

import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/** Reglas comunes de transporte para el agujero de gusano y el agujero negro/blanco. */
public final class TransportUtil {

    private TransportUtil() {}

    /** Entidades que pueden ser transportadas con seguridad (jugadores, criaturas, objetos y orbes de xp). */
    public static boolean eligible(Entity e) {
        if (e instanceof DisasterVisualEntity || DisasterManager.isInTransit(e)) {
            return false;
        }
        if (e.isRemoved() || !e.isAlive() || e.isSpectator() || e.isPassenger() || e.isVehicle()) {
            return false;
        }
        if (e instanceof EnderDragon || e instanceof PartEntity<?>) {
            return false;
        }
        return e instanceof LivingEntity || e instanceof ItemEntity || e instanceof ExperienceOrb;
    }

    /** Mueve la MISMA entidad (sin copiarla) al destino, conservando su estado, y reinicia la caida. */
    public static void teleport(ServerLevel level, Entity e, Vec3 dest) {
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(level, dest.x, dest.y, dest.z, sp.getYRot(), sp.getXRot());
        } else {
            e.moveTo(dest.x, dest.y, dest.z, e.getYRot(), e.getXRot());
            if (e instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        }
        e.resetFallDistance();
    }

    /** Impulso de salida (no se aplica a nada que ya no exista). */
    public static void kick(Entity e, double vx, double vy, double vz) {
        if (e.isRemoved()) {
            return;
        }
        e.setDeltaMovement(vx, vy, vz);
        e.hurtMarked = true;
    }
}
