package com.blockscanner;

import com.blockscanner.data.SpiralCursorState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiralTraversalTest {

    @Test
    void firstWaypointsFollowClockwisePositiveXFirstSpiral() {
        SpiralCursorState state = SpiralCursorState.initial();

        List<SpiralTraversal.ChunkCoordinate> expectedCenters = List.of(
            new SpiralTraversal.ChunkCoordinate(0, 0),
            new SpiralTraversal.ChunkCoordinate(7, 0),
            new SpiralTraversal.ChunkCoordinate(7, 7),
            new SpiralTraversal.ChunkCoordinate(0, 7),
            new SpiralTraversal.ChunkCoordinate(-7, 7),
            new SpiralTraversal.ChunkCoordinate(-7, 0),
            new SpiralTraversal.ChunkCoordinate(-7, -7),
            new SpiralTraversal.ChunkCoordinate(0, -7),
            new SpiralTraversal.ChunkCoordinate(7, -7)
        );

        for (SpiralTraversal.ChunkCoordinate expected : expectedCenters) {
            assertEquals(expected, SpiralTraversal.currentCenterChunk(state));
            state = SpiralTraversal.advance(state);
        }
    }

    @Test
    void currentCenterChunkUsesWaypointStepOfSeven() {
        SpiralCursorState state = new SpiralCursorState(2, -3, 0, 1, 0, 0, 0);
        SpiralTraversal.ChunkCoordinate center = SpiralTraversal.currentCenterChunk(state);

        assertEquals(14, center.chunkX());
        assertEquals(-21, center.chunkZ());
    }

    @Test
    void legLengthIncreasesAfterEveryTwoLegs() {
        SpiralCursorState state = SpiralCursorState.initial();

        state = SpiralTraversal.advance(state);
        assertEquals(1, state.legLength());
        assertEquals(1, state.directionIndex());
        assertEquals(1, state.legsCompletedAtLength());

        state = SpiralTraversal.advance(state);
        assertEquals(2, state.legLength());
        assertEquals(2, state.directionIndex());
        assertEquals(0, state.legsCompletedAtLength());

        state = SpiralTraversal.advance(state);
        assertEquals(2, state.legLength());
        assertEquals(2, state.directionIndex());

        state = SpiralTraversal.advance(state);
        assertEquals(2, state.legLength());
        assertEquals(3, state.directionIndex());
        assertEquals(1, state.legsCompletedAtLength());

        state = SpiralTraversal.advance(state);
        assertEquals(2, state.legLength());
        assertEquals(3, state.directionIndex());

        state = SpiralTraversal.advance(state);
        assertEquals(3, state.legLength());
        assertEquals(0, state.directionIndex());
        assertEquals(0, state.legsCompletedAtLength());
    }

    @Test
    void enumerateBatchChunksReturnsSevenBySevenWindow() {
        List<SpiralTraversal.ChunkCoordinate> chunks = SpiralTraversal.enumerateBatchChunks(14, -21);

        assertEquals(49, chunks.size());
        assertEquals(new SpiralTraversal.ChunkCoordinate(11, -24), chunks.getFirst());
        assertEquals(new SpiralTraversal.ChunkCoordinate(17, -18), chunks.getLast());
        assertTrue(chunks.contains(new SpiralTraversal.ChunkCoordinate(14, -21)));
    }
}