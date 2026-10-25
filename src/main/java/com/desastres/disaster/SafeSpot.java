package com.desastres.disaster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Busqueda de posiciones seguras para teletransportar entidades y para anclar portales.
 * Nunca fuerza la carga de chunks: solo mira chunks que ya estan cargados.
 */
public final class SafeSpot {

    private SafeSpot() {}

    private static boolean loaded(ServerLevel level, int x, int z) {
        return level.hasChunkAt(new BlockPos(x, 0, z));
    }

    private static boolean hazard(BlockState st) {
        return st.is(BlockTags.FIRE) || st.is(Blocks.LAVA) || st.is(Blocks.MAGMA_BLOCK)
                || st.is(Blocks.CACTUS) || st.is(Blocks.CAMPFIRE) || st.is(Blocks.SOUL_CAMPFIRE)
                || st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.WITHER_ROSE) || st.is(Blocks.POWDER_SNOW)
                || st.is(Blocks.COBWEB);
    }

    /** Comprueba si la entidad cabe en (x, y, z) sin colisionar, sin liquidos, sin peligros y con suelo firme. */
    public static boolean isSafe(ServerLevel level, Entity e, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }
        if (y <= level.getMinBuildHeight() + 2 || y >= level.getMaxBuildHeight() - 3) {
            return false;
        }
        AABB box = e.getType().getDimensions().makeBoundingBox(x, y, z);
        if (!level.noCollision(e, box)) {
            return false;
        }
        if (level.containsAnyLiquid(box.inflate(0.1D))) {
            return false;
        }
        BlockPos min = BlockPos.containing(box.minX - 0.5D, box.minY - 1.2D, box.minZ - 0.5D);
        BlockPos max = BlockPos.containing(box.maxX + 0.5D, box.maxY, box.maxZ + 0.5D);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(p)) {
                return false;
            }
            if (hazard(level.getBlockState(p))) {
                return false;
            }
        }
        BlockPos below = BlockPos.containing(x, box.minY - 0.1D, z);
        BlockState floor = level.getBlockState(below);
        return floor.isFaceSturdy(level, below, Direction.UP);
    }

    /** Busca un punto seguro para la entidad cerca de (cx, cz), sobre el suelo. Devuelve null si no hay. */
    public static Vec3 findNear(ServerLevel level, Entity e, double cx, double cz, int radius, int attempts, RandomSource rnd) {
        for (int i = 0; i < attempts; i++) {
            double x = cx + (rnd.nextDouble() * 2.0D - 1.0D) * radius;
            double z = cz + (rnd.nextDouble() * 2.0D - 1.0D) * radius;
            if (!loaded(level, Mth.floor(x), Mth.floor(z))) {
                continue;
            }
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            for (int dy = 0; dy <= 1; dy++) {
                if (isSafe(level, e, x, gy + dy, z)) {
                    return new Vec3(x, gy + dy, z);
                }
            }
        }
        return null;
    }

    /**
     * Busca el centro de un portal: terreno firme, sin liquidos ni peligros y con un hueco libre encima.
     * Devuelve el punto a nivel de suelo (x + 0.5, groundY, z + 0.5) o null.
     */
    public static Vec3 findAnchor(ServerLevel level, double cx, double cz, double minDist, double maxDist,
                                  int attempts, RandomSource rnd) {
        for (int i = 0; i < attempts; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0D;
            double dist = minDist + rnd.nextDouble() * (maxDist - minDist);
            int x = Mth.floor(cx + Math.cos(ang) * dist);
            int z = Mth.floor(cz + Math.sin(ang) * dist);
            if (!loaded(level, x, z) || !loaded(level, x + 8, z) || !loaded(level, x - 8, z)
                    || !loaded(level, x, z + 8) || !loaded(level, x, z - 8)) {
                continue;
            }
            if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, 64, z))) {
                continue;
            }
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (gy <= level.getMinBuildHeight() + 6 || gy >= level.getMaxBuildHeight() - 12) {
                continue;
            }
            BlockPos ground = new BlockPos(x, gy - 1, z);
            BlockState gs = level.getBlockState(ground);
            if (!gs.getFluidState().isEmpty() || hazard(gs) || !gs.isFaceSturdy(level, ground, Direction.UP)) {
                continue;
            }
            // hueco libre de 3x3 en planta y 5 de alto sobre el suelo
            boolean open = true;
            for (int dx = -1; dx <= 1 && open; dx++) {
                for (int dz = -1; dz <= 1 && open; dz++) {
                    for (int dy = 0; dy < 5; dy++) {
                        BlockPos p = new BlockPos(x + dx, gy + dy, z + dz);
                        BlockState st = level.getBlockState(p);
                        if (!st.getCollisionShape(level, p).isEmpty() || !st.getFluidState().isEmpty() || hazard(st)) {
                            open = false;
                            break;
                        }
                    }
                }
            }
            if (open) {
                return new Vec3(x + 0.5D, gy, z + 0.5D);
            }
        }
        return null;
    }
}
