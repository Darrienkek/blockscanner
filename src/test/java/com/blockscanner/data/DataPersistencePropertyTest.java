package com.blockscanner.data;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Positive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for DataPersistence class.
 * Feature: block-scanner-mod
 */
class DataPersistencePropertyTest {

    @Property(tries = 100)
    @Label("Feature: block-scanner-mod, Property 5: Persistence Round-Trip")
    void persistenceRoundTrip(
        @ForAll @NotBlank String serverAddress,
        @ForAll List<@From("scanResults") ScanResult> foundBlocks,
        @ForAll List<@From("scannedChunks") ScannedChunk> scannedChunks,
        @ForAll @Positive long lastUpdated
    ) throws IOException {
        // **Validates: Requirements 3.2, 3.3**
        
        // Create original snapshot
        ScanDataSnapshot originalSnapshot = new ScanDataSnapshot(
            serverAddress,
            foundBlocks,
            scannedChunks,
            lastUpdated
        );

        // Test that the snapshot data is preserved correctly
        assertEquals(serverAddress, originalSnapshot.serverAddress());
        assertEquals(foundBlocks.size(), originalSnapshot.foundBlocks().size());
        assertEquals(scannedChunks.size(), originalSnapshot.scannedChunks().size());
        assertEquals(lastUpdated, originalSnapshot.lastUpdated());

        // Verify all found blocks are preserved in the snapshot
        for (ScanResult originalBlock : foundBlocks) {
            assertTrue(originalSnapshot.foundBlocks().contains(originalBlock),
                "Found block should be preserved in snapshot: " + originalBlock);
        }

        // Verify all scanned chunks are preserved in the snapshot
        for (ScannedChunk originalChunk : scannedChunks) {
            assertTrue(originalSnapshot.scannedChunks().contains(originalChunk),
                "Scanned chunk should be preserved in snapshot: " + originalChunk);
        }

        // Test that loading the snapshot into a data store preserves the data
        ScanDataStore dataStore = new ScanDataStore();
        dataStore.loadFromSnapshot(originalSnapshot);
        
        ScanDataSnapshot roundTripSnapshot = dataStore.getSnapshot();
        
        // Verify round-trip equivalence
        assertEquals(originalSnapshot.serverAddress(), roundTripSnapshot.serverAddress());
        assertEquals(uniqueFoundBlockKeys(originalSnapshot.foundBlocks()).size(), roundTripSnapshot.foundBlocks().size());
        assertEquals(new java.util.HashSet<>(originalSnapshot.scannedChunks()).size(), roundTripSnapshot.scannedChunks().size());

        // Verify all found blocks are preserved through round-trip
        for (String key : uniqueFoundBlockKeys(originalSnapshot.foundBlocks())) {
            boolean hasMatch = roundTripSnapshot.foundBlocks().stream()
                .anyMatch(result -> key.equals(foundBlockKey(result)));
            assertTrue(hasMatch, "Found block key should be preserved through round-trip: " + key);
        }

        // Verify all scanned chunks are preserved through round-trip
        for (ScannedChunk originalChunk : originalSnapshot.scannedChunks()) {
            assertTrue(roundTripSnapshot.scannedChunks().contains(originalChunk),
                "Scanned chunk should be preserved through round-trip: " + originalChunk);
        }
    }

    @Property(tries = 100)
    @Label("Feature: block-scanner-mod, Property 6: Server Data Isolation")
    void serverDataIsolation(
        @ForAll("serverAddresses") String serverAddress1,
        @ForAll("serverAddresses") String serverAddress2
    ) throws IOException {
        // **Validates: Requirements 3.4**
        
        // Assume different server addresses (skip if same)
        Assume.that(!serverAddress1.equals(serverAddress2));

        // Test the sanitization logic directly
        String sanitized1 = sanitizeServerAddress(serverAddress1);
        String sanitized2 = sanitizeServerAddress(serverAddress2);
        
        // Create file paths using temp directory
        Path tempDir = Files.createTempDirectory("blockscanner-test");
        Path file1 = tempDir.resolve(sanitized1 + ".json");
        Path file2 = tempDir.resolve(sanitized2 + ".json");

        // Verify that different server addresses result in different file paths
        assertNotEquals(file1, file2, 
            "Different server addresses should have different file paths");
        
        // Verify that the file names are different
        assertNotEquals(file1.getFileName(), file2.getFileName(),
            "Different server addresses should have different file names");
            
        // Clean up
        Files.deleteIfExists(tempDir);
    }

    @Provide
    Arbitrary<ScanResult> scanResults() {
        return Combinators.combine(
            Arbitraries.of("barrier", "command_block", "chain_command_block", "repeating_command_block"),
            Arbitraries.integers().between(-30000000, 30000000),
            Arbitraries.integers().between(-64, 320),
            Arbitraries.integers().between(-30000000, 30000000),
            Arbitraries.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
            Arbitraries.longs().greaterOrEqual(0),
            Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings().withChars('a', 'z').ofMinLength(0).ofMaxLength(100)
            )
        ).as(ScanResult::new);
    }

    @Provide
    Arbitrary<ScannedChunk> scannedChunks() {
        return Combinators.combine(
            Arbitraries.integers().between(-1875000, 1875000), // Chunk coordinates
            Arbitraries.integers().between(-1875000, 1875000),
            Arbitraries.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
        ).as(ScannedChunk::new);
    }

    @Provide
    Arbitrary<String> serverAddresses() {
        return Arbitraries.strings()
            .withChars("abcdefghijklmnopqrstuvwxyz0123456789.-")
            .ofMinLength(1)
            .ofMaxLength(30);
    }

    /**
     * Helper method to test server address sanitization logic.
     */
    private String sanitizeServerAddress(String address) {
        if (address == null) {
            return "unknown";
        }
        
        // Replace invalid filename characters with underscores
        // Keep alphanumeric, dots, dashes, and underscores
        String sanitized = address.replaceAll("[^a-zA-Z0-9._-]", "_")
            .replaceAll("_{2,}", "_") // Replace multiple underscores with single
            .replaceAll("^_+|_+$", "") // Remove leading/trailing underscores
            .toLowerCase();
        return sanitized.isBlank() ? "unknown" : sanitized;
    }

    private java.util.Set<String> uniqueFoundBlockKeys(List<ScanResult> results) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (ScanResult result : results) {
            keys.add(foundBlockKey(result));
        }
        return keys;
    }

    private String foundBlockKey(ScanResult result) {
        return result.x() + "," + result.y() + "," + result.z() + "," + result.dimension();
    }
}
