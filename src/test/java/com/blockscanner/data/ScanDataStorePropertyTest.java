package com.blockscanner.data;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

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
        
        // Add the same block multiple times with different timestamps
        for (int i = 0; i < duplicateCount; i++) {
            ScanResult result = new ScanResult(blockType, x, y, z, dimension, System.currentTimeMillis() + i, null);
            store.addFoundBlock(result);
        }
        
        // Should have exactly one entry for this position
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
        
        // Create a simple ChunkPos-like structure for testing
        // Mark the chunk as scanned multiple times
        for (int i = 0; i < scanCount; i++) {
            store.markChunkScanned(chunkX, chunkZ, dimension);
        }
        
        // Should have exactly one entry for this chunk
        long matchingChunks = store.getScannedChunks().stream()
            .filter(chunk -> chunk.chunkX() == chunkX && chunk.chunkZ() == chunkZ && chunk.dimension().equals(dimension))
            .count();
            
        assertEquals(1, matchingChunks);
        
        // Should report as scanned
        assertTrue(store.isChunkScanned(chunkX, chunkZ, dimension));
    }

    @Provide
    Arbitrary<String> blockTypes() {
        return Arbitraries.of("barrier", "command_block", "chain_command_block", "repeating_command_block");
    }

    @Provide
    Arbitrary<String> dimensions() {
        return Arbitraries.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    }
}
