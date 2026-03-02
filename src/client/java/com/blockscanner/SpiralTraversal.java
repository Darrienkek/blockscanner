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
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        return new ChunkCoordinate(
            state.waypointGridX() * WAYPOINT_STEP,
            state.waypointGridZ() * WAYPOINT_STEP
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
        List<ChunkCoordinate> chunks = new ArrayList<>(BATCH_TOTAL);
        for (int x = centerChunkX - BATCH_RADIUS; x <= centerChunkX + BATCH_RADIUS; x++) {
            for (int z = centerChunkZ - BATCH_RADIUS; z <= centerChunkZ + BATCH_RADIUS; z++) {
                chunks.add(new ChunkCoordinate(x, z));
            }
        }
        return chunks;
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