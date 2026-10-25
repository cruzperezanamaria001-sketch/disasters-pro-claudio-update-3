package com.desastres.disaster;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Utilidades de particulas y sonido con alcance largo (los desastres son enormes). */
public final class Fx {
    private static final double RANGE_SQ = 160.0D * 160.0D;

    private Fx() {}

    private static void send(ServerPlayer player, ParticleOptions type, double x, double y, double z,
                             float dx, float dy, float dz, float speed, int count) {
        if (player.connection == null) {
            return;
        }
        // longDistance = true -> el cliente las dibuja aunque esten lejos
        player.connection.send(new ClientboundLevelParticlesPacket(type, true, x, y, z, dx, dy, dz, speed, count));
    }

    /** Particulas con dispersion aleatoria (count > 0). */
    public static void burst(ServerLevel level, ParticleOptions type, double x, double y, double z,
                             int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= RANGE_SQ) {
                send(player, type, x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, count);
            }
        }
    }

    /** Una particula con velocidad definida (con count = 0 el "offset" funciona como velocidad). */
    public static void dir(ServerLevel level, ParticleOptions type, double x, double y, double z,
                           double vx, double vy, double vz) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= RANGE_SQ) {
                send(player, type, x, y, z, (float) vx, (float) vy, (float) vz, 1.0F, 0);
            }
        }
    }

    /** Una particula con velocidad, enviada solo a un jugador. */
    public static void dirTo(ServerLevel level, ServerPlayer player, ParticleOptions type,
                             double x, double y, double z, double vx, double vy, double vz) {
        send(player, type, x, y, z, (float) vx, (float) vy, (float) vz, 1.0F, 0);
    }

    public static void sound(ServerLevel level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.AMBIENT, volume, pitch);
    }
}
