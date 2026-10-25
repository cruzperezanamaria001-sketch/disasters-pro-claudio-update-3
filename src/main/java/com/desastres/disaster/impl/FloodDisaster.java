package com.desastres.disaster.impl;

import com.desastres.disaster.Disaster;
import com.desastres.disaster.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Inundacion: el agua sube por capas en una zona, se mantiene y luego baja. */
public class FloodDisaster extends Disaster {
    private static final int RISE_INTERVAL = 20;
    private static final int DRAIN_INTERVAL = 15;

    private enum Phase { RISE, HOLD, DRAIN }

    private final int radius;
    private final int startY;
    private final int topY;
    private final int holdTicks;
    private final List<int[]> columns = new ArrayList<>(); // {x, z, groundY}
    private final List<List<BlockPos>> rows = new ArrayList<>();

    private Phase phase = Phase.RISE;
    private int currentY;
    private int holdTimer = 0;

    public FloodDisaster(ServerLevel level, Vec3 target, int intensity) {
        super(level, target, intensity);
        this.radius = 18 + 10 * intensity;
        this.holdTicks = scaled(600);
        int cx = Mth.floor(target.x);
        int cz = Mth.floor(target.z);
        int baseY = surface(cx, cz);
        this.topY = baseY + 4 + 3 * intensity;

        int minGround = baseY;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                if (!loaded(x, z)) {
                    continue;
                }
                int g = surface(x, z);
                columns.add(new int[]{x, z, g});
                if (g < minGround) {
                    minGround = g;
                }
            }
        }
        this.startY = Math.max(minGround, baseY - 10);
        this.currentY = startY - 1;

        int steps = topY - startY + 1;
        int rainTicks = steps * RISE_INTERVAL + holdTicks + steps * DRAIN_INTERVAL + 200;
        level.setWeatherParameters(0, rainTicks, true, true);
    }

    @Override
    protected void tick() {
        switch (phase) {
            case RISE:
                if (age % RISE_INTERVAL == 0) {
                    if (currentY < topY) {
                        currentY++;
                        riseTo(currentY, rows.isEmpty());
                        Fx.sound(level, pos.x, currentY, pos.z, SoundEvents.GENERIC_SPLASH, 6.0F, 0.6F);
                    } else {
                        phase = Phase.HOLD;
                        holdTimer = 0;
                    }
                }
                break;
            case HOLD:
                holdTimer++;
                if (holdTimer >= holdTicks) {
                    phase = Phase.DRAIN;
                }
                break;
            case DRAIN:
                if (age % DRAIN_INTERVAL == 0) {
                    if (rows.isEmpty()) {
                        finish();
                    } else {
                        removeRow(rows.remove(rows.size() - 1));
                    }
                }
                break;
            default:
                break;
        }

        if (phase != Phase.DRAIN) {
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double r = radius * Math.sqrt(random.nextDouble());
                Fx.burst(level, ParticleTypes.SPLASH, pos.x + Math.cos(a) * r, currentY + 1.0D,
                        pos.z + Math.sin(a) * r, 6, 0.5D, 0.2D, 0.5D, 0.1D);
            }
        }
    }

    private void riseTo(int y, boolean first) {
        List<BlockPos> row = new ArrayList<>();
        if (grief) {
            for (int[] c : columns) {
                int g = c[2];
                if (g > y) {
                    continue;
                }
                int from = first ? Math.max(g, y - 12) : y;
                for (int yy = from; yy <= y; yy++) {
                    BlockPos bp = new BlockPos(c[0], yy, c[1]);
                    BlockState st = level.getBlockState(bp);
                    if (st.isAir() || (st.canBeReplaced() && st.getFluidState().isEmpty())) {
                        level.setBlock(bp, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                        row.add(bp);
                    }
                }
            }
        }
        rows.add(row);
    }

    private void removeRow(List<BlockPos> row) {
        for (BlockPos bp : row) {
            if (loaded(bp) && level.getBlockState(bp).is(Blocks.WATER)) {
                level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void onEnd() {
        for (List<BlockPos> row : rows) {
            removeRow(row);
        }
        rows.clear();
    }
}
