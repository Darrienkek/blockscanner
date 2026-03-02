package com.blockscanner;

import com.blockscanner.data.ScanConfig;
import com.blockscanner.data.SpiralCursorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Manages the scanning state and keybind handling.
 * Controls when the BlockScanner is active and processes scan operations.
 */
public class ScanController {
    private volatile boolean scanningActive = false;
    private final BlockScanner blockScanner;
    private final EntityScanner entityScanner;
    private static KeyBinding toggleKey;

    private static final int MAX_CHUNKS_PER_TICK = 2;
    private static final long SPECTATOR_WARNING_INTERVAL_MS = 5_000L;
    private static final long PROGRESS_ANNOUNCEMENT_INTERVAL_MS = 10 * 60 * 1000L;
    private static final double WAYPOINT_ARRIVAL_DISTANCE = 1.25D;
    private static final List<String> DEFAULT_TARGET_BLOCKS = List.of(
        "minecraft:barrier",
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block"
    );

    private static final List<String> DEFAULT_TARGET_ENTITIES = List.of(
            "minecraft:block_display",
            "minecraft:item_display",
            "minecraft:text_display",
            "minecraft:item_frame",
            "minecraft:glow_item_frame"
    );
    private static final List<String> DEFAULT_ANNOUNCE_BLOCKS = List.of();

    private List<String> targetBlocks = new ArrayList<>(DEFAULT_TARGET_BLOCKS);
    private List<String> targetEntites = new ArrayList<>(DEFAULT_TARGET_ENTITIES);
    private List<String> announceBlocks = new ArrayList<>(DEFAULT_ANNOUNCE_BLOCKS);
    private boolean rescanScannedChunks = false;
    private boolean scanSigns = false;

    private String lastTraversalDimension;
    private long lastSpectatorWarningAt;
    private long lastProgressAnnouncementAt;

    private volatile boolean waitingForSpectator;
    private volatile String traversalDimension = "unknown";
    private volatile int traversalWaypointGridX;
    private volatile int traversalWaypointGridZ;
    private volatile int traversalCenterChunkX;
    private volatile int traversalCenterChunkZ;
    private volatile String traversalDirection = "+X";
    private volatile long traversalWaypointsCompleted;
    private volatile int traversalBatchChunksScanned;

    public ScanController(BlockScanner blockScanner, EntityScanner entityScanner) {
        this.blockScanner = blockScanner;
        this.blockScanner.setTargetBlocks(targetBlocks);
        this.blockScanner.setAnnounceBlocks(announceBlocks);
        this.blockScanner.setRescanScannedChunks(rescanScannedChunks);
        this.blockScanner.setScanSigns(scanSigns);

        this.entityScanner = entityScanner;
        this.entityScanner.setTargetEntities(targetEntites);
        this.entityScanner.setAnnounceEntities(targetEntites);
        this.entityScanner.setRescanScannedChunks(rescanScannedChunks);
    }

