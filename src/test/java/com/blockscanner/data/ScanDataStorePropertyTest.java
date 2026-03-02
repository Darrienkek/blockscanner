package com.blockscanner.data;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based tests for ScanDataStore.
 * Feature: block-scanner-mod
 */
public class ScanDataStorePropertyTest {

    /**
     * Property 7: No Duplicate Block Entries
     * For any block position (x, y, z, dimension), adding it to the data store multiple times
     * SHALL result in exactly one entry for that position.
     *
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Label("Feature: block-scanner-mod, Property 7: No Duplicate Block Entries")
    void noDuplicateBlockEntries(
        @ForAll @IntRange(min = -30000000, max = 30000000) int x,
        @ForAll @IntRange(min = -64, max = 320) int y,
        @ForAll @IntRange(min = -30000000, max = 30000000) int z,
        @ForAll("blockTypes") String blockType,
        @ForAll("dimensions") String dimension,
        @ForAll @IntRange(min = 2, max = 10) int duplicateCount
    ) {
        ScanDataStore store = new ScanDataStore();

        for (int i = 0; i < duplicateCount; i++) {
            ScanResult result = new ScanResult(blockType, x, y, z, dimension, System.currentTimeMillis() + i, null);
            store.addFoundBlock(result);
        }

        List<ScanResult> foundBlocks = store.getFoundBlocks();
        long matchingBlocks = foundBlocks.stream()
            .filter(result -> result.x() == x && result.y() == y && result.z() == z && result.dimension().equals(dimension))
            .count();

        assertEquals(1, matchingBlocks);
    }

    /**
     * Property 3: Chunk Scan Idempotence
     * For any chunk that has been marked as scanned, attempting to scan it again
     * SHALL NOT add duplicate entries to the scan results or re-process the chunk.
     *
     * **Validates: Requirements 2.6**
     */
    @Property(tries = 100)
    @Label("Feature: block-scanner-mod, Property 3: Chunk Scan Idempotence")
    void chunkScanIdempotence(
        @ForAll @IntRange(min = -1875000, max = 1875000) int chunkX,
        @ForAll @IntRange(min = -1875000, max = 1875000) int chunkZ,
        @ForAll("dimensions") String dimension,
        @ForAll @IntRange(min = 2, max = 10) int scanCount
    ) {
        ScanDataStore store = new ScanDataStore();

        for (int i = 0; i < scanCount; i++) {
            store.markChunkScanned(chunkX, chunkZ, dimension);
        }

        long matchingChunks = store.getScannedChunks().stream()
            .filter(chunk -> chunk.chunkX() == chunkX && chunk.chunkZ() == chunkZ && chunk.dimension().equals(dimension))
            .count();

        assertEquals(1, matchingChunks);
        assertTrue(store.isChunkScanned(chunkX, chunkZ, dimension));
    }

    @Property(tries = 100)
    @Label("Feature: block-scanner-mod, Traversal state retained across session clears")
    void traversalStateRetainedAcrossSessionClear(
        @ForAll("dimensions") String dimension,
        @ForAll("cursorStates") SpiralCursorState cursor
    ) {
        ScanDataStore store = new ScanDataStore();
        store.updateTraversalState(dimension, cursor);

        store.clearSessionData();

        assertEquals(cursor, store.getTraversalState(dimension));
    }

    @Property(tries = 100)
    @Label("Feature: block-scanner-mod, Traversal state round-trip via snapshot")
    void traversalStateSnapshotRoundTrip(
        @ForAll("dimensions") String dimension,
        @ForAll("cursorStates") SpiralCursorState cursor
    ) {
        ScanDataStore store = new ScanDataStore();
        store.setCurrentServer("singleplayer");
        store.updateTraversalState(dimension, cursor);

        ScanDataSnapshot snapshot = store.getSnapshot();
        ScanDataStore restored = new ScanDataStore();
        restored.loadFromSnapshot(snapshot);

        assertEquals(cursor, restored.getTraversalState(dimension));
    }

    @Provide
    Arbitrary<String> blockTypes() {
        return Arbitraries.of("barrier", "command_block", "chain_command_block", "repeating_command_block");
    }

    @Provide
    Arbitrary<String> dimensions() {
        return Arbitraries.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    }

    @Provide
    Arbitrary<SpiralCursorState> cursorStates() {
        return Arbitraries.integers().between(1, 50)
            .flatMap(legLength -> Combinators.combine(
                Arbitraries.integers().between(-2000, 2000),
                Arbitraries.integers().between(-2000, 2000),
                Arbitraries.integers().between(0, 3),
                Arbitraries.integers().between(0, legLength),
                Arbitraries.integers().between(0, 1),
                Arbitraries.longs().between(0, 1_000_000)
            ).as((x, z, direction, steps, legsCompleted, completedWaypoints) ->
                new SpiralCursorState(x, z, direction, legLength, steps, legsCompleted, completedWaypoints)
            ));
    }
}