package com.blockscanner.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage for scan results with thread-safe operations.
 * Manages found blocks and scanned chunks with persistence support.
 */
public class ScanDataStore {
    private final Map<String, ScanResult> foundBlocks = new ConcurrentHashMap<>();
    private final Set<ScannedChunk> scannedChunks = ConcurrentHashMap.newKeySet();
    private String currentServer;
    private String currentDimension;

    /**
     * Adds a found block to the data store.
     * Prevents duplicate entries for the same block position.
     * 
     * @param result The scan result to add
     */
    public void addFoundBlock(ScanResult result) {
        if (result == null) {
            return;
        }
        
        String positionKey = result.x() + "," + result.y() + "," + result.z() + "," + result.dimension();
        foundBlocks.put(positionKey, result);
    }

    /**
     * Marks a chunk as scanned in the specified dimension.
     * 
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param dimension The dimension where the chunk was scanned
     */
    public void markChunkScanned(int chunkX, int chunkZ, String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return;
        }

        ScannedChunk scannedChunk = new ScannedChunk(chunkX, chunkZ, dimension);
        scannedChunks.add(scannedChunk);
    }

    /**
     * Checks if a chunk has been scanned in the specified dimension.
     * 
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param dimension The dimension to check
     * @return true if the chunk has been scanned, false otherwise
     */
    public boolean isChunkScanned(int chunkX, int chunkZ, String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return false;
        }

        ScannedChunk targetChunk = new ScannedChunk(chunkX, chunkZ, dimension);
        return scannedChunks.contains(targetChunk);
    }

    /**
     * Gets all found blocks as a list.
     * 
     * @return A list of all found blocks
     */
    public List<ScanResult> getFoundBlocks() {
        return new ArrayList<>(foundBlocks.values());
    }

    /**
     * Gets all scanned chunks as a set.
     * 
     * @return A set of all scanned chunks
     */
    public Set<ScannedChunk> getScannedChunks() {
        return new HashSet<>(scannedChunks);
    }

    /**
     * Sets the current server address.
     * 
     * @param serverAddress The server address
     */
    public void setCurrentServer(String serverAddress) {
        this.currentServer = serverAddress;
    }

    /**
     * Gets the current server address.
     * 
     * @return The current server address
     */
    public String getCurrentServer() {
        return currentServer;
    }

    /**
     * Sets the current dimension.
     * 
     * @param dimension The current dimension
     */
    public void setCurrentDimension(String dimension) {
        this.currentDimension = dimension;
    }

    /**
     * Gets the current dimension.
     * 
     * @return The current dimension
     */
    public String getCurrentDimension() {
        return currentDimension;
    }

    /**
     * Creates a snapshot of the current data for JSON serialization.
     * 
     * @return A ScanDataSnapshot containing all current data
     */
    public ScanDataSnapshot getSnapshot() {
        return new ScanDataSnapshot(
            currentServer != null ? currentServer : "unknown",
            getFoundBlocks(),
            new ArrayList<>(scannedChunks),
            System.currentTimeMillis()
        );
    }

    /**
     * Clears all session-only data (scanned chunks).
     * Keeps persisted data (found blocks).
     */
    public void clearSessionData() {
        scannedChunks.clear();
    }

    /**
     * Clears all data.
     */
    public void clear() {
        foundBlocks.clear();
        scannedChunks.clear();
    }

    /**
     * Loads data from a snapshot.
     * 
     * @param snapshot The snapshot to load from
     */
    public void loadFromSnapshot(ScanDataSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        clear();
        this.currentServer = snapshot.serverAddress();
        
        for (ScanResult result : snapshot.foundBlocks()) {
            addFoundBlock(result);
        }
        
        scannedChunks.addAll(snapshot.scannedChunks());
    }
}
