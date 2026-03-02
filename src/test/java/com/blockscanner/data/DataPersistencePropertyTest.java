package com.blockscanner.data;

import com.google.gson.Gson;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Positive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        @ForAll("traversalStates") Map<String, SpiralCursorState> traversalStates,
        @ForAll @Positive long lastUpdated
    ) throws IOException {
        // **Validates: Requirements 3.2, 3.3**

        ScanDataSnapshot originalSnapshot = new ScanDataSnapshot(
            serverAddress,
            foundBlocks,
            scannedChunks,
            traversalStates,
            lastUpdated
        );

        assertEquals(serverAddress, originalSnapshot.serverAddress());
        assertEquals(foundBlocks.size(), originalSnapshot.foundBlocks().size());
        assertEquals(scannedChunks.size(), originalSnapshot.scannedChunks().size());
        assertEquals(new java.util.HashSet<>(traversalStates.entrySet()), new java.util.HashSet<>(originalSnapshot.traversalByDimension().entrySet()));
        assertEquals(lastUpdated, originalSnapshot.lastUpdated());

        ScanDataStore dataStore = new ScanDataStore();
        dataStore.loadFromSnapshot(originalSnapshot);

        ScanDataSnapshot roundTripSnapshot = dataStore.getSnapshot();

        assertEquals(originalSnapshot.serverAddress(), roundTripSnapshot.serverAddress());
        assertEquals(uniqueFoundBlockKeys(originalSnapshot.foundBlocks()).size(), roundTripSnapshot.foundBlocks().size());
        assertEquals(new java.util.HashSet<>(originalSnapshot.scannedChunks()).size(), roundTripSnapshot.scannedChunks().size());
        assertEquals(originalSnapshot.traversalByDimension(), roundTripSnapshot.traversalByDimension());

        for (String key : uniqueFoundBlockKeys(originalSnapshot.foundBlocks())) {
            boolean hasMatch = roundTripSnapshot.foundBlocks().stream()
                .anyMatch(result -> key.equals(foundBlockKey(result)));
            assertTrue(hasMatch, "Found block key should be preserved through round-trip: " + key);
        }

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

        Assume.that(!serverAddress1.equals(serverAddress2));

        String sanitized1 = sanitizeServerAddress(serverAddress1);
        String sanitized2 = sanitizeServerAddress(serverAddress2);

        Path tempDir = Files.createTempDirectory("blockscanner-test");
        Path file1 = tempDir.resolve(sanitized1 + ".json");
        Path file2 = tempDir.resolve(sanitized2 + ".json");

        assertNotEquals(file1, file2,
            "Different server addresses should have different file paths");

        assertNotEquals(file1.getFileName(), file2.getFileName(),
            "Different server addresses should have different file names");

        Files.deleteIfExists(tempDir);
    }

    @Example
    @Label("Feature: block-scanner-mod, Backward compatibility for missing traversal state")
    void missingTraversalFieldDefaultsToEmpty() {
        String legacyJson = """
            {
              "serverAddress": "singleplayer",
              "foundBlocks": [],
              "scannedChunks": [],
              "lastUpdated": 1
            }
            """;

        ScanDataSnapshot snapshot = new Gson().fromJson(legacyJson, ScanDataSnapshot.class);
        assertNotNull(snapshot);
        assertTrue(snapshot.traversalByDimension().isEmpty());
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
            Arbitraries.integers().between(-1875000, 1875000),
            Arbitraries.integers().between(-1875000, 1875000),
            Arbitraries.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
        ).as(ScannedChunk::new);
    }

    @Provide
    Arbitrary<Map<String, SpiralCursorState>> traversalStates() {
        return Arbitraries.maps(
            Arbitraries.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
            cursorStates()
        ).ofMinSize(0).ofMaxSize(3);
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

    @Provide
    Arbitrary<String> serverAddresses() {
        return Arbitraries.strings()
            .withChars("abcdefghijklmnopqrstuvwxyz0123456789.-")
            .ofMinLength(1)
            .ofMaxLength(30);
    }

    private String sanitizeServerAddress(String address) {
        if (address == null) {
            return "unknown";
        }

        String sanitized = address.replaceAll("[^a-zA-Z0-9._-]", "_")
            .replaceAll("_{2,}", "_")
            .replaceAll("^_+|_+$", "")
            .toLowerCase();
        return sanitized.isBlank() ? "unknown" : sanitized;
    }

    private Set<String> uniqueFoundBlockKeys(List<ScanResult> results) {
        Set<String> keys = new java.util.HashSet<>();
        for (ScanResult result : results) {
            keys.add(foundBlockKey(result));
        }
        return keys;
    }

    private String foundBlockKey(ScanResult result) {
        return result.x() + "," + result.y() + "," + result.z() + "," + result.dimension();
    }
}