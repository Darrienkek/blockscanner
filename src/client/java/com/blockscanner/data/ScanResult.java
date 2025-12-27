package com.blockscanner.data;

/**
 * Represents a found target block (barrier or command block) with its location.
 * 
 * @param blockType  The type of block found (e.g., "barrier", "command_block", 
 *                   "chain_command_block", "repeating_command_block")
 * @param x          The x coordinate of the block
 * @param y          The y coordinate of the block
 * @param z          The z coordinate of the block
 * @param dimension  The dimension where the block was found 
 *                   (e.g., "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
 * @param timestamp  The Unix timestamp when the block was found
 * @param signText   The text on a sign block, if applicable
 */
public record ScanResult(
    String blockType,
    int x,
    int y,
    int z,
    String dimension,
    long timestamp,
    String signText
) {
    /**
     * Creates a ScanResult with validation.
     * 
     * @throws IllegalArgumentException if blockType or dimension is null or empty
     */
    public ScanResult {
        if (blockType == null || blockType.isBlank()) {
            throw new IllegalArgumentException("blockType cannot be null or empty");
        }
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("dimension cannot be null or empty");
        }
    }
}
