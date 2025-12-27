package com.blockscanner.data;

/**
 * Represents a chunk that has been fully scanned, identified by its chunk coordinates and dimension.
 * 
 * @param chunkX    The chunk X coordinate (block X / 16)
 * @param chunkZ    The chunk Z coordinate (block Z / 16)
 * @param dimension The dimension where the chunk was scanned
 *                  (e.g., "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
 */
public record ScannedChunk(
    int chunkX,
    int chunkZ,
    String dimension
) {
    /**
     * Creates a ScannedChunk with validation.
     * 
     * @throws IllegalArgumentException if dimension is null or empty
     */
    public ScannedChunk {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("dimension cannot be null or empty");
        }
    }
}