package com.blockscanner;

import com.blockscanner.data.ConfigPersistence;
import com.blockscanner.data.DataPersistence;
import com.blockscanner.data.ScanConfig;
import com.blockscanner.data.ScanDataStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main entry point for the Block Scanner mod.
 * This client-side mod scans loaded chunks for user-configured block types,
 * persists results to JSON, and provides a web-based visualization interface.
 */
public class BlockScannerMod implements ClientModInitializer {
    public static final String MOD_ID = "blockscanner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static ScanController scanController;
    private static ScanDataStore dataStore;
    private static DataPersistence dataPersistence;
    private static ConfigPersistence configPersistence;
    private static BlockScanner blockScanner;
    private static WebServer webServer;
    private boolean webServerStarted = false;
    private boolean announcedStatus = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Block Scanner mod initializing...");
        
        dataStore = new ScanDataStore();
        dataPersistence = new DataPersistence();
        configPersistence = new ConfigPersistence();
        blockScanner = new BlockScanner(dataStore);
        scanController = new ScanController(blockScanner);
        webServer = new WebServer(8080, dataStore, scanController, configPersistence, dataPersistence);
        
            try {
                ScanConfig config = configPersistence.load();
            if (config != null) {
                String error = scanController.updateConfig(config.targetBlocks(), config.rescanScannedChunks(), config.scanSigns());
                if (error != null) {
                    LOGGER.warn("Invalid saved config ignored: {}", error);
                } else {
                    LOGGER.info("Loaded scan config from {}", configPersistence.getConfigFile());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load scan config: {}", e.getMessage());
        }
        
        ScanController.registerKeybind();
        
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String serverAddress = getServerAddress(client);
            LOGGER.info("Joined server: {}", serverAddress);
            
            dataStore.setCurrentServer(serverAddress);
            
            try {
                dataPersistence.load(dataStore, serverAddress);
                LOGGER.info("Loaded scan data for server: {}", serverAddress);
            } catch (IOException e) {
                LOGGER.warn("Failed to load scan data for server {}: {}", serverAddress, e.getMessage());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            String serverAddress = dataStore.getCurrentServer();
            if (serverAddress != null) {
                LOGGER.info("Disconnected from server: {}", serverAddress);
                
                try {
                    dataPersistence.save(dataStore);
                    LOGGER.info("Saved scan data for server: {}", serverAddress);
                } catch (IOException e) {
                    LOGGER.error("Failed to save scan data for server {}: {}", serverAddress, e.getMessage());
                }
            }
            
            dataStore.clearSessionData();
        });
        
        try {
            webServer.start();
            LOGGER.info("Web server started on port 8080");
            webServerStarted = true;
        } catch (IOException e) {
            LOGGER.error("Failed to start web server: {}", e.getMessage());
            webServerStarted = false;
        }
        
        LOGGER.info("Block Scanner mod initialized successfully!");
    }
    
    /**
     * Handles client tick events.
     * Processes keybind presses and delegates to ScanController.
     * 
     * @param client The Minecraft client instance
     */
    private void onClientTick(MinecraftClient client) {
        if (ScanController.getToggleKey() != null) {
            while (ScanController.getToggleKey().wasPressed()) {
                scanController.toggle();
            }
        }

        if (!announcedStatus && client.player != null) {
            if (webServerStarted) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("[Block Scanner] Web server running at http://localhost:8080"),
                    false
                );
            } else {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("[Block Scanner] Web server failed to start (check logs)"),
                    false
                );
            }
            announcedStatus = true;
        }
        
        scanController.onClientTick(client);
        
        if (scanController.isActive() && client.world != null) {
            saveDataIfNeeded();
        }
    }
    
    /**
     * Gets the current server address.
     * 
     * @param client The Minecraft client
     * @return The server address or "singleplayer" for local worlds
     */
    private String getServerAddress(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null) {
            return client.getCurrentServerEntry().address;
        } else if (client.isInSingleplayer()) {
            return "singleplayer";
        } else {
            return "unknown";
        }
    }
    
    /**
     * Saves data if needed (throttled to avoid excessive I/O).
     */
    private long lastSaveTime = 0;
    private long lastSaveAttemptTime = 0;
    private static final long SAVE_INTERVAL_MS = 30000;
    private static final long SAVE_RETRY_INTERVAL_MS = 5000;
    
    private void saveDataIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSaveTime > SAVE_INTERVAL_MS &&
            currentTime - lastSaveAttemptTime > SAVE_RETRY_INTERVAL_MS) {
            lastSaveAttemptTime = currentTime;
            try {
                dataPersistence.save(dataStore);
                lastSaveTime = currentTime;
            } catch (IOException e) {
                LOGGER.warn("Failed to save scan data to {}: {}", 
                    dataPersistence.getServerDataFile(dataStore.getCurrentServer()), 
                    e.getMessage());
            } catch (Exception e) {
                LOGGER.error("Unexpected error saving scan data: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Gets the scan controller instance.
     * 
     * @return The scan controller
     */
    public static ScanController getScanController() {
        return scanController;
    }
    
    /**
     * Gets the data store instance.
     * 
     * @return The data store
     */
    public static ScanDataStore getDataStore() {
        return dataStore;
    }
    
    /**
     * Gets the web server instance.
     * 
     * @return The web server
     */
    public static WebServer getWebServer() {
        return webServer;
    }
}
