package com.blockscanner;

import com.blockscanner.data.ScanDataStore;
import com.blockscanner.data.ScanResult;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.Chunk;

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
public class BlockScanner {
    private final ScanDataStore dataStore;
    private final Queue<ChunkPos> scanQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> queuedChunks = ConcurrentHashMap.newKeySet();
    private final Set<String> targetBlockIds = ConcurrentHashMap.newKeySet();
    private volatile boolean rescanScannedChunks = false;
    private volatile boolean scanSigns = false;
    private static final int MAX_QUEUE_SIZE = 200;
    
    public BlockScanner(ScanDataStore dataStore) {
        this.dataStore = dataStore;
    }
    
    /**
     * Gets the data store instance.
     * 
     * @return The data store
     */
    public ScanDataStore getDataStore() {
        return dataStore;
    }

    public synchronized String setTargetBlocks(List<String> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return "targetBlocks must include at least one block id";
        }

        Set<String> resolved = ConcurrentHashMap.newKeySet();
        List<String> invalidIds = new ArrayList<>();
        for (String blockId : blockIds) {
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
            return "Unknown block id(s): " + String.join(", ", invalidIds);
        }

        if (resolved.isEmpty()) {
            return "targetBlocks must include at least one valid block id";
        }

        targetBlockIds.clear();
        targetBlockIds.addAll(resolved);
        return null;
    }

    public void setRescanScannedChunks(boolean rescanScannedChunks) {
        this.rescanScannedChunks = rescanScannedChunks;
    }

    public void setScanSigns(boolean scanSigns) {
        this.scanSigns = scanSigns;
    }
    
    /**
     * Checks if a block is a target block.
     * 
     * @param block The block to check
     * @return true if the block is a configured target block, false otherwise
     */
    public boolean isTargetBlock(Block block) {
        if (block == null) {
            return false;
        }

        if (scanSigns && isSignBlock(block)) {
            return true;
        }
        
        String blockId = getBlockTypeName(block);
        return targetBlockIds.contains(blockId);
    }
    
    /**
     * Queues all loaded chunks for scanning that haven't been scanned yet.
     * 
     * @param world The world to scan chunks from
     */
    public void queueChunksForScanning(World world) {
        if (world == null) {
            return;
        }
        
    }
    
    /**
     * Queues chunks around a specific position for scanning.
     * 
     * @param world The world to scan chunks from
     * @param centerPos The center position to scan around
     * @param radius The radius in chunks to scan around the center
     */
    public void queueChunksAroundPosition(World world, BlockPos centerPos, int radius) {
        if (world == null || centerPos == null || radius < 0) {
            return;
        }
        
        String dimension = world.getRegistryKey().getValue().toString();
        ChunkPos centerChunk = new ChunkPos(centerPos);
        
        for (int x = centerChunk.x - radius; x <= centerChunk.x + radius; x++) {
            for (int z = centerChunk.z - radius; z <= centerChunk.z + radius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                if (scanQueue.size() >= MAX_QUEUE_SIZE) {
                    return;
                }
                if (!rescanScannedChunks && dataStore.isChunkScanned(x, z, dimension)) {
                    continue;
                }
                {
                    String key = dimension + ":" + x + "," + z;
                    if (queuedChunks.add(key)) {
                        scanQueue.offer(pos);
                    }
                }
            }
        }
    }
    
    /**
     * Processes the scan queue, scanning up to maxChunksPerTick chunks.
     * 
     * @param world The world to scan chunks in
     * @param maxChunksPerTick Maximum number of chunks to scan per tick to avoid lag
     */
    public void processScanQueue(World world, int maxChunksPerTick) {
        if (world == null || maxChunksPerTick <= 0) {
            return;
        }
        
        int chunksProcessed = 0;
        while (!scanQueue.isEmpty() && chunksProcessed < maxChunksPerTick) {
            ChunkPos pos = scanQueue.poll();
            if (pos != null) {
                String dimension = world.getRegistryKey().getValue().toString();
                queuedChunks.remove(dimension + ":" + pos.x + "," + pos.z);
                scanChunk(world, pos);
                chunksProcessed++;
            }
        }
    }
    
    /**
     * Scans a single chunk for target blocks, iterating from y 320 down to y -64.
     * 
     * @param world The world containing the chunk
     * @param pos The chunk position to scan
     */
    public void scanChunk(World world, ChunkPos pos) {
        if (world == null || pos == null) {
            return;
        }
        
        String dimension = world.getRegistryKey().getValue().toString();
        
        if (!rescanScannedChunks && dataStore.isChunkScanned(pos.x, pos.z, dimension)) {
            return;
        }
        
        Chunk chunk = world.getChunkManager().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (chunk == null) {
            BlockScannerMod.LOGGER.info("Skipping chunk {} (not loaded yet)", pos);
            dataStore.markChunkSkipped(pos.x, pos.z, dimension);
            return;
        }
        
        int blocksFound = 0;
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 320; y >= -64; y--) {
                    BlockPos blockPos = new BlockPos(pos.getStartX() + x, y, pos.getStartZ() + z);
                    Block block = world.getBlockState(blockPos).getBlock();
                    
                    if (isTargetBlock(block)) {
                        String blockType = getBlockTypeName(block);
                        String signText = getSignText(world, blockPos);
                        ScanResult result = new ScanResult(
                            blockType,
                            blockPos.getX(),
                            blockPos.getY(),
                            blockPos.getZ(),
                            dimension,
                            System.currentTimeMillis(),
                            signText
                        );
                        
                        dataStore.addFoundBlock(result);
                        blocksFound++;
                        BlockScannerMod.LOGGER.info("Found {} at {}, {}, {} in {}", blockType, blockPos.getX(), blockPos.getY(), blockPos.getZ(), dimension);
                        sendFoundBlockMessage(blockType, blockPos);
                    }
                }
            }
        }
        
        dataStore.markChunkScanned(pos.x, pos.z, dimension);
        BlockScannerMod.LOGGER.info("Finished scanning chunk {}, found {} target blocks. Total chunks scanned: {}", 
            pos, blocksFound, dataStore.getScannedChunks().size());
    }
    
    /**
     * Gets the string name for a block type.
     * 
     * @param block The block to get the name for
     * @return The string name of the block type
     */
    private String getBlockTypeName(Block block) {
        if (block == null) {
            return "unknown";
        }
        Identifier id = Registries.BLOCK.getId(block);
        return id != null ? id.toString() : "unknown";
    }

    private boolean isSignBlock(Block block) {
        return block instanceof AbstractSignBlock;
    }

    private String getSignText(World world, BlockPos blockPos) {
        BlockEntity blockEntity = world.getBlockEntity(blockPos);
        if (!(blockEntity instanceof SignBlockEntity signBlockEntity)) {
            return null;
        }

        String frontText = readSignSide(signBlockEntity.getFrontText());
        String backText = readSignSide(signBlockEntity.getBackText());

        boolean hasFront = frontText != null && !frontText.isBlank();
        boolean hasBack = backText != null && !backText.isBlank();

        if (hasFront && hasBack) {
            return "Front: " + frontText + "\nBack: " + backText;
        }
        if (hasFront) {
            return frontText;
        }
        if (hasBack) {
            return backText;
        }
        return null;
    }

    private String readSignSide(SignText signText) {
        if (signText == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            Text line = signText.getMessage(i, false);
            if (line == null) {
                continue;
            }
            String text = line.getString();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(text);
        }
        return builder.length() > 0 ? builder.toString() : null;
    }

    private void sendFoundBlockMessage(String blockType, BlockPos blockPos) {
        if (blockType == null || blockPos == null) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null) {
            String message = "[Block Scanner] Found " + blockType + " at " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ();
            client.player.sendMessage(net.minecraft.text.Text.literal(message), false);
        }
    }
    
    /**
     * Clears the scan queue and resets session data.
     * This is called when disconnecting from a server.
     */
    public void reset() {
        scanQueue.clear();
        queuedChunks.clear();
        dataStore.clearSessionData();
    }

    public void clearAllData() {
        scanQueue.clear();
        queuedChunks.clear();
        dataStore.clear();
    }
}
