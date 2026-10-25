package com.desastres.disaster.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
 * Registro persistente del hielo TEMPORAL de la ola de congelacion.
 *
 * Cada evento guarda las posiciones, el tipo de hielo colocado y el bloque ORIGINAL (en una paleta). Al llegar
 * la hora (game time del nivel = ticks del servidor) se restaura el bloque original unicamente si la posicion
 * sigue siendo el hielo que puso el evento: si un jugador (u otro sistema) la cambio despues, no se toca.
 * Las posiciones en chunks descargados se reintentan mas tarde, asi nunca queda hielo permanente.
 * Al guardarse con el mundo, cerrar el juego no deja el terreno congelado para siempre.
 */
public class FrozenTerrainData extends SavedData {
    private static final String NAME = "disasters_frozen_terrain";

    public static final byte KIND_PACKED = 0; // suelo natural
    public static final byte KIND_ICE = 1;    // superficie de agua
    private static final byte DONE = -1;

    private static final class Event {
        long restoreAt;
        long[] pos;
        byte[] kind;
        byte[] orig;                       // indice en la paleta
        List<BlockState> palette = new ArrayList<>();
        int remaining;
        int cursor;
        long nextTry;
    }

    private final List<Event> events = new ArrayList<>();

    public static FrozenTerrainData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FrozenTerrainData::load, FrozenTerrainData::new, NAME);
    }

    private static FrozenTerrainData peek(ServerLevel level) {
        return level.getDataStorage().get(FrozenTerrainData::load, NAME);
    }

    public static Block blockOf(byte kind) {
        return kind == KIND_ICE ? Blocks.ICE : Blocks.PACKED_ICE;
    }

    public void addEvent(long restoreAt, long[] positions, byte[] kinds, byte[] origIndex, List<BlockState> palette) {
        if (positions.length == 0) {
            return;
        }
        Event ev = new Event();
        ev.restoreAt = restoreAt;
        ev.pos = positions;
        ev.kind = kinds;
        ev.orig = origIndex;
        ev.palette = new ArrayList<>(palette);
        ev.remaining = positions.length;
        events.add(ev);
        setDirty();
    }

    // ------------------------------------------------------------------ tick

    public static void tickAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            FrozenTerrainData data = peek(level);
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
            if (now < ev.restoreAt || now < ev.nextTry) {
                continue;
            }
            int budget = Math.max(200, ev.pos.length / 10);
            int processed = 0;
            while (ev.cursor < ev.pos.length && processed < budget) {
                int i = ev.cursor++;
                if (ev.kind[i] == DONE) {
                    continue;
                }
                processed++;
                if (restoreOne(level, ev, i)) {
                    ev.kind[i] = DONE;
                    ev.remaining--;
                }
            }
            changed = true;
            if (ev.cursor >= ev.pos.length) {
                if (ev.remaining <= 0) {
                    it.remove(); // evento terminado: se elimina del registro
                } else {
                    ev.cursor = 0;          // quedan posiciones en chunks descargados
                    ev.nextTry = now + 100; // se reintentan mas tarde
                }
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /** true = hecho (restaurado o descartado a proposito); false = reintentar (chunk descargado). */
    private boolean restoreOne(ServerLevel level, Event ev, int i) {
        BlockPos p = BlockPos.of(ev.pos[i]);
        if (level.isOutsideBuildHeight(p)) {
            return true;
        }
        if (!level.hasChunkAt(p)) {
            return false;
        }
        BlockState current = level.getBlockState(p);
        if (!current.is(blockOf(ev.kind[i]))) {
            return true; // alguien lo cambio despues: no se sobrescribe
        }
        int idx = ev.orig[i];
        if (idx < 0 || idx >= ev.palette.size()) {
            return true;
        }
        level.setBlock(p, ev.palette.get(idx), Block.UPDATE_CLIENTS);
        return true;
    }

    // ------------------------------------------------------------------ guardado

    public static FrozenTerrainData load(CompoundTag tag) {
        FrozenTerrainData data = new FrozenTerrainData();
        ListTag list = tag.getList("events", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Event ev = new Event();
            ev.restoreAt = t.getLong("restoreAt");
            ev.pos = t.getLongArray("pos");
            ev.kind = t.getByteArray("kind");
            ev.orig = t.getByteArray("orig");
            ListTag pal = t.getList("palette", Tag.TAG_COMPOUND);
            for (int k = 0; k < pal.size(); k++) {
                ev.palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pal.getCompound(k)));
            }
            if (ev.pos.length == 0 || ev.kind.length != ev.pos.length || ev.orig.length != ev.pos.length) {
                continue; // dato corrupto: se descarta sin romper nada
            }
            int remaining = 0;
            for (byte k : ev.kind) {
                if (k != DONE) {
                    remaining++;
                }
            }
            if (remaining == 0) {
                continue;
            }
            ev.remaining = remaining;
            data.events.add(ev);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Event ev : events) {
            CompoundTag t = new CompoundTag();
            t.putLong("restoreAt", ev.restoreAt);
            t.putLongArray("pos", ev.pos);
            t.putByteArray("kind", ev.kind);
            t.putByteArray("orig", ev.orig);
            ListTag pal = new ListTag();
            for (BlockState st : ev.palette) {
                pal.add(NbtUtils.writeBlockState(st));
            }
            t.put("palette", pal);
            list.add(t);
        }
        tag.put("events", list);
        return tag;
    }
}
