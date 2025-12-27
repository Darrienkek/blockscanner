package com.blockscanner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import com.blockscanner.data.ScanConfig;
import net.minecraft.util.math.ChunkPos;

/**
 * Manages the scanning state and keybind handling.
 * Controls when the BlockScanner is active and processes scan operations.
 */
public class ScanController {
    private boolean scanningActive = false;
    private final BlockScanner blockScanner;
    private static KeyBinding toggleKey;
    
    private static final int MAX_CHUNKS_PER_TICK = 2;
    private static final int QUEUE_INTERVAL_TICKS = 10;
    private static final int NEARBY_QUEUE_RADIUS = 5;
    private static final int DEFAULT_SEARCH_AREA_BLOCKS = 10000;
    private static final int MIN_SEARCH_AREA_BLOCKS = 16;
    private static final int MAX_SEARCH_AREA_BLOCKS = 200000;
    private int queueCooldownTicks = 0;
    private int searchAreaBlocks = DEFAULT_SEARCH_AREA_BLOCKS;
    private SearchPattern searchPattern;
    private ChunkPos searchOriginChunk = new ChunkPos(0, 0);
    private ChunkPos lastTargetChunk;
    
    public ScanController(BlockScanner blockScanner) {
        this.blockScanner = blockScanner;
        this.searchPattern = new SearchPattern(getSearchAreaChunkRadius());
    }
    
    /**
     * Toggles the scanning state between active and inactive.
     * Displays appropriate chat messages to inform the player.
     */
    public void toggle() {
        scanningActive = !scanningActive;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            if (scanningActive) {
                searchOriginChunk = client.player.getChunkPos();
                searchPattern.reset();
                queueCooldownTicks = 0;
                lastTargetChunk = null;
                client.player.sendMessage(Text.literal("§a[Block Scanner] Scanning started"), false);
            } else {
                client.player.sendMessage(Text.literal("§c[Block Scanner] Scanning stopped"), false);
            }
        }
    }
    
    /**
     * Checks if scanning is currently active.
     * 
     * @return true if scanning is active, false otherwise
     */
    public boolean isActive() {
        return scanningActive;
    }
    
    /**
     * Processes client tick events when scanning is active.
     * Queues chunks for scanning and processes the scan queue.
     * 
     * @param client The Minecraft client instance
     */
    public void onClientTick(MinecraftClient client) {
        if (!scanningActive || client.world == null || client.player == null) {
            return;
        }
        
        String currentDimension = client.world.getRegistryKey().getValue().toString();
        String previousDimension = blockScanner.getDataStore().getCurrentDimension();
        if (!currentDimension.equals(previousDimension)) {
            BlockScannerMod.LOGGER.info("Dimension changed from {} to {}", previousDimension, currentDimension);
        }
        blockScanner.getDataStore().setCurrentDimension(currentDimension);

        blockScanner.queueChunksAroundPosition(
            client.world,
            client.player.getBlockPos(),
            NEARBY_QUEUE_RADIUS
        );

        ChunkPos playerChunk = client.player.getChunkPos();
        if (queueCooldownTicks <= 0) {
            boolean shouldAdvance = false;
            synchronized (this) {
                if (lastTargetChunk == null) {
                    shouldAdvance = true;
                } else if (lastTargetChunk.x == playerChunk.x && lastTargetChunk.z == playerChunk.z) {
                    shouldAdvance = true;
                }
            }

            if (shouldAdvance) {
                ChunkPos targetOffset;
                ChunkPos origin;
                synchronized (this) {
                    targetOffset = searchPattern.next();
                    origin = searchOriginChunk;
                }
                if (targetOffset == null) {
                    scanningActive = false;
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("§e[Block Scanner] Search complete"), false);
                    }
                    return;
                }
                ChunkPos targetChunk = new ChunkPos(origin.x + targetOffset.x, origin.z + targetOffset.z);
                synchronized (this) {
                    lastTargetChunk = targetChunk;
                }
            }
            queueCooldownTicks = QUEUE_INTERVAL_TICKS;
        } else {
            queueCooldownTicks--;
        }
        
        blockScanner.processScanQueue(client.world, MAX_CHUNKS_PER_TICK);
    }
    
    /**
     * Registers the keybind for toggling scanning.
     * Uses Numpad 5 as the default key.
     */
    public static void registerKeybind() {
        toggleKey = null;
        BlockScannerMod.LOGGER.warn("KeyBinding registration disabled - keybind functionality not available");
    }
    
    /**
     * Gets the registered toggle keybind.
     * 
     * @return The toggle keybind
     */
    public static KeyBinding getToggleKey() {
        return toggleKey;
    }

    public synchronized int getSearchAreaBlocks() {
        return searchAreaBlocks;
    }

    public synchronized int getSearchAreaChunkRadius() {
        int halfBlocks = Math.max(0, searchAreaBlocks) / 2;
        return Math.floorDiv(halfBlocks, 16);
    }

    public synchronized int getMinSearchAreaBlocks() {
        return MIN_SEARCH_AREA_BLOCKS;
    }

    public synchronized int getMaxSearchAreaBlocks() {
        return MAX_SEARCH_AREA_BLOCKS;
    }

    public synchronized ScanConfig getConfigSnapshot() {
        return new ScanConfig(searchAreaBlocks);
    }

    public synchronized ChunkPos getSearchOriginChunk() {
        return searchOriginChunk;
    }

    public synchronized ChunkPos getLastTargetChunk() {
        return lastTargetChunk;
    }

    public synchronized String updateConfig(Integer searchAreaBlocks) {
        if (searchAreaBlocks != null) {
            if (searchAreaBlocks < MIN_SEARCH_AREA_BLOCKS || searchAreaBlocks > MAX_SEARCH_AREA_BLOCKS) {
                return "searchAreaBlocks must be between " + MIN_SEARCH_AREA_BLOCKS + " and " + MAX_SEARCH_AREA_BLOCKS;
            }
            if (searchAreaBlocks % 16 != 0) {
                return "searchAreaBlocks must be a multiple of 16";
            }
        }

        boolean updated = false;
        if (searchAreaBlocks != null) {
            this.searchAreaBlocks = searchAreaBlocks;
            updated = true;
        }
        if (updated) {
            this.searchPattern = new SearchPattern(getSearchAreaChunkRadius());
            this.searchPattern.reset();
            queueCooldownTicks = 0;
            lastTargetChunk = null;
        }
        return null;
    }
}
