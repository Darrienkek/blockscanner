package com.blockscanner;

import com.blockscanner.data.ScanDataStore;
import com.blockscanner.data.ScanResult;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Core scanning logic that iterates through chunks and identifies target blocks.
 * Handles queuing chunks for scanning and processing them asynchronously.
 */
public class EntityScanner
{
    private final ScanDataStore dataStore;
    private final Queue<ChunkPos> scanQueue = new ConcurrentLinkedQueue<>();
    private final Queue<String> announcementQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> queuedChunks = ConcurrentHashMap.newKeySet();
    private final Set<String> targetEntityIds = ConcurrentHashMap.newKeySet();
    private final Set<String> announceEntityIds = ConcurrentHashMap.newKeySet();
    private final Set<String> announcedEntityPositions = ConcurrentHashMap.newKeySet();
    private final Set<String> scannedEntityChunks = ConcurrentHashMap.newKeySet();
    private volatile boolean rescanScannedChunks = false;
    private volatile long lastAnnouncementAt = 0L;
    private static final int MAX_QUEUE_SIZE = 200;
    private static final long ANNOUNCEMENT_INTERVAL_MS = 3_000L;
    private static final int MAX_ANNOUNCEMENT_QUEUE_SIZE = 256;

    public EntityScanner(ScanDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public ScanDataStore getDataStore() {
        return dataStore;
    }

    public synchronized String setTargetEntities(List<String> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return "targetEntities must include at least one entity id";
        }

        ValidationResult validation = validateEntityIds(entityIds);
        if (validation.errorMessage() != null) {
            return validation.errorMessage();
        }
        if (validation.resolvedBlockIds().isEmpty()) {
            return "targetEntities must include at least one valid entity id";
        }

        targetEntityIds.clear();
        targetEntityIds.addAll(validation.resolvedBlockIds());
        return null;
    }

    public synchronized String setAnnounceEntities(List<String> entityIds) {
        if (entityIds == null) {
            announceEntityIds.clear();
            return null;
        }

        ValidationResult validation = validateEntityIds(entityIds);
        if (validation.errorMessage() != null) {
            return validation.errorMessage();
        }

        announceEntityIds.clear();
        announceEntityIds.addAll(validation.resolvedBlockIds());
        return null;
    }

    public void setRescanScannedChunks(boolean rescanScannedChunks) {
        this.rescanScannedChunks = rescanScannedChunks;
    }


    public boolean isTargetEntity(Entity entity) {
        if (entity == null) {
            return false;
        }

        String blockId = geyEntityTypeName(entity);
        return targetEntityIds.contains(blockId);
    }

