package com.blockscanner;

import com.blockscanner.data.SpiralCursorState;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure traversal utilities for squared-spiral chunk waypoint generation.
 */
public final class SpiralTraversal {
    public static final int BATCH_SIDE = 7;
    public static final int BATCH_RADIUS = 3;
    public static final int BATCH_TOTAL = BATCH_SIDE * BATCH_SIDE;
    public static final int WAYPOINT_STEP = BATCH_SIDE;

    private static final int[][] DIRECTION_STEPS = {
        {1, 0},
        {0, 1},
        {-1, 0},
        {0, -1}
    };

    private SpiralTraversal() {
    }

    public record ChunkCoordinate(int chunkX, int chunkZ) {
    }

    public static ChunkCoordinate currentCenterChunk(SpiralCursorState state) {
        return currentCenterChunk(state, WAYPOINT_STEP);
    }

    public static ChunkCoordinate currentCenterChunk(SpiralCursorState state, int waypointStep) {
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        if (waypointStep <= 0) {
            throw new IllegalArgumentException("waypointStep must be greater than 0");
        }
        return new ChunkCoordinate(
            state.waypointGridX() * waypointStep,
            state.waypointGridZ() * waypointStep
        );
    }

    public static SpiralCursorState advance(SpiralCursorState state) {
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }

        int directionIndex = state.directionIndex();
        int[] direction = DIRECTION_STEPS[directionIndex];
        int nextGridX = state.waypointGridX() + direction[0];
        int nextGridZ = state.waypointGridZ() + direction[1];

        int legLength = state.legLength();
        int stepsTakenOnLeg = state.stepsTakenOnLeg() + 1;
        int legsCompletedAtLength = state.legsCompletedAtLength();

        if (stepsTakenOnLeg >= legLength) {
            stepsTakenOnLeg = 0;
            directionIndex = (directionIndex + 1) % DIRECTION_STEPS.length;
            legsCompletedAtLength += 1;
            if (legsCompletedAtLength >= 2) {
                legsCompletedAtLength = 0;
                legLength += 1;
            }
        }

        return new SpiralCursorState(
            nextGridX,
            nextGridZ,
            directionIndex,
            legLength,
            stepsTakenOnLeg,
            legsCompletedAtLength,
            state.waypointsCompleted() + 1
        );
    }

    public static List<ChunkCoordinate> enumerateBatchChunks(int centerChunkX, int centerChunkZ) {
        return enumerateBatchChunks(centerChunkX, centerChunkZ, BATCH_RADIUS);
    }

    public static List<ChunkCoordinate> enumerateBatchChunks(int centerChunkX, int centerChunkZ, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius cannot be negative");
        }
        int total = batchTotalFromRadius(radius);
        List<ChunkCoordinate> chunks = new ArrayList<>(total);
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                chunks.add(new ChunkCoordinate(x, z));
            }
        }
        return chunks;
    }

    public static int batchSideFromRadius(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius cannot be negative");
        }
        return (radius * 2) + 1;
    }

    public static int batchTotalFromRadius(int radius) {
        int side = batchSideFromRadius(radius);
        return side * side;
    }

    public static String directionName(int directionIndex) {
        return switch (Math.floorMod(directionIndex, DIRECTION_STEPS.length)) {
            case 0 -> "+X";
            case 1 -> "+Z";
            case 2 -> "-X";
            default -> "-Z";
        };
    }
}