    /**
     * Toggles the scanning state between active and inactive.
     * Displays appropriate chat messages to inform the player.
     */
    public void toggle() {
        scanningActive = !scanningActive;

        MinecraftClient client = MinecraftClient.getInstance();
        if (!scanningActive) {
            blockScanner.clearQueueOnly();
            entityScanner.clearQueueOnly();
            waitingForSpectator = false;
            releaseMovementKeys(client);
            lastProgressAnnouncementAt = 0L;
        } else {
            lastProgressAnnouncementAt = System.currentTimeMillis();
            sendServerChatMessage("Auto scan started");
        }

        if (client.player != null) {
            if (scanningActive) {
                client.player.sendMessage(Text.literal("[Block Scanner] Scanning started"), false);
            } else {
                client.player.sendMessage(Text.literal("[Block Scanner] Scanning stopped"), false);
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
            waitingForSpectator = false;
            releaseMovementKeys(client);
            return;
        }

        blockScanner.processAnnouncementQueue();
        entityScanner.processAnnouncementQueue();
        maybeAnnounceProgress(client);

        String currentDimension = client.world.getRegistryKey().getValue().toString();
        blockScanner.getDataStore().setCurrentDimension(currentDimension);
        entityScanner.getDataStore().setCurrentDimension(currentDimension);

        if (!currentDimension.equals(lastTraversalDimension)) {
            blockScanner.clearQueueOnly();
            entityScanner.clearQueueOnly();
            lastTraversalDimension = currentDimension;
        }

        SpiralCursorState cursor = blockScanner.getDataStore().getOrCreateTraversalState(currentDimension);
        SpiralTraversal.ChunkCoordinate centerChunk = SpiralTraversal.currentCenterChunk(cursor);

        if (!client.player.isSpectator()) {
            waitingForSpectator = true;
            releaseMovementKeys(client);
            maybeWarnSpectatorRequired(client);
            int batchChunksScanned = countBatchScanned(currentDimension, centerChunk.chunkX(), centerChunk.chunkZ());
            updateTraversalStatus(currentDimension, cursor, centerChunk, batchChunksScanned);
            return;
        }

        waitingForSpectator = false;
        movePlayerTowardWaypoint(client, centerChunk.chunkX(), centerChunk.chunkZ());

        blockScanner.queueChunksInWindow(
            client.world,
            centerChunk.chunkX(),
            centerChunk.chunkZ(),
            SpiralTraversal.BATCH_RADIUS
        );

        entityScanner.queueChunksInWindow(
                client.world,
                centerChunk.chunkX(),
                centerChunk.chunkZ(),
                SpiralTraversal.BATCH_RADIUS
        );
        blockScanner.processScanQueue(client.world, MAX_CHUNKS_PER_TICK);
        entityScanner.processScanQueue(client.world, MAX_CHUNKS_PER_TICK);

        int batchChunksScanned = countBatchScanned(currentDimension, centerChunk.chunkX(), centerChunk.chunkZ());
        if (isBatchComplete(batchChunksScanned)) {
            SpiralCursorState nextCursor = SpiralTraversal.advance(cursor);
            blockScanner.getDataStore().updateTraversalState(currentDimension, nextCursor);
            entityScanner.getDataStore().updateTraversalState(currentDimension, nextCursor);
            blockScanner.clearQueueOnly();
            entityScanner.clearQueueOnly();
            SpiralTraversal.ChunkCoordinate nextCenter = SpiralTraversal.currentCenterChunk(nextCursor);
            updateTraversalStatus(currentDimension, nextCursor, nextCenter, 0);
            return;
        }

        updateTraversalStatus(currentDimension, cursor, centerChunk, batchChunksScanned);
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

    public synchronized ScanConfig getConfigSnapshot() {
        return new ScanConfig(List.copyOf(targetBlocks), List.copyOf(announceBlocks), rescanScannedChunks, scanSigns);
    }

    public synchronized List<String> getTargetBlocks() {
        return List.copyOf(targetBlocks);
    }

    public synchronized List<String> getAnnounceBlocks() {
        return List.copyOf(announceBlocks);
    }

    public synchronized boolean isRescanScannedChunks() {
        return rescanScannedChunks;
    }

    public synchronized boolean isScanSigns() {
        return scanSigns;
    }

    public synchronized String updateConfig(
        List<String> targetBlocks,
        List<String> announceBlocks,
        Boolean rescanScannedChunks,
        Boolean scanSigns
    ) {
        if (targetBlocks != null) {
            List<String> normalized = normalizeBlockIds(targetBlocks);
            if (normalized.isEmpty()) {
                return "targetBlocks must include at least one block id";
            }
            String error = blockScanner.setTargetBlocks(normalized);
            if (error != null) {
                return error;
            }
            this.targetBlocks = new ArrayList<>(normalized);
        }

        if (announceBlocks != null) {
            List<String> normalizedAnnounce = normalizeBlockIds(announceBlocks);
            String error = blockScanner.setAnnounceBlocks(normalizedAnnounce);
            if (error != null) {
                return error;
            }
            this.announceBlocks = new ArrayList<>(normalizedAnnounce);
        }

        if (rescanScannedChunks != null) {
            this.rescanScannedChunks = rescanScannedChunks;
            blockScanner.setRescanScannedChunks(rescanScannedChunks);
        }

        if (scanSigns != null) {
            this.scanSigns = scanSigns;
            blockScanner.setScanSigns(scanSigns);
        }

        return null;
    }

    public void clearAllData() {
        blockScanner.clearAllData();
        entityScanner.clearAllData();
    }

    public TraversalStatus getTraversalStatusSnapshot() {
        return new TraversalStatus(
            scanningActive,
            waitingForSpectator,
            SpiralTraversal.BATCH_SIDE,
            SpiralTraversal.BATCH_TOTAL,
            traversalDimension,
            traversalWaypointGridX,
            traversalWaypointGridZ,
            traversalCenterChunkX,
            traversalCenterChunkZ,
            traversalDirection,
            traversalWaypointsCompleted,
            traversalBatchChunksScanned
        );
    }

    public static record TraversalStatus(
        boolean enabled,
        boolean waitingForSpectator,
        int batchSideChunks,
        int batchTotalChunks,
        String currentDimension,
        int waypointGridX,
        int waypointGridZ,
        int waypointCenterChunkX,
        int waypointCenterChunkZ,
        String direction,
        long waypointsCompleted,
        int batchChunksScanned
    ) {
    }

    private void updateTraversalStatus(
        String dimension,
        SpiralCursorState cursor,
        SpiralTraversal.ChunkCoordinate center,
        int batchChunksScanned
    ) {
        traversalDimension = dimension;
        traversalWaypointGridX = cursor.waypointGridX();
        traversalWaypointGridZ = cursor.waypointGridZ();
        traversalCenterChunkX = center.chunkX();
        traversalCenterChunkZ = center.chunkZ();
        traversalDirection = SpiralTraversal.directionName(cursor.directionIndex());
        traversalWaypointsCompleted = cursor.waypointsCompleted();
        traversalBatchChunksScanned = Math.max(0, Math.min(batchChunksScanned, SpiralTraversal.BATCH_TOTAL));
    }

    private int countBatchScanned(String dimension, int centerChunkX, int centerChunkZ) {
        int scanned = 0;
        for (SpiralTraversal.ChunkCoordinate chunk : SpiralTraversal.enumerateBatchChunks(centerChunkX, centerChunkZ)) {
            if (blockScanner.getDataStore().isChunkScanned(chunk.chunkX(), chunk.chunkZ(), dimension)) {
                scanned++;
            }
        }
        return scanned;
    }

    private void maybeWarnSpectatorRequired(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (!shouldWarnSpectator(now, lastSpectatorWarningAt, SPECTATOR_WARNING_INTERVAL_MS)) {
            return;
        }
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Block Scanner] Auto movement paused: switch to spectator mode."), false);
        }
        lastSpectatorWarningAt = now;
    }