    public void queueChunksInWindow(World world, int centerChunkX, int centerChunkZ, int radius) {
        if (world == null || radius < 0) {
            return;
        }

        String dimension = world.getRegistryKey().getValue().toString();
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                if (scanQueue.size() >= MAX_QUEUE_SIZE) {
                    return;
                }
                if (!rescanScannedChunks && scannedEntityChunks.contains(chunkKey(dimension, x, z))) {
                    continue;
                }
                String key = chunkKey(dimension, x, z);
                if (queuedChunks.add(key)) {
                    scanQueue.offer(new ChunkPos(x, z));
                }
            }
        }
    }

    public void processScanQueue(World world, int maxChunksPerTick) {
        if (world == null || maxChunksPerTick <= 0) {
            return;
        }

        int chunksProcessed = 0;
        while (!scanQueue.isEmpty() && chunksProcessed < maxChunksPerTick) {
            ChunkPos pos = scanQueue.poll();
            if (pos != null) {
                String dimension = world.getRegistryKey().getValue().toString();
                queuedChunks.remove(chunkKey(dimension, pos.x, pos.z));
                scanChunk(world, pos);
                chunksProcessed++;
            }
        }
    }

    public void scanChunk(World world, ChunkPos pos) {

        if (world == null || pos == null) {
            return;
        }

        String dimension = world.getRegistryKey().getValue().toString();
        String key = chunkKey(dimension, pos.x, pos.z);
        if (!rescanScannedChunks && scannedEntityChunks.contains(key)) {
            return;
        }

        Chunk chunk = world.getChunkManager().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (chunk == null) {
            dataStore.markChunkSkipped(pos.x, pos.z, dimension);
            return;
        }

        Box chunkBox = new Box(pos.getStartX(), world.getBottomY(), pos.getStartZ(), pos.getEndX() + 1, 512, pos.getEndZ() + 1);
        List<Entity> entities = world.getOtherEntities(null, chunkBox, entity -> true);

        if (!entities.isEmpty())
        {
            for (Entity entity : entities)
            {
                if (isTargetEntity(entity))
                {
                    String entityType = geyEntityTypeName(entity);
                    ScanResult result = new ScanResult(entityType, entity.getBlockPos().getX(), entity.getBlockPos().getY(), entity.getBlockPos().getZ(), dimension, System.currentTimeMillis(), "");
                    dataStore.addFoundEntity(result);
                    sendFoundEntityMessage(entityType, entity.getBlockPos());
                    queueEntityAnnouncement(entityType, entity.getBlockPos(), dimension);
                }
            }
        }

        scannedEntityChunks.add(key);
        dataStore.markChunkScanned(pos.x, pos.z, dimension);
    }

    private String geyEntityTypeName(Entity entity) {
        if (entity == null) {
            return "unknown";
        }
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        return id != null ? id.toString() : "unknown";
    }

    private void sendFoundEntityMessage(String entityType, BlockPos blockPos) {
        if (entityType == null || blockPos == null) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null) {
            String message = "[Entity Scanner] Found " + entityType + " at " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ();
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    public void processAnnouncementQueue() {
        long now = System.currentTimeMillis();
        if (now - lastAnnouncementAt < ANNOUNCEMENT_INTERVAL_MS) {
            return;
        }

        String nextMessage = announcementQueue.poll();
        if (nextMessage == null) {
            return;
        }

        if (sendServerChatMessage(nextMessage)) {
            lastAnnouncementAt = now;
        }
    }

    public void clearQueueOnly() {
        scanQueue.clear();
        queuedChunks.clear();
    }

    public void reset() {
        clearQueueOnly();
        announcementQueue.clear();
        announcedEntityPositions.clear();
        scannedEntityChunks.clear();
        dataStore.clearSessionData();
    }

    public void clearAllData() {
        clearQueueOnly();
        announcementQueue.clear();
        announcedEntityPositions.clear();
        scannedEntityChunks.clear();
        dataStore.clear();
    }

    private String chunkKey(String dimension, int chunkX, int chunkZ) {
        return dimension + ":" + chunkX + "," + chunkZ;
    }

    private void queueEntityAnnouncement(String entityType, BlockPos blockPos, String dimension) {
        if (entityType == null || blockPos == null || dimension == null) {
            return;
        }
        if (!announceEntityIds.contains(entityType)) {
            return;
        }

        String positionKey = blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ() + "," + dimension;
        if (!announcedEntityPositions.add(positionKey)) {
            return;
        }
        if (announcementQueue.size() >= MAX_ANNOUNCEMENT_QUEUE_SIZE) {
            return;
        }

        String message = "Found " + entityType + " at " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ();
        announcementQueue.offer(message);
    }

    private boolean sendServerChatMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            return false;
        }

        client.getNetworkHandler().sendChatMessage(message);
        return true;
    }

    private ValidationResult validateEntityIds(List<String> entityIds) {
        Set<String> resolved = ConcurrentHashMap.newKeySet();
        List<String> invalidIds = new ArrayList<>();
        for (String blockId : entityIds) {
            if (blockId == null || blockId.isBlank()) {
                continue;
            }
            Identifier identifier = Identifier.tryParse(blockId.trim());
            if (identifier == null) {
                invalidIds.add(blockId);
                continue;
            }
            resolved.add(identifier.toString());
        }
        if (!invalidIds.isEmpty()) {
            return new ValidationResult(Set.of(), "Unknown entity id(s): " + String.join(", ", invalidIds));
        }
        return new ValidationResult(resolved, null);
    }

    private record ValidationResult(Set<String> resolvedBlockIds, String errorMessage) {
    }
}
