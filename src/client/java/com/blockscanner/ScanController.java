package com.blockscanner;

import com.blockscanner.data.ScanConfig;
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
    private static KeyBinding toggleKey;

    private static final int MAX_CHUNKS_PER_TICK = 2;
    private static final int NEARBY_QUEUE_RADIUS = 5;
    private static final List<String> DEFAULT_TARGET_BLOCKS = List.of(
        "minecraft:barrier",
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block"
    );

    private List<String> targetBlocks = new ArrayList<>(DEFAULT_TARGET_BLOCKS);
    private boolean rescanScannedChunks = false;
    private boolean scanSigns = false;

    public ScanController(BlockScanner blockScanner) {
        this.blockScanner = blockScanner;
        this.blockScanner.setTargetBlocks(targetBlocks);
        this.blockScanner.setRescanScannedChunks(rescanScannedChunks);
        this.blockScanner.setScanSigns(scanSigns);
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
                client.player.sendMessage(Text.literal("a[Block Scanner] Scanning started"), false);
            } else {
                client.player.sendMessage(Text.literal("c[Block Scanner] Scanning stopped"), false);
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

    public synchronized ScanConfig getConfigSnapshot() {
        return new ScanConfig(List.copyOf(targetBlocks), rescanScannedChunks, scanSigns);
    }

    public synchronized List<String> getTargetBlocks() {
        return List.copyOf(targetBlocks);
    }

    public synchronized boolean isRescanScannedChunks() {
        return rescanScannedChunks;
    }

    public synchronized boolean isScanSigns() {
        return scanSigns;
    }

    public synchronized String updateConfig(List<String> targetBlocks, Boolean rescanScannedChunks, Boolean scanSigns) {
        if (targetBlocks != null) {
            List<String> normalized = normalizeTargetBlocks(targetBlocks);
            if (normalized.isEmpty()) {
                return "targetBlocks must include at least one block id";
            }
            String error = blockScanner.setTargetBlocks(normalized);
            if (error != null) {
                return error;
            }
            this.targetBlocks = new ArrayList<>(normalized);
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
    }

    private List<String> normalizeTargetBlocks(List<String> rawBlockIds) {
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