    private void movePlayerTowardWaypoint(MinecraftClient client, int waypointCenterChunkX, int waypointCenterChunkZ) {
        if (client == null || client.player == null) {
            return;
        }

        double targetX = waypointCenterChunkX * 16.0D + 8.5D;
        double targetZ = waypointCenterChunkZ * 16.0D + 8.5D;
        double deltaX = targetX - client.player.getX();
        double deltaZ = targetZ - client.player.getZ();
        double distanceSquared = (deltaX * deltaX) + (deltaZ * deltaZ);

        if (distanceSquared <= WAYPOINT_ARRIVAL_DISTANCE * WAYPOINT_ARRIVAL_DISTANCE) {
            releaseMovementKeys(client);
            return;
        }

        float yaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D);
        client.player.setYaw(yaw);
        client.player.setHeadYaw(yaw);

        client.options.forwardKey.setPressed(true);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.sprintKey.setPressed(true);
    }

    private void releaseMovementKeys(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
    }


    static boolean isBatchComplete(int scannedChunks) {
        return scannedChunks >= SpiralTraversal.BATCH_TOTAL;
    }

    static boolean shouldWarnSpectator(long now, long lastWarningAt, long intervalMs) {
        return now - lastWarningAt >= intervalMs;
    }

    private void maybeAnnounceProgress(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (!shouldWarnSpectator(now, lastProgressAnnouncementAt, PROGRESS_ANNOUNCEMENT_INTERVAL_MS)) {
            return;
        }
        if (client == null || client.player == null) {
            return;
        }

        int totalChunks = blockScanner.getDataStore().getScannedChunks().size();
        int currentX = client.player.getBlockX();
        int currentZ = client.player.getBlockZ();
        String message = "I've scanned total " + totalChunks + ", I'm currently at " + currentX + "," + currentZ + ".";
        if (sendServerChatMessage(message)) {
            lastProgressAnnouncementAt = now;
        }
    }

    private boolean sendServerChatMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null || client.player == null) {
            return false;
        }

        client.getNetworkHandler().sendChatMessage(message);
        return true;
    }

    private List<String> normalizeBlockIds(List<String> rawBlockIds) {
        Set<String> normalized = new LinkedHashSet<>();
        if (rawBlockIds == null) {
            return List.of();
        }
        for (String raw : rawBlockIds) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lowered = trimmed.toLowerCase(Locale.ROOT);
            if (!lowered.contains(":")) {
                lowered = "minecraft:" + lowered;
            }
            normalized.add(lowered);
        }
        return new ArrayList<>(normalized);
    }
}
