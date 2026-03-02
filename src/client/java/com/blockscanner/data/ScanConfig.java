package com.blockscanner.data;

import java.util.List;

/**
 * Configuration for scanning behavior.
 *
 * @param targetBlocks block ids to search for (e.g. minecraft:barrier)
 * @param announceBlocks block ids to announce in server chat when found
 * @param rescanScannedChunks whether already scanned chunks should be scanned again
 * @param scanSigns whether to scan all signs regardless of target blocks
 * @param chunksPerTick chunks processed per scanner per client tick
 * @param batchRadius chunk radius around each waypoint (1=3x3, 2=5x5, 3=7x7)
 * @param scanBlocksEnabled whether block scanning is enabled
 * @param scanEntitiesEnabled whether entity scanning is enabled
 */
public record ScanConfig(
    List<String> targetBlocks,
    List<String> announceBlocks,
    boolean rescanScannedChunks,
    boolean scanSigns,
    Integer chunksPerTick,
    Integer batchRadius,
    Boolean scanBlocksEnabled,
    Boolean scanEntitiesEnabled
) {
}
