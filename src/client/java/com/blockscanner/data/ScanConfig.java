package com.blockscanner.data;

import java.util.List;

/**
 * Configuration for scanning behavior.
 *
 * @param targetBlocks block ids to search for (e.g. minecraft:barrier)
 * @param rescanScannedChunks whether already scanned chunks should be scanned again
 * @param scanSigns whether to scan all signs regardless of target blocks
 */
public record ScanConfig(List<String> targetBlocks, boolean rescanScannedChunks, boolean scanSigns) {
}
