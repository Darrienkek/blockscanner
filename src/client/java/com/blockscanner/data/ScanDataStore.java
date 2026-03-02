package com.blockscanner.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage for scan results with thread-safe operations.
 * Manages found blocks and scanned chunks with persistence support.
 */
public class ScanDataStore {
    private final Map<String, ScanResult> foundBlocks = new ConcurrentHashMap<>();
    private final Map<String, ScanResult> foundEntities =  new ConcurrentHashMap<>();
    private final Set<ScannedChunk> scannedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ScannedChunk> skippedChunks = ConcurrentHashMap.newKeySet();
    private final Map<String, SpiralCursorState> traversalByDimension = new ConcurrentHashMap<>();
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

    public void addFoundEntity(ScanResult result) {
        if (result == null) {
            return;
        }

        String positionKey = result.x() + "," + result.y() + "," + result.z() + "," + result.dimension();
        foundEntities.put(positionKey, result);
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
        skippedChunks.remove(scannedChunk);
    }

    /**
     * Marks a chunk as skipped because it was not loaded when scanning was attempted.
     *
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param dimension The dimension where the chunk was skipped
     */
    public void markChunkSkipped(int chunkX, int chunkZ, String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return;
        }

        ScannedChunk skippedChunk = new ScannedChunk(chunkX, chunkZ, dimension);
        if (!scannedChunks.contains(skippedChunk)) {
            skippedChunks.add(skippedChunk);
        }
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
     * Checks if a chunk was skipped because it was not loaded.
     *
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param dimension The dimension to check
     * @return true if the chunk was skipped, false otherwise
     */
    public boolean isChunkSkipped(int chunkX, int chunkZ, String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return false;
        }

        ScannedChunk targetChunk = new ScannedChunk(chunkX, chunkZ, dimension);
        return skippedChunks.contains(targetChunk);
    }

    /**
     * Gets all found blocks as a list.
     *
     * @return A list of all found blocks
     */
    public List<ScanResult> getFoundBlocks() {
        return new ArrayList<>(foundBlocks.values());
    }

    public List<ScanResult> getFoundEntities() {
        return new ArrayList<>(foundEntities.values());
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
     * Gets all skipped chunks as a set.
     *
     * @return A set of all skipped chunks
     */
    public Set<ScannedChunk> getSkippedChunks() {
        return new HashSet<>(skippedChunks);
    }

    /**
     * Gets a traversal state for the given dimension, creating one if missing.
     *
     * @param dimension dimension id
     * @return traversal state
     */
    public SpiralCursorState getOrCreateTraversalState(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("dimension cannot be null or empty");
        }
        return traversalByDimension.computeIfAbsent(dimension, ignored -> SpiralCursorState.initial());
    }

    /**
     * Updates traversal state for a dimension.
     *
     * @param dimension dimension id
     * @param state traversal cursor
     */
    public void updateTraversalState(String dimension, SpiralCursorState state) {
        if (dimension == null || dimension.isBlank() || state == null) {
            return;
        }
        traversalByDimension.put(dimension, state);
    }

    /**
     * Gets traversal state for a dimension.
     *
     * @param dimension dimension id
     * @return traversal cursor or null if none exists
     */
    public SpiralCursorState getTraversalState(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return null;
        }
        return traversalByDimension.get(dimension);
    }

    /**
     * Gets traversal states across all dimensions.
     *
     * @return copy of traversal state map
     */
    public Map<String, SpiralCursorState> getTraversalStates() {
        return new HashMap<>(traversalByDimension);
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
            getTraversalStates(),
            System.currentTimeMillis()
        );
    }

    /**
     * Clears all session-only data (scanned chunks).
     * Keeps persisted data (found blocks and traversal state).
     */
    public void clearSessionData() {
        scannedChunks.clear();
        skippedChunks.clear();
    }

    /**
     * Clears all data.
     */
    public void clear() {
        foundBlocks.clear();
        scannedChunks.clear();
        skippedChunks.clear();
        traversalByDimension.clear();
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
        traversalByDimension.putAll(snapshot.traversalByDimension());
    }
}