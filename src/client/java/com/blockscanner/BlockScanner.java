package com.blockscanner;

import com.blockscanner.data.ScanDataStore;
import com.blockscanner.data.ScanResult;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.Chunk;

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
    
    /**
     * Checks if a block is a target block (barrier or command block).
     * 
     * @param block The block to check
     * @return true if the block is a barrier or command block, false otherwise
     */
    public boolean isTargetBlock(Block block) {
        if (block == null) {
            return false;
        }
        
        return block == Blocks.BARRIER ||
               block == Blocks.COMMAND_BLOCK ||
               block == Blocks.CHAIN_COMMAND_BLOCK ||
               block == Blocks.REPEATING_COMMAND_BLOCK;
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
                if (!dataStore.isChunkScanned(x, z, dimension)) {
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
        
        if (dataStore.isChunkScanned(pos.x, pos.z, dimension)) {
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
                        ScanResult result = new ScanResult(
                            blockType,
                            blockPos.getX(),
                            blockPos.getY(),
                            blockPos.getZ(),
                            dimension,
                            System.currentTimeMillis()
                        );
                        
                        dataStore.addFoundBlock(result);
                        blocksFound++;
                        BlockScannerMod.LOGGER.info("Found {} at {}, {}, {} in {}", blockType, blockPos.getX(), blockPos.getY(), blockPos.getZ(), dimension);
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
        if (block == Blocks.BARRIER) {
            return "barrier";
        } else if (block == Blocks.COMMAND_BLOCK) {
            return "command_block";
        } else if (block == Blocks.CHAIN_COMMAND_BLOCK) {
            return "chain_command_block";
        } else if (block == Blocks.REPEATING_COMMAND_BLOCK) {
            return "repeating_command_block";
        }
        return "unknown";
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
}
