package com.desastres.disaster.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Registro persistente del hielo generado por los cometas.
 *
 * - Cada evento guarda EXACTAMENTE las posiciones (y el tipo de hielo) que el impacto coloco.
 * - El temporizador usa el game time del nivel (ticks del servidor): 400 ticks = 20 s despues de generar el hielo.
 *   Como el game time se guarda con el mundo, si se cierra y se vuelve a abrir el mundo la cuenta continua sin
 *   adelantarse ni conversiones incorrectas (el tiempo no avanza mientras el mundo esta cerrado).
 * - Al convertir se vuelve a comprobar que el bloque siga siendo el hielo que puso el evento; si alguien lo
 *   cambio, no se toca. El hielo que ya existia en el mundo nunca esta en el registro.
 */
public class CometIceData extends SavedData {
    private static final String NAME = "disasters_comet_ice";
    /** 20 segundos exactos a 20 ticks/s. */
    public static final int MELT_DELAY_TICKS = 400;

    public static final byte KIND_ICE = 0;
    public static final byte KIND_PACKED = 1;
    public static final byte KIND_BLUE = 2;

    private static final class Event {
        long meltAt;
        long[] pos;
        byte[] kind;
        int next;
    }

    private final List<Event> events = new ArrayList<>();

    public static CometIceData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CometIceData::load, CometIceData::new, NAME);
    }

    /** Devuelve los datos solo si ya existen (no crea archivos nuevos). */
    private static CometIceData peek(ServerLevel level) {
        return level.getDataStorage().get(CometIceData::load, NAME);
    }

    public static Block blockOf(byte kind) {
        if (kind == KIND_PACKED) {
            return Blocks.PACKED_ICE;
        }
        if (kind == KIND_BLUE) {
            return Blocks.BLUE_ICE;
        }
        return Blocks.ICE;
    }

    /** Registra un evento de hielo. El conteo de 20 s empieza en el tick de juego indicado. */
    public void addEvent(long generatedAtGameTime, long[] positions, byte[] kinds) {
        if (positions.length == 0) {
            return;
        }
        Event ev = new Event();
        ev.meltAt = generatedAtGameTime + MELT_DELAY_TICKS;
        ev.pos = positions;
        ev.kind = kinds;
        ev.next = 0;
        events.add(ev);
        setDirty();
    }

    // ------------------------------------------------------------------ tick

    public static void tickAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            CometIceData data = peek(level);
            if (data != null && !data.events.isEmpty()) {
                data.tick(level);
            }
        }
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        boolean changed = false;
        Iterator<Event> it = events.iterator();
        while (it.hasNext()) {
            Event ev = it.next();
            if (now < ev.meltAt) {
                continue;
            }
            // La conversion empieza exactamente a los 20 s y se reparte en unos ticks (de dentro hacia fuera)
            int batch = Math.max(60, ev.pos.length / 25);
            int end = Math.min(ev.pos.length, ev.next + batch);
            for (; ev.next < end; ev.next++) {
                melt(level, ev.pos[ev.next], ev.kind[ev.next]);
            }
            changed = true;
            if (ev.next >= ev.pos.length) {
                it.remove(); // limpieza del evento terminado
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private void melt(ServerLevel level, long packedPos, byte kind) {
        BlockPos p = BlockPos.of(packedPos);
        if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p)) {
            return; // chunk descargado: el bloque se queda como esta (sin errores ni cargas forzadas)
        }
        BlockState current = level.getBlockState(p);
        if (!current.is(blockOf(kind))) {
            return; // alguien lo cambio despues del impacto: no se toca
        }
        if (isCovered(level, p)) {
            // Cubierto por una estructura: se evapora sin generar agua (evita inundar interiores)
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** True si hay algun bloque "de verdad" (techo, hojas, estructura) justo encima. */
    private static boolean isCovered(ServerLevel level, BlockPos p) {
        for (int i = 1; i <= 8; i++) {
            BlockPos q = p.above(i);
            if (level.isOutsideBuildHeight(q)) {
                return false;
            }
            BlockState s = level.getBlockState(q);
            if (s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty()) {
                continue;
            }
            if (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE)) {
                continue;
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ guardado

    public static CometIceData load(CompoundTag tag) {
        CometIceData data = new CometIceData();
        ListTag list = tag.getList("events", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Event ev = new Event();
            ev.meltAt = t.getLong("meltAt");
            ev.next = t.getInt("next");
            ev.pos = t.getLongArray("pos");
            ev.kind = t.getByteArray("kind");
            if (ev.pos.length == 0 || ev.kind.length != ev.pos.length || ev.next < 0 || ev.next > ev.pos.length) {
                continue; // dato corrupto: se descarta sin romper nada
            }
            data.events.add(ev);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Event ev : events) {
            CompoundTag t = new CompoundTag();
            t.putLong("meltAt", ev.meltAt);
            t.putInt("next", ev.next);
            t.putLongArray("pos", ev.pos);
            t.putByteArray("kind", ev.kind);
            list.add(t);
        }
        tag.put("events", list);
        return tag;
    }
}
