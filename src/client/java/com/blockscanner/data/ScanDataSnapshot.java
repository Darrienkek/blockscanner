package com.blockscanner.data;

import java.util.List;

/**
 * A snapshot of scan data for JSON serialization and persistence.
 * Contains all found blocks and scanned chunks for a specific server.
 * 
 * @param serverAddress  The server address this data belongs to
 * @param foundBlocks    List of all found target blocks
 * @param scannedChunks  List of all scanned chunk coordinates
 * @param lastUpdated    Unix timestamp of when this snapshot was last updated
 */
public record ScanDataSnapshot(
    String serverAddress,
    List<ScanResult> foundBlocks,
    List<ScannedChunk> scannedChunks,
    long lastUpdated
) {
    /**
     * Creates a ScanDataSnapshot with validation and defensive copies.
     * 
     * @throws IllegalArgumentException if serverAddress is null or empty
     */
    public ScanDataSnapshot {
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("serverAddress cannot be null or empty");
        }
        foundBlocks = foundBlocks == null ? List.of() : List.copyOf(foundBlocks);
        scannedChunks = scannedChunks == null ? List.of() : List.copyOf(scannedChunks);
    }
}
