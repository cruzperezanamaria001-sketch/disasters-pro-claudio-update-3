package com.desastres.disaster;

import com.desastres.DisastersMod;
import com.desastres.config.DisasterConfig;
import com.desastres.disaster.impl.CometIceData;
import com.desastres.disaster.impl.FrozenTerrainData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Mantiene la lista de desastres activos y los ticka cada tick del servidor. */
@Mod.EventBusSubscriber(modid = DisastersMod.MODID)
public final class DisasterManager {
    private static final List<Disaster> ACTIVE = new ArrayList<>();
    private static final List<Disaster> PENDING = new ArrayList<>();
    /** Entidades que un desastre esta transportando (gusano / agujero negro-blanco). Los demas desastres las ignoran. */
    private static final Set<UUID> IN_TRANSIT = new HashSet<>();

    private DisasterManager() {}

    /** Anade un desastre (se activa en el siguiente tick). Solo desde el hilo del servidor. */
    public static void add(Disaster disaster) {
        PENDING.add(disaster);
    }

    public static boolean isInTransit(Entity entity) {
        return !IN_TRANSIT.isEmpty() && IN_TRANSIT.contains(entity.getUUID());
    }

    public static void markTransit(Entity entity) {
        IN_TRANSIT.add(entity.getUUID());
    }

    public static void unmarkTransit(Entity entity) {
        IN_TRANSIT.remove(entity.getUUID());
    }

    public static boolean hasActive() {
        return !ACTIVE.isEmpty() || !PENDING.isEmpty();
    }

    public static boolean canUse(ServerPlayer player) {
        if (!DisasterConfig.REQUIRE_PERMISSION.get()) {
            return true;
        }
        return player.isCreative() || player.hasPermissions(2);
    }

    private static int countMajor() {
        int n = 0;
        for (Disaster d : ACTIVE) {
            if (!d.isMinor() && !d.isFinished()) n++;
        }
        for (Disaster d : PENDING) {
            if (!d.isMinor() && !d.isFinished()) n++;
        }
        return n;
    }

    /**
     * Crea un desastre.
     * @param targetMode 0 = donde mira el jugador, 1 = sobre el jugador
     */
    public static boolean spawn(ServerPlayer player, DisasterType type, int intensity, int targetMode) {
        if (!canUse(player)) {
            player.displayClientMessage(Component.translatable("message.disasters.no_permission"), true);
            return false;
        }
        if (countMajor() >= DisasterConfig.MAX_ACTIVE.get()) {
            player.displayClientMessage(Component.translatable("message.disasters.too_many"), true);
            return false;
        }

        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 target;
        if (targetMode == 1) {
            target = player.position();
        } else {
            HitResult hit = player.pick(160.0D, 1.0F, false);
            if (hit.getType() == HitResult.Type.MISS) {
                double x = player.getX() + look.x * 24.0D;
                double z = player.getZ() + look.z * 24.0D;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                target = new Vec3(x, y, z);
            } else {
                target = hit.getLocation();
            }
        }

        if (!level.hasChunkAt(BlockPos.containing(target))) {
            player.displayClientMessage(Component.translatable("message.disasters.unloaded"), true);
            return false;
        }

        Disaster disaster = type.create(level, target, look, intensity);
        PENDING.add(disaster);
        player.displayClientMessage(Component.translatable("message.disasters.spawned",
                Component.translatable(type.translationKey()), intensity), true);
        return true;
    }

    /** Detiene todos los desastres activos. Devuelve cuantos habia. */
    public static int stopAll() {
        int n = 0;
        for (Disaster d : ACTIVE) {
            if (!d.isFinished()) {
                d.finish();
                n++;
            }
        }
        for (Disaster d : PENDING) {
            if (!d.isFinished()) {
                d.finish();
                n++;
            }
        }
        return n;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Hielo del cometa: el temporizador de 20 s va por game time del nivel (ticks del servidor)
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            CometIceData.tickAll(server);
            FrozenTerrainData.tickAll(server);
        }

        if (!PENDING.isEmpty()) {
            ACTIVE.addAll(PENDING);
            PENDING.clear();
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Disaster> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Disaster d = it.next();
            try {
                d.doTick();
            } catch (Exception ex) {
                DisastersMod.LOGGER.error("Error en el desastre {}", d.getClass().getSimpleName(), ex);
                d.finish();
            }
            if (d.isFinished()) {
                try {
                    d.onEnd();
                } catch (Exception ex) {
                    DisastersMod.LOGGER.error("Error al limpiar el desastre {}", d.getClass().getSimpleName(), ex);
                }
                d.cleanupVisual();
                it.remove();
            }
        }
    }

    /** Una entidad en transito no puede recibir dano de otras fuentes (salvo /kill y vacio). */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (IN_TRANSIT.isEmpty() || !IN_TRANSIT.contains(event.getEntity().getUUID())) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        PENDING.clear();
        IN_TRANSIT.clear();
    }
}
