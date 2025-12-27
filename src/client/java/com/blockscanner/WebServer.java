package com.blockscanner;

import com.blockscanner.data.ConfigPersistence;
import com.blockscanner.data.DataPersistence;
import com.blockscanner.data.ScanConfig;
import com.blockscanner.data.ScanDataStore;
import com.blockscanner.data.ScanResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Embedded HTTP server using Java's com.sun.net.httpserver.HttpServer.
 * Serves the visualization interface and provides JSON API endpoints.
 */
public class WebServer {
    private HttpServer server;
    private final int port;
    private final ScanDataStore dataStore;
    private final ScanController scanController;
    private final ConfigPersistence configPersistence;
    private final DataPersistence dataPersistence;
    private final Gson gson;
    private final String modVersion;
    private final AtomicLong toggleCount = new AtomicLong(0);
    private final AtomicLong lastToggleAt = new AtomicLong(0);
    
    public WebServer(int port, ScanDataStore dataStore, ScanController scanController, ConfigPersistence configPersistence, DataPersistence dataPersistence) {
        this.port = port;
        this.dataStore = dataStore;
        this.scanController = scanController;
        this.configPersistence = configPersistence;
        this.dataPersistence = dataPersistence;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.modVersion = FabricLoader.getInstance()
            .getModContainer(BlockScannerMod.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }
    
    /**
     * Starts the HTTP server on the configured port.
     * 
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", this::handleRoot);
        server.createContext("/app.js", this::handleAppJs);
        server.createContext("/api/blocks", this::handleBlocks);
        server.createContext("/api/chunks", this::handleChunks);
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/player", this::handlePlayer);
        server.createContext("/api/toggle", this::handleToggle);
        server.createContext("/api/config", this::handleConfig);
        server.createContext("/api/clear", this::handleClear);
        
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        
        BlockScannerMod.LOGGER.info("WebServer started on port {}", port);
    }
    
    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            BlockScannerMod.LOGGER.info("WebServer stopped");
        }
    }
    
    /**
     * Handles requests to the root path (/) - serves HTML visualization page.
     */
    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        
        String html = generateVisualizationHTML();
        exchange.getResponseHeaders().add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        exchange.getResponseHeaders().add("Pragma", "no-cache");
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "text/html", html);
    }

    private void handleAppJs(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            sendResponse(exchange, 204, "text/plain", "");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String js = generateAppJs();
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/javascript", js);
    }
    
    /**
     * Handles requests to /api/blocks - returns found blocks as JSON.
     */
    private void handleBlocks(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            sendResponse(exchange, 204, "text/plain", "");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        
        List<ScanResult> blocks = dataStore.getFoundBlocks();
        String json = gson.toJson(blocks);
        
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/json", json);
    }
    
    /**
     * Handles requests to /api/chunks - returns scanned chunks as JSON.
     */
    private void handleChunks(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            sendResponse(exchange, 204, "text/plain", "");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("scannedChunks", dataStore.getScannedChunks());
        payload.put("skippedChunks", dataStore.getSkippedChunks());
        String json = gson.toJson(payload);
        
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/json", json);
    }
    
    /**
     * Handles requests to /api/status - returns scanning status and counts.
     */
    private void handleStatus(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            sendResponse(exchange, 204, "text/plain", "");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        
        Map<String, Object> status = new HashMap<>();
        status.put("scanning", scanController.isActive());
        status.put("serverAddress", dataStore.getCurrentServer() != null ? dataStore.getCurrentServer() : "unknown");
        status.put("modVersion", modVersion);
        status.put("toggleCount", toggleCount.get());
        status.put("lastToggleAt", lastToggleAt.get());
        
        List<ScanResult> blocks = dataStore.getFoundBlocks();
        status.put("totalBlocksFound", blocks.size());
        status.put("totalChunksScanned", dataStore.getScannedChunks().size());
        
        Map<String, Integer> blockCounts = new HashMap<>();
        for (ScanResult block : blocks) {
            blockCounts.merge(block.blockType(), 1, Integer::sum);
        }
        status.put("blockCounts", blockCounts);
        
        String json = gson.toJson(status);
        
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/json", json);
    }
    
    /**
     * Handles requests to /api/player - returns player position.
     */
    private void handlePlayer(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            sendResponse(exchange, 204, "text/plain", "");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        Map<String, Object> playerData = new HashMap<>();
        
        if (client.player != null) {
            playerData.put("x", client.player.getX());
            playerData.put("y", client.player.getY());
            playerData.put("z", client.player.getZ());
            
            ChunkPos chunkPos = client.player.getChunkPos();
            playerData.put("chunkX", chunkPos.x);
            playerData.put("chunkZ", chunkPos.z);
            
            if (client.world != null) {
                playerData.put("dimension", client.world.getRegistryKey().getValue().toString());
            } else {
                playerData.put("dimension", "unknown");
            }
        } else {
            playerData.put("x", 0.0);
            playerData.put("y", 0.0);
            playerData.put("z", 0.0);
            playerData.put("chunkX", 0);
            playerData.put("chunkZ", 0);
            playerData.put("dimension", "unknown");
        }
        
        String json = gson.toJson(playerData);
        
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/json", json);
    }
    
    /**
     * Handles requests to /api/toggle - toggles scanning on/off.
     */
    private void handleToggle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            sendResponse(exchange, 204, "text/plain", "");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        toggleCount.incrementAndGet();
        lastToggleAt.set(System.currentTimeMillis());

        CompletableFuture<Boolean> toggledState = new CompletableFuture<>();
        net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
            try {
                scanController.toggle();
                toggledState.complete(scanController.isActive());
            } catch (Throwable t) {
                toggledState.completeExceptionally(t);
            }
        });

        boolean active;
        try {
            active = toggledState.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            BlockScannerMod.LOGGER.warn("Toggle timed out waiting for client thread");
            active = scanController.isActive();
        } catch (Exception e) {
            BlockScannerMod.LOGGER.warn("Toggle failed: {}", e.getMessage());
            active = scanController.isActive();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("scanning", active);
        response.put("message", active ? "Scanning started" : "Scanning stopped");
        
        String json = gson.toJson(response);
        
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/json", json);
    }
    
    /**
     * Handles requests to /api/config - gets or updates scanning configuration.
     */
    private void handleConfig(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equals(method)) {
            addCorsHeaders(exchange);
            sendResponse(exchange, 204, "text/plain", "");
            return;
        }
        if ("GET".equals(method)) {
            Map<String, Object> config = buildConfigResponse();
            String json = gson.toJson(config);
            addCorsHeaders(exchange);
            sendResponse(exchange, 200, "application/json", json);
            return;
        }

        if (!"POST".equals(method)) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String body = readRequestBody(exchange);
        com.google.gson.JsonObject payload = gson.fromJson(body, com.google.gson.JsonObject.class);
        List<String> targetBlocks = null;
        Boolean rescanScannedChunks = null;
        Boolean scanSigns = null;
        if (payload != null && payload.has("targetBlocks")) {
            targetBlocks = toStringList(payload.get("targetBlocks"));
        }
        if (payload != null && payload.has("rescanScannedChunks")) {
            rescanScannedChunks = toBoolean(payload.get("rescanScannedChunks"));
        }
        if (payload != null && payload.has("scanSigns")) {
            scanSigns = toBoolean(payload.get("scanSigns"));
        }

        String error = scanController.updateConfig(targetBlocks, rescanScannedChunks, scanSigns);
        if (error != null) {
            Map<String, Object> response = buildConfigResponse();
            response.put("error", error);
            String json = gson.toJson(response);
            addCorsHeaders(exchange);
            sendResponse(exchange, 400, "application/json", json);
            return;
        }

        try {
            ScanConfig config = scanController.getConfigSnapshot();
            configPersistence.save(config);
        } catch (IOException e) {
            BlockScannerMod.LOGGER.warn("Failed to save scan config: {}", e.getMessage());
            Map<String, Object> response = buildConfigResponse();
            response.put("error", "Failed to save config to disk");
            String json = gson.toJson(response);
            addCorsHeaders(exchange);
            sendResponse(exchange, 500, "application/json", json);
            return;
        }

        Map<String, Object> config = buildConfigResponse();
        String json = gson.toJson(config);

        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/json", json);
    }

    /**
     * Handles requests to /api/clear - clears scan data for the current server.
     */
    private void handleClear(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            sendResponse(exchange, 204, "text/plain", "");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String serverAddress = dataStore.getCurrentServer();
        if (serverAddress == null || serverAddress.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Server address not available");
            String json = gson.toJson(response);
            addCorsHeaders(exchange);
            sendResponse(exchange, 400, "application/json", json);
            return;
        }

        scanController.clearAllData();

        boolean deleted = false;
        try {
            deleted = dataPersistence.delete(serverAddress);
        } catch (IOException e) {
            BlockScannerMod.LOGGER.warn("Failed to delete scan data for {}: {}", serverAddress, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to delete persisted scan data");
            String json = gson.toJson(response);
            addCorsHeaders(exchange);
            sendResponse(exchange, 500, "application/json", json);
            return;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("cleared", true);
        response.put("deletedFile", deleted);
        String json = gson.toJson(response);
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/json", json);
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        return new String(body, StandardCharsets.UTF_8);
    }

    private List<String> toStringList(com.google.gson.JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonArray()) {
            return null;
        }
        List<String> values = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement element : value.getAsJsonArray()) {
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String text = element.getAsString();
                if (text != null) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    private Boolean toBoolean(com.google.gson.JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        return null;
    }

    private Map<String, Object> buildConfigResponse() {
        Map<String, Object> config = new HashMap<>();
        config.put("targetBlocks", scanController.getTargetBlocks());
        config.put("rescanScannedChunks", scanController.isRescanScannedChunks());
        config.put("scanSigns", scanController.isScanSigns());
        return config;
    }

    /**
     * Adds CORS headers to allow cross-origin requests.
     */
    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }
    
    /**
     * Sends an HTTP response with the specified status, content type, and body.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String contentType, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        exchange.getResponseHeaders().add("Pragma", "no-cache");
        exchange.getResponseHeaders().add("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    /**
     * Generates the HTML visualization page.
     */
    private String generateVisualizationHTML() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Block Scanner - Visualization</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        margin: 0;
                        padding: 20px;
                        background-color: #1a1a1a;
                        color: #ffffff;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 30px;
                    }
                    .status {
                        background-color: #2d2d2d;
                        padding: 15px;
                        border-radius: 8px;
                        margin-bottom: 20px;
                    }
                    .status.active {
                        border-left: 4px solid #4CAF50;
                    }
                    .status.inactive {
                        border-left: 4px solid #f44336;
                    }
                    .toggle-button {
                        background-color: #4CAF50;
                        color: white;
                        border: none;
                        padding: 10px 20px;
                        font-size: 16px;
                        border-radius: 4px;
                        cursor: pointer;
                        margin-left: 15px;
                        transition: background-color 0.2s;
                    }
                    .toggle-button:hover {
                        background-color: #45a049;
                    }
                    .toggle-button.stop {
                        background-color: #f44336;
                    }
                    .toggle-button.stop:hover {
                        background-color: #da190b;
                    }
                    .grid-container {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 20px;
                        margin-bottom: 20px;
                    }
                    .panel {
                        background-color: #2d2d2d;
                        padding: 20px;
                        border-radius: 8px;
                    }
                    .hidden {
                        display: none;
                    }
                    .block-list {
                        max-height: 400px;
                        overflow-y: auto;
                    }
                    .block-item {
                        background-color: #3d3d3d;
                        margin: 5px 0;
                        padding: 10px;
                        border-radius: 4px;
                        cursor: pointer;
                        transition: background-color 0.2s;
                    }
                    .block-item:hover {
                        background-color: #4d4d4d;
                    }
                    .block-type {
                        font-weight: bold;
                        color: #4CAF50;
                    }
                    .coordinates {
                        font-family: monospace;
                        color: #cccccc;
                    }
                    .sign-text {
                        margin-top: 6px;
                        font-size: 12px;
                        color: #e0e0e0;
                        white-space: pre-line;
                    }
                    .chunk-map {
                        display: grid;
                        grid-template-columns: repeat(20, 1fr);
                        gap: 1px;
                        background-color: #1a1a1a;
                        padding: 10px;
                        border-radius: 4px;
                        max-height: 300px;
                        overflow: auto;
                    }
                    .chunk-cell {
                        width: 12px;
                        height: 12px;
                        background-color: #3d3d3d;
                        border-radius: 2px;
                    }
                    .chunk-cell.scanned {
                        background-color: #4CAF50;
                    }
                    .chunk-cell.skipped {
                        background-color: #ff9800;
                    }
                    .chunk-cell.player {
                        background-color: #2196F3;
                        border: 2px solid #ffffff;
                    }
                    .dimension-tabs {
                        display: flex;
                        margin-bottom: 10px;
                    }
                    .tab {
                        padding: 8px 16px;
                        background-color: #3d3d3d;
                        border: none;
                        color: #ffffff;
                        cursor: pointer;
                        margin-right: 5px;
                        border-radius: 4px;
                    }
                    .tab.active {
                        background-color: #4CAF50;
                    }
                    .stats {
                        display: flex;
                        justify-content: space-around;
                        margin-top: 15px;
                    }
                    .stat {
                        text-align: center;
                    }
                    .stat-value {
                        font-size: 24px;
                        font-weight: bold;
                        color: #4CAF50;
                    }
                    .stat-label {
                        font-size: 12px;
                        color: #cccccc;
                    }
                    .config-panel {
                        margin-top: 20px;
                    }
                    .config-form {
                        display: grid;
                        gap: 10px;
                    }
                    .config-row {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        gap: 12px;
                    }
                    .config-row label {
                        flex: 1;
                        color: #cccccc;
                    }
                    .config-row input[type="checkbox"] {
                        transform: scale(1.1);
                    }
                    .config-input {
                        width: 100%;
                        padding: 8px;
                        border-radius: 4px;
                        border: 1px solid #444;
                        background-color: #1f1f1f;
                        color: #ffffff;
                        font-family: monospace;
                        min-height: 90px;
                    }
                    .config-actions {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        margin-top: 10px;
                    }
                    .config-save {
                        background-color: #2196F3;
                        color: #ffffff;
                        border: none;
                        padding: 8px 16px;
                        border-radius: 4px;
                        cursor: pointer;
                        transition: background-color 0.2s;
                    }
                    .config-save:hover {
                        background-color: #1b7fcf;
                    }
                    .config-status {
                        font-size: 12px;
                        color: #cccccc;
                        min-height: 16px;
                    }
                    .config-note {
                        font-size: 12px;
                        color: #888888;
                    }
                    .config-warning {
                        font-size: 12px;
                        color: #f5a623;
                        min-height: 16px;
                    }
                    .config-danger {
                        background-color: #d9534f;
                        color: #ffffff;
                        border: none;
                        padding: 8px 16px;
                        border-radius: 4px;
                        cursor: pointer;
                        transition: background-color 0.2s;
                    }
                    .config-danger:hover {
                        background-color: #c9302c;
                    }
                    .block-counts {
                        margin-top: 10px;
                        display: grid;
                        gap: 6px;
                        font-size: 12px;
                        color: #cccccc;
                    }
                    .block-count-row {
                        display: flex;
                        justify-content: space-between;
                        gap: 10px;
                    }
                    .panel-toggle {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                        font-size: 12px;
                        color: #cccccc;
                        margin-bottom: 10px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Block Scanner Visualization</h1>
                        <div id="status" class="status">
                            <span id="status-text">Loading...</span>
                            <button id="toggle-button" class="toggle-button" type="button">
                                Start Scanning
                            </button>
                        </div>
                    </div>
                    
                    <div class="grid-container">
                        <div class="panel">
                            <h2>Found Blocks</h2>
                            <div id="block-list" class="block-list">
                                Loading blocks...
                            </div>
                            <div class="stats">
                                <div class="stat">
                                    <div id="total-blocks" class="stat-value">0</div>
                                    <div class="stat-label">Total Blocks</div>
                                </div>
                            </div>
                            <div id="block-counts" class="block-counts"></div>
                        </div>
                        
                        <div class="panel">
                            <h2>Chunk Coverage</h2>
                            <div class="dimension-tabs">
                                <button class="tab active" data-dimension="minecraft:overworld" type="button">Overworld</button>
                                <button class="tab" data-dimension="minecraft:the_nether" type="button">Nether</button>
                                <button class="tab" data-dimension="minecraft:the_end" type="button">End</button>
                            </div>
                            <div id="chunk-map" class="chunk-map">
                                Loading chunks...
                            </div>
                            <div class="stats">
                                <div class="stat">
                                    <div id="total-chunks" class="stat-value">0</div>
                                    <div class="stat-label">Scanned Chunks</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="panel">
                        <div class="panel-toggle">
                            <input id="signs-toggle" type="checkbox" />
                            <label for="signs-toggle">Scan signs (shows section)</label>
                        </div>
                        <div id="signs-panel" class="hidden">
                            <h2>Found Signs</h2>
                            <div id="sign-list" class="block-list">
                                Loading signs...
                            </div>
                        </div>
                    </div>

                    <div class="panel config-panel">
                        <h2>Scan Settings</h2>
                        <div class="config-form">
                            <div class="config-row">
                                <label for="target-blocks">Target block IDs (comma or newline separated)</label>
                            </div>
                            <textarea id="target-blocks" class="config-input" placeholder="minecraft:barrier&#10;minecraft:command_block"></textarea>
                            <div class="config-note">Tip: use full ids like minecraft:diamond_block or modid:custom_block.</div>
                            <div class="config-warning" id="block-warning"></div>
                            <div class="config-row">
                                <label for="rescan-toggle">Rescan already scanned chunks</label>
                                <input id="rescan-toggle" type="checkbox" />
                            </div>
                            <div class="config-note">Enable this if the world changes and you want to scan previously scanned chunks again.</div>
                            <div class="config-actions">
                                <button class="config-save" id="config-save" type="button">Save Settings</button>
                                <div class="config-status" id="config-status"></div>
                            </div>
                            <div class="config-actions">
                                <button class="config-danger" id="clear-button" type="button">Clear Server Scan Data</button>
                                <div class="config-status" id="clear-status"></div>
                            </div>
                        </div>
                    </div>
                </div>

                <script>/*
                    let currentDimension = 'minecraft:overworld';
                    let blocksData = [];
                    let chunksData = [];
                    let skippedChunksData = [];
                    let playerData = {};
                    let configData = {};
                    let statusData = {};
                    const centeredChunkMap = {};
                    
                    const apiBase = window.location.protocol === 'file:' ? 'http://localhost:8080' : '';

                    function apiUrl(path) {
                        return `${apiBase}${path}`;
                    }

                    async function fetchData() {
                        try {
                            const [statusRes, blocksRes, chunksRes, playerRes] = await Promise.all([
                                fetch(apiUrl('/api/status')),
                                fetch(apiUrl('/api/blocks')),
                                fetch(apiUrl('/api/chunks')),
                                fetch(apiUrl('/api/player'))
                            ]);
                            
                            statusData = await statusRes.json();
                            blocksData = await blocksRes.json();
                            const chunkPayload = await chunksRes.json();
                            chunksData = chunkPayload.scannedChunks || [];
                            skippedChunksData = chunkPayload.skippedChunks || [];
                            playerData = await playerRes.json();
                            
                            if (playerData.dimension && playerData.dimension !== currentDimension) {
                                currentDimension = playerData.dimension;
                                updateDimensionTabs();
                            }
                            
                            updateStatus(statusData);
                            updateBlockList();
                            updateChunkMap();
                        } catch (error) {
                            console.error('Error fetching data:', error);
                            setStatusError('API error - check that the mod is running.');
                        }
                    }
                    
                    async function fetchConfig() {
                        try {
                            const configRes = await fetch(apiUrl('/api/config'));
                            configData = await configRes.json();
                            updateConfigForm(configData);
                        } catch (error) {
                            console.error('Error fetching config:', error);
                            const statusEl = document.getElementById('config-status');
                            statusEl.textContent = 'Config load failed';
                        }
                    }

                    const COMMON_BLOCKS = new Set([
                        'minecraft:stone',
                        'minecraft:dirt',
                        'minecraft:grass_block',
                        'minecraft:deepslate',
                        'minecraft:sand',
                        'minecraft:gravel',
                        'minecraft:cobblestone',
                        'minecraft:netherrack',
                        'minecraft:water',
                        'minecraft:lava'
                    ]);

                    function parseTargetBlocks(text) {
                        if (!text) {
                            return [];
                        }
                        return text
                            .split(/[,\\n]/)
                            .map(entry => entry.trim())
                            .filter(entry => entry.length > 0);
                    }

                    function formatTargetBlocks(blocks) {
                        if (!Array.isArray(blocks)) {
                            return '';
                        }
                        return blocks.join('\\n');
                    }

                    function updateConfigForm(config) {
                        if (!config) {
                            return;
                        }
                        const targetBlocksInput = document.getElementById('target-blocks');
                        const rescanToggle = document.getElementById('rescan-toggle');

                        targetBlocksInput.value = formatTargetBlocks(config.targetBlocks);
                        rescanToggle.checked = Boolean(config.rescanScannedChunks);
                        updateBlockWarning(parseTargetBlocks(targetBlocksInput.value));
                    }

                    function updateBlockWarning(blocks) {
                        const warningEl = document.getElementById('block-warning');
                        const lower = blocks.map(block => {
                            const raw = block.toLowerCase();
                            return raw.includes(':') ? raw : `minecraft:${raw}`;
                        });
                        const hasCommon = lower.some(block => COMMON_BLOCKS.has(block));

                        if (hasCommon) {
                            warningEl.textContent = 'Warning: scanning for common blocks can crash or lag your game.';
                        } else {
                            warningEl.textContent = '';
                        }
                    }

                    function validateConfigInputs(targetBlocks) {
                        if (!Array.isArray(targetBlocks) || targetBlocks.length === 0) {
                            return 'Enter at least one block id.';
                        }
                        return null;
                    }

                    async function saveConfig() {
                        const statusEl = document.getElementById('config-status');
                        statusEl.textContent = 'Saving...';

                        const targetBlocks = parseTargetBlocks(document.getElementById('target-blocks').value);
                        const rescanScannedChunks = document.getElementById('rescan-toggle').checked;

                        const validationError = validateConfigInputs(targetBlocks);
                        if (validationError) {
                            statusEl.textContent = validationError;
                            updateBlockWarning(targetBlocks);
                            return;
                        }

                        try {
                            const response = await fetch(apiUrl('/api/config'), {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json'
                                },
                                body: JSON.stringify({
                                    targetBlocks: targetBlocks,
                                    rescanScannedChunks: rescanScannedChunks
                                })
                            });

                            const payload = await response.json();
                            if (!response.ok) {
                                const message = payload?.error || 'Failed to save config';
                                statusEl.textContent = message;
                                updateConfigForm(payload);
                                return;
                            }

                            configData = payload;
                            updateConfigForm(configData);
                            statusEl.textContent = 'Saved';
                        } catch (error) {
                            console.error('Error saving config:', error);
                            statusEl.textContent = 'Save failed';
                        }
                    }

                    function updateStatus(status) {
                        const statusEl = document.getElementById('status');
                        const statusText = document.getElementById('status-text');
                        const toggleButton = document.getElementById('toggle-button');

                        const dimensionName = getDimensionDisplayName(currentDimension);

                        if (status.scanning) {
                            statusEl.className = 'status active';
                            statusText.textContent = `Scanning Active - Server: ${status.serverAddress} - Dimension: ${dimensionName}`;
                            toggleButton.textContent = 'Stop Scanning';
                            toggleButton.className = 'toggle-button stop';
                        } else {
                            statusEl.className = 'status inactive';
                            statusText.textContent = `Scanning Inactive - Server: ${status.serverAddress} - Dimension: ${dimensionName}`;
                            toggleButton.textContent = 'Start Scanning';
                            toggleButton.className = 'toggle-button';
                        }

                        document.getElementById('total-blocks').textContent = status.totalBlocksFound;
                        document.getElementById('total-chunks').textContent = status.totalChunksScanned;
                        updateBlockCounts(status.blockCounts || {});
                    }

                    function setStatusError(message) {
                        const statusEl = document.getElementById('status');
                        const statusText = document.getElementById('status-text');
                        const toggleButton = document.getElementById('toggle-button');

                        statusEl.className = 'status inactive';
                        statusText.textContent = message;
                        toggleButton.disabled = true;
                    }

                    function updateBlockCounts(blockCounts) {
                        const countsEl = document.getElementById('block-counts');
                        const configured = Array.isArray(configData?.targetBlocks) ? configData.targetBlocks : [];
                        const keys = configured.length > 0 ? configured : Object.keys(blockCounts);

                        if (keys.length === 0) {
                            countsEl.innerHTML = '<div style="color: #888;">No target blocks configured.</div>';
                            return;
                        }

                        const rows = keys.map(key => {
                            const count = blockCounts[key] || 0;
                            return `
                                <div class="block-count-row">
                                    <span>${formatBlockLabel(key)}</span>
                                    <span>${count}</span>
                                </div>
                            `;
                        }).join('');
                        countsEl.innerHTML = rows;
                    }
                    
                    function updateBlockList() {
                        const blockList = document.getElementById('block-list');
                        const signList = document.getElementById('sign-list');

                        if (blocksData.length === 0) {
                            blockList.innerHTML = '<div style="text-align: center; color: #888;">No blocks found yet</div>';
                            signList.innerHTML = '<div style="text-align: center; color: #888;">No signs found yet</div>';
                            return;
                        }

                        const groupedBlocks = {};
                        const groupedSigns = {};
                        blocksData.forEach(block => {
                            if (isSignBlock(block.blockType)) {
                                if (!groupedSigns[block.blockType]) {
                                    groupedSigns[block.blockType] = [];
                                }
                                groupedSigns[block.blockType].push(block);
                                return;
                            }

                            if (!groupedBlocks[block.blockType]) {
                                groupedBlocks[block.blockType] = [];
                            }
                            groupedBlocks[block.blockType].push(block);
                        });

                        blockList.innerHTML = renderGroupedBlocks(groupedBlocks, 'No non-sign blocks found yet');
                        signList.innerHTML = renderGroupedBlocks(groupedSigns, 'No signs found yet');
                    }

                    function renderGroupedBlocks(groupedBlocks, emptyMessage) {
                        const keys = Object.keys(groupedBlocks).sort();
                        if (keys.length === 0) {
                            return `<div style="text-align: center; color: #888;">${emptyMessage}</div>`;
                        }

                        let html = '';
                        keys.forEach(blockType => {
                            html += `<h3>${formatBlockLabel(blockType)}</h3>`;
                            groupedBlocks[blockType].forEach(block => {
                                const teleportCmd = `/tp ${block.x} ${block.y} ${block.z}`;
                                const signText = formatSignText(block.signText);
                                html += `
                                    <div class="block-item" data-tp="${escapeHtml(teleportCmd)}">
                                        <div class="block-type">${escapeHtml(block.blockType)}</div>
                                        <div class="coordinates">${block.x}, ${block.y}, ${block.z}</div>
                                        <div style="font-size: 12px; color: #888;">${escapeHtml(block.dimension)}</div>
                                        ${signText}
                                    </div>
                                `;
                            });
                        });
                        return html;
                    }
                    
                    function updateChunkMap() {
                        const chunkMap = document.getElementById('chunk-map');
                        
                        const dimensionChunks = chunksData.filter(chunk => chunk.dimension === currentDimension);
                        const dimensionSkipped = skippedChunksData.filter(chunk => chunk.dimension === currentDimension);
                        const hasPlayer = playerData.dimension === currentDimension;
                        const allChunks = dimensionChunks.concat(dimensionSkipped);
                        
                        if (allChunks.length === 0 && !hasPlayer) {
                            chunkMap.innerHTML = '<div style="text-align: center; color: #888; grid-column: 1 / -1;">No chunks scanned in this dimension</div>';
                            return;
                        }
                        
                        let minX;
                        let maxX;
                        let minZ;
                        let maxZ;
                        if (allChunks.length > 0) {
                            minX = Math.min(...allChunks.map(c => c.chunkX));
                            maxX = Math.max(...allChunks.map(c => c.chunkX));
                            minZ = Math.min(...allChunks.map(c => c.chunkZ));
                            maxZ = Math.max(...allChunks.map(c => c.chunkZ));
                        } else if (hasPlayer) {
                            minX = playerData.chunkX;
                            maxX = playerData.chunkX;
                            minZ = playerData.chunkZ;
                            maxZ = playerData.chunkZ;
                        }
                        
                        if (hasPlayer) {
                            minX = Math.min(minX, playerData.chunkX);
                            maxX = Math.max(maxX, playerData.chunkX);
                            minZ = Math.min(minZ, playerData.chunkZ);
                            maxZ = Math.max(maxZ, playerData.chunkZ);
                        }
                        
                        const maxViewRadius = 32;
                        let centerX = Math.round((minX + maxX) / 2);
                        let centerZ = Math.round((minZ + maxZ) / 2);
                        if (hasPlayer) {
                            centerX = playerData.chunkX;
                            centerZ = playerData.chunkZ;
                        }
                        minX = centerX - maxViewRadius;
                        maxX = centerX + maxViewRadius;
                        minZ = centerZ - maxViewRadius;
                        maxZ = centerZ + maxViewRadius;
                        
                        const width = maxX - minX + 1;
                        const height = maxZ - minZ + 1;
                        
                        chunkMap.style.gridTemplateColumns = `repeat(${width}, 1fr)`;
                        
                        let html = '';
                        for (let z = minZ; z <= maxZ; z++) {
                            for (let x = minX; x <= maxX; x++) {
                                const isScanned = dimensionChunks.some(c => c.chunkX === x && c.chunkZ === z);
                                const isSkipped = dimensionSkipped.some(c => c.chunkX === x && c.chunkZ === z);
                                const isPlayer = playerData.dimension === currentDimension && 
                                               playerData.chunkX === x && playerData.chunkZ === z;
                                let className = 'chunk-cell';
                                if (isPlayer) className += ' player';
                                else if (isScanned) className += ' scanned';
                                else if (isSkipped) className += ' skipped';
                                
                                html += `<div class="${className}" title="Chunk ${x}, ${z}"></div>`;
                            }
                        }
                        
                        chunkMap.innerHTML = html;
                        const playerCell = chunkMap.querySelector('.chunk-cell.player');
                        if (playerCell && !centeredChunkMap[currentDimension]) {
                            centerInScrollContainer(chunkMap, playerCell);
                            centeredChunkMap[currentDimension] = true;
                        }
                    }
                    
                    function switchDimension(dimension, event) {
                        currentDimension = dimension;
                        
                        document.querySelectorAll('.tab').forEach(tab => {
                            tab.classList.remove('active');
                        });
                        if (event && event.currentTarget) {
                            event.currentTarget.classList.add('active');
                        }
                        
                        updateChunkMap();
                    }
                    
                    function updateDimensionTabs() {
                        document.querySelectorAll('.tab').forEach(tab => {
                            tab.classList.remove('active');
                            if (tab.textContent.toLowerCase().includes(getDimensionDisplayName(currentDimension).toLowerCase())) {
                                tab.classList.add('active');
                            }
                        });
                    }
                    
                    function getDimensionDisplayName(dimension) {
                        switch (dimension) {
                            case 'minecraft:overworld': return 'Overworld';
                            case 'minecraft:the_nether': return 'Nether';
                            case 'minecraft:the_end': return 'End';
                            default: return dimension.replace('minecraft:', '');
                        }
                    }

                    function formatBlockLabel(blockType) {
                        if (!blockType) {
                            return 'unknown';
                        }
                        return blockType.replace('minecraft:', '').replace(/_/g, ' ');
                    }

                    function isSignBlock(blockType) {
                        if (!blockType) {
                            return false;
                        }
                        const lower = blockType.toLowerCase();
                        return lower.endsWith('_sign') || lower.endsWith('_wall_sign') || lower.endsWith('_hanging_sign') || lower.endsWith('_wall_hanging_sign');
                    }

                    function escapeHtml(text) {
                        if (!text) {
                            return '';
                        }
                        return text
                            .replace(/&/g, '&amp;')
                            .replace(/</g, '&lt;')
                            .replace(/>/g, '&gt;')
                            .replace(/"/g, '&quot;')
                            .replace(/'/g, '&#039;');
                    }

                    function centerInScrollContainer(container, element) {
                        if (!container || !element) {
                            return;
                        }
                        const targetLeft = element.offsetLeft - (container.clientWidth / 2) + (element.clientWidth / 2);
                        const targetTop = element.offsetTop - (container.clientHeight / 2) + (element.clientHeight / 2);
                        if (Number.isFinite(targetLeft)) {
                            container.scrollLeft = targetLeft;
                        }
                        if (Number.isFinite(targetTop)) {
                            container.scrollTop = targetTop;
                        }
                    }

                    function formatSignText(signText) {
                        if (!signText) {
                            return '';
                        }
                        return `<div class="sign-text">${escapeHtml(signText)}</div>`;
                    }
                    
                    function copyToClipboard(text) {
                        navigator.clipboard.writeText(text).then(() => {
                            const originalText = event.target.innerHTML;
                            event.target.style.backgroundColor = '#4CAF50';
                            event.target.innerHTML = originalText + '<br><small style="color: #fff;">Copied!</small>';
                            
                            setTimeout(() => {
                                event.target.style.backgroundColor = '#3d3d3d';
                                event.target.innerHTML = originalText;
                            }, 1000);
                        }).catch(err => {
                            console.error('Failed to copy to clipboard:', err);
                        });
                    }
                    
                    function setupConfigListeners() {
                        const targetBlocksInput = document.getElementById('target-blocks');
                        const signsToggle = document.getElementById('signs-toggle');
                        const signsPanel = document.getElementById('signs-panel');
                        const toggleButton = document.getElementById('toggle-button');
                        const saveButton = document.getElementById('config-save');
                        const clearButton = document.getElementById('clear-button');

                        const handler = () => {
                            const blocks = parseTargetBlocks(targetBlocksInput.value);
                            updateBlockWarning(blocks);
                        };

                        targetBlocksInput.addEventListener('input', handler);
                        toggleButton.addEventListener('click', toggleScanning);
                        saveButton.addEventListener('click', saveConfig);
                        clearButton.addEventListener('click', confirmClearData);
                        document.querySelectorAll('.tab').forEach(tab => {
                            tab.addEventListener('click', event => {
                                const dimension = event.currentTarget.dataset.dimension;
                                switchDimension(dimension, event);
                            });
                        });
                        const syncSignsPanel = () => {
                            signsPanel.classList.toggle('hidden', !signsToggle.checked);
                        };
                        signsToggle.addEventListener('change', syncSignsPanel);
                        syncSignsPanel();
                    }

                    async function confirmClearData() {
                        const statusEl = document.getElementById('clear-status');
                        statusEl.textContent = '';
                        if (!confirm('This will delete all scan data for this server.')) {
                            return;
                        }
                        if (!confirm('Are you really, really, really sure you want to do this?')) {
                            return;
                        }

                        statusEl.textContent = 'Clearing...';
                        try {
                            const response = await fetch(apiUrl('/api/clear'), {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json'
                                }
                            });

                            const payload = await response.json();
                            if (!response.ok) {
                                statusEl.textContent = payload?.error || 'Failed to clear data';
                                return;
                            }

                            statusEl.textContent = 'Cleared';
                            await fetchData();
                        } catch (error) {
                            console.error('Error clearing data:', error);
                            statusEl.textContent = 'Clear failed';
                        }
                    }

                    async function toggleScanning() {
                        const toggleButton = document.getElementById('toggle-button');
                        const originalText = toggleButton.textContent;
                        
                        toggleButton.disabled = true;
                        toggleButton.textContent = 'Please wait...';
                        
                        try {
                            const response = await fetch(apiUrl('/api/toggle'), {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json',
                                }
                            });
                            
                            if (response.ok) {
                                const result = await response.json();
                                console.log('Toggle result:', result.message, 'scanning=', result.scanning);
                                await fetchData();
                            } else {
                                console.error('Failed to toggle scanning:', response.statusText);
                                alert('Failed to toggle scanning. Please try again.');
                            }
                        } catch (error) {
                            console.error('Error toggling scanning:', error);
                            alert('Error communicating with server. Please try again.');
                        } finally {
                            toggleButton.disabled = false;
                            toggleButton.textContent = originalText;
                        }
                    }
                    
                    setupConfigListeners();
                    fetchData();
                    fetchConfig();
                    setInterval(fetchData, 3000);
                    window.__blockscannerUiInitialized = true;
                */</script>
                <script src="/app.js" defer></script>
            </body>
            </html>
            """;
    }

    private String generateAppJs() {
        return """
            if (window.__blockscannerUiInitialized) {
              console.log('[Block Scanner] UI already initialized (inline script)');
            } else {
              window.__blockscannerUiInitialized = true;
            
              let currentDimension = 'minecraft:overworld';
              let blocksData = [];
              let chunksData = [];
              let skippedChunksData = [];
              let playerData = {};
              let configData = {};
              let statusData = {};
              const centeredChunkMap = {};
              
              const apiBase = window.location.protocol === 'file:' ? 'http://localhost:8080' : '';
            
              function apiUrl(path) {
                return `${apiBase}${path}`;
              }
            
              async function fetchData() {
                try {
                  const [statusRes, blocksRes, chunksRes, playerRes] = await Promise.all([
                    fetch(apiUrl('/api/status')),
                    fetch(apiUrl('/api/blocks')),
                    fetch(apiUrl('/api/chunks')),
                    fetch(apiUrl('/api/player'))
                  ]);
                  
                  statusData = await statusRes.json();
                  blocksData = await blocksRes.json();
                  const chunkPayload = await chunksRes.json();
                  chunksData = chunkPayload.scannedChunks || [];
                  skippedChunksData = chunkPayload.skippedChunks || [];
                  playerData = await playerRes.json();
                  
                  if (playerData.dimension && playerData.dimension !== currentDimension) {
                    currentDimension = playerData.dimension;
                    updateDimensionTabs();
                  }
                  
                  updateStatus(statusData);
                  updateBlockList();
                  updateChunkMap();
                } catch (error) {
                  console.error('Error fetching data:', error);
                  setStatusError('API error - check that the mod is running.');
                }
              }
              
              async function fetchConfig() {
                try {
                  const configRes = await fetch(apiUrl('/api/config'));
                  configData = await configRes.json();
                  updateConfigForm(configData);
                } catch (error) {
                  console.error('Error fetching config:', error);
                  const statusEl = document.getElementById('config-status');
                  if (statusEl) {
                    statusEl.textContent = 'Config load failed';
                  }
                }
              }
            
              const COMMON_BLOCKS = new Set([
                'minecraft:stone',
                'minecraft:dirt',
                'minecraft:grass_block',
                'minecraft:deepslate',
                'minecraft:sand',
                'minecraft:gravel',
                'minecraft:cobblestone',
                'minecraft:netherrack',
                'minecraft:water',
                'minecraft:lava'
              ]);
            
              function parseTargetBlocks(text) {
                if (!text) {
                  return [];
                }
                return text
                  .split(/[,\\n]/)
                  .map(entry => entry.trim())
                  .filter(entry => entry.length > 0);
              }
            
              function formatTargetBlocks(blocks) {
                if (!Array.isArray(blocks)) {
                  return '';
                }
                return blocks.join('\\n');
              }
            
              function updateConfigForm(config) {
                if (!config) {
                  return;
                }
                const targetBlocksInput = document.getElementById('target-blocks');
                const rescanToggle = document.getElementById('rescan-toggle');
                const signsToggle = document.getElementById('signs-toggle');
                const signsPanel = document.getElementById('signs-panel');
             
                if (targetBlocksInput) {
                  targetBlocksInput.value = formatTargetBlocks(config.targetBlocks);
                }
                if (rescanToggle) {
                  rescanToggle.checked = Boolean(config.rescanScannedChunks);
                }
                if (signsToggle) {
                  signsToggle.checked = Boolean(config.scanSigns);
                }
                if (signsToggle && signsPanel) {
                  signsPanel.classList.toggle('hidden', !signsToggle.checked);
                }
                if (targetBlocksInput) {
                  updateBlockWarning(parseTargetBlocks(targetBlocksInput.value));
                }
              }
            
              function updateBlockWarning(blocks) {
                const warningEl = document.getElementById('block-warning');
                if (!warningEl) {
                  return;
                }
                const lower = blocks.map(block => {
                  const raw = block.toLowerCase();
                  return raw.includes(':') ? raw : `minecraft:${raw}`;
                });
                const hasCommon = lower.some(block => COMMON_BLOCKS.has(block));
            
                if (hasCommon) {
                  warningEl.textContent = 'Warning: scanning for common blocks can crash or lag your game.';
                } else {
                  warningEl.textContent = '';
                }
              }
            
              function validateConfigInputs(targetBlocks) {
                if (!Array.isArray(targetBlocks) || targetBlocks.length === 0) {
                  return 'Enter at least one block id.';
                }
                return null;
              }
            
              async function saveConfig() {
                const statusEl = document.getElementById('config-status');
                if (statusEl) {
                  statusEl.textContent = 'Saving...';
                }
             
                const targetBlocks = parseTargetBlocks(document.getElementById('target-blocks')?.value);
                const rescanScannedChunks = Boolean(document.getElementById('rescan-toggle')?.checked);
                const scanSigns = Boolean(document.getElementById('signs-toggle')?.checked);
             
                const validationError = validateConfigInputs(targetBlocks);
                if (validationError) {
                  if (statusEl) {
                    statusEl.textContent = validationError;
                  }
                  updateBlockWarning(targetBlocks);
                  return;
                }
            
                try {
                  const response = await fetch(apiUrl('/api/config'), {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/json'
                    },
                  body: JSON.stringify({
                    targetBlocks: targetBlocks,
                    rescanScannedChunks: rescanScannedChunks,
                    scanSigns: scanSigns
                  })
                });
            
                  const payload = await response.json();
                  if (!response.ok) {
                    const message = payload?.error || 'Failed to save config';
                    if (statusEl) {
                      statusEl.textContent = message;
                    }
                    updateConfigForm(payload);
                    return;
                  }
            
                  configData = payload;
                  updateConfigForm(configData);
                  if (statusEl) {
                    statusEl.textContent = 'Saved';
                  }
                } catch (error) {
                  console.error('Error saving config:', error);
                  if (statusEl) {
                    statusEl.textContent = 'Save failed';
                  }
                }
              }
            
              function updateStatus(status) {
                const statusEl = document.getElementById('status');
                const statusText = document.getElementById('status-text');
                const toggleButton = document.getElementById('toggle-button');
                
                const dimensionName = getDimensionDisplayName(currentDimension);
                
                if (status.scanning) {
                  if (statusEl) {
                    statusEl.className = 'status active';
                  }
                  if (statusText) {
                    statusText.textContent = `Scanning Active - Server: ${status.serverAddress} - Dimension: ${dimensionName}`;
                  }
                  if (toggleButton) {
                    toggleButton.textContent = 'Stop Scanning';
                    toggleButton.className = 'toggle-button stop';
                  }
                } else {
                  if (statusEl) {
                    statusEl.className = 'status inactive';
                  }
                  if (statusText) {
                    statusText.textContent = `Scanning Inactive - Server: ${status.serverAddress} - Dimension: ${dimensionName}`;
                  }
                  if (toggleButton) {
                    toggleButton.textContent = 'Start Scanning';
                    toggleButton.className = 'toggle-button';
                  }
                }
                
                const totalBlocksEl = document.getElementById('total-blocks');
                const totalChunksEl = document.getElementById('total-chunks');
                if (totalBlocksEl) {
                  totalBlocksEl.textContent = status.totalBlocksFound;
                }
                if (totalChunksEl) {
                  totalChunksEl.textContent = status.totalChunksScanned;
                }
                updateBlockCounts(status.blockCounts || {});
              }
              
              function setStatusError(message) {
                const statusEl = document.getElementById('status');
                const statusText = document.getElementById('status-text');
                const toggleButton = document.getElementById('toggle-button');
            
                if (statusEl) {
                  statusEl.className = 'status inactive';
                }
                if (statusText) {
                  statusText.textContent = message;
                }
                if (toggleButton) {
                  toggleButton.disabled = true;
                }
              }
              
              function updateBlockCounts(blockCounts) {
                const countsEl = document.getElementById('block-counts');
                const configured = Array.isArray(configData?.targetBlocks) ? configData.targetBlocks : [];
                const keys = configured.length > 0 ? configured : Object.keys(blockCounts);
            
                if (!countsEl) {
                  return;
                }
            
                if (keys.length === 0) {
                  countsEl.innerHTML = '<div style="color: #888;">No target blocks configured.</div>';
                  return;
                }
            
                const rows = keys.map(key => {
                  const count = blockCounts[key] || 0;
                  return `
                    <div class="block-count-row">
                      <span>${formatBlockLabel(key)}</span>
                      <span>${count}</span>
                    </div>
                  `;
                }).join('');
                countsEl.innerHTML = rows;
              }
            
              function updateBlockList() {
                const blockList = document.getElementById('block-list');
                const signList = document.getElementById('sign-list');
            
                if (blocksData.length === 0) {
                  if (blockList) {
                    blockList.innerHTML = '<div style="text-align: center; color: #888;">No blocks found yet</div>';
                  }
                  if (signList) {
                    signList.innerHTML = '<div style="text-align: center; color: #888;">No signs found yet</div>';
                  }
                  return;
                }
            
                const groupedBlocks = {};
                const groupedSigns = {};
                blocksData.forEach(block => {
                  if (isSignBlock(block.blockType)) {
                    if (!groupedSigns[block.blockType]) {
                      groupedSigns[block.blockType] = [];
                    }
                    groupedSigns[block.blockType].push(block);
                    return;
                  }
            
                  if (!groupedBlocks[block.blockType]) {
                    groupedBlocks[block.blockType] = [];
                  }
                  groupedBlocks[block.blockType].push(block);
                });
            
                if (blockList) {
                  blockList.innerHTML = renderGroupedBlocks(groupedBlocks, 'No non-sign blocks found yet');
                }
                if (signList) {
                  signList.innerHTML = renderGroupedBlocks(groupedSigns, 'No signs found yet');
                }
              }
            
              function renderGroupedBlocks(groupedBlocks, emptyMessage) {
                const keys = Object.keys(groupedBlocks).sort();
                if (keys.length === 0) {
                  return `<div style="text-align: center; color: #888;">${emptyMessage}</div>`;
                }
            
                let html = '';
                keys.forEach(blockType => {
                  html += `<h3>${formatBlockLabel(blockType)}</h3>`;
                  groupedBlocks[blockType].forEach(block => {
                    const teleportCmd = `/tp ${block.x} ${block.y} ${block.z}`;
                    const signText = formatSignText(block.signText);
                    html += `
                      <div class="block-item" data-tp="${escapeHtml(teleportCmd)}">
                        <div class="block-type">${escapeHtml(block.blockType)}</div>
                        <div class="coordinates">${block.x}, ${block.y}, ${block.z}</div>
                        <div style="font-size: 12px; color: #888;">${escapeHtml(block.dimension)}</div>
                        ${signText}
                      </div>
                    `;
                  });
                });
                return html;
              }
              
              function updateChunkMap() {
                const chunkMap = document.getElementById('chunk-map');
                
                const dimensionChunks = chunksData.filter(chunk => chunk.dimension === currentDimension);
                const dimensionSkipped = skippedChunksData.filter(chunk => chunk.dimension === currentDimension);
                const hasPlayer = playerData.dimension === currentDimension;
                const allChunks = dimensionChunks.concat(dimensionSkipped);
                
                if (!chunkMap) {
                  return;
                }
                
                if (allChunks.length === 0 && !hasPlayer) {
                  chunkMap.innerHTML = '<div style="text-align: center; color: #888; grid-column: 1 / -1;">No chunks scanned in this dimension</div>';
                  return;
                }
                
                let minX;
                let maxX;
                let minZ;
                let maxZ;
                if (allChunks.length > 0) {
                  minX = Math.min(...allChunks.map(c => c.chunkX));
                  maxX = Math.max(...allChunks.map(c => c.chunkX));
                  minZ = Math.min(...allChunks.map(c => c.chunkZ));
                  maxZ = Math.max(...allChunks.map(c => c.chunkZ));
                } else if (hasPlayer) {
                  minX = playerData.chunkX;
                  maxX = playerData.chunkX;
                  minZ = playerData.chunkZ;
                  maxZ = playerData.chunkZ;
                }
                
                if (hasPlayer) {
                  minX = Math.min(minX, playerData.chunkX);
                  maxX = Math.max(maxX, playerData.chunkX);
                  minZ = Math.min(minZ, playerData.chunkZ);
                  maxZ = Math.max(maxZ, playerData.chunkZ);
                }
                
                const maxViewRadius = 32;
                let centerX = Math.round((minX + maxX) / 2);
                let centerZ = Math.round((minZ + maxZ) / 2);
                if (hasPlayer) {
                  centerX = playerData.chunkX;
                  centerZ = playerData.chunkZ;
                }
                minX = centerX - maxViewRadius;
                maxX = centerX + maxViewRadius;
                minZ = centerZ - maxViewRadius;
                maxZ = centerZ + maxViewRadius;
                
                const width = maxX - minX + 1;
                chunkMap.style.gridTemplateColumns = `repeat(${width}, 1fr)`;
                
                let html = '';
                for (let z = minZ; z <= maxZ; z++) {
                  for (let x = minX; x <= maxX; x++) {
                    const isScanned = dimensionChunks.some(c => c.chunkX === x && c.chunkZ === z);
                    const isSkipped = dimensionSkipped.some(c => c.chunkX === x && c.chunkZ === z);
                    const isPlayer = playerData.dimension === currentDimension && 
                      playerData.chunkX === x && playerData.chunkZ === z;
                    let className = 'chunk-cell';
                    if (isPlayer) className += ' player';
                    else if (isScanned) className += ' scanned';
                    else if (isSkipped) className += ' skipped';
                    
                    html += `<div class="${className}" title="Chunk ${x}, ${z}"></div>`;
                  }
                }
                
                  chunkMap.innerHTML = html;
                  const playerCell = chunkMap.querySelector('.chunk-cell.player');
                  if (playerCell && !centeredChunkMap[currentDimension]) {
                    centerInScrollContainer(chunkMap, playerCell);
                    centeredChunkMap[currentDimension] = true;
                  }
                }
              
              function switchDimension(dimension, event) {
                currentDimension = dimension;
                
                document.querySelectorAll('.tab').forEach(tab => {
                  tab.classList.remove('active');
                });
                if (event && event.currentTarget) {
                  event.currentTarget.classList.add('active');
                }
                
                updateChunkMap();
              }
              
              function updateDimensionTabs() {
                document.querySelectorAll('.tab').forEach(tab => {
                  tab.classList.remove('active');
                  if (tab.textContent.toLowerCase().includes(getDimensionDisplayName(currentDimension).toLowerCase())) {
                    tab.classList.add('active');
                  }
                });
              }
              
              function getDimensionDisplayName(dimension) {
                switch (dimension) {
                  case 'minecraft:overworld': return 'Overworld';
                  case 'minecraft:the_nether': return 'Nether';
                  case 'minecraft:the_end': return 'End';
                  default: return dimension.replace('minecraft:', '');
                }
              }
              
              function formatBlockLabel(blockType) {
                if (!blockType) {
                  return 'unknown';
                }
                return blockType.replace('minecraft:', '').replace(/_/g, ' ');
              }
              
              function isSignBlock(blockType) {
                if (!blockType) {
                  return false;
                }
                const lower = blockType.toLowerCase();
                return lower.endsWith('_sign') || lower.endsWith('_wall_sign') || lower.endsWith('_hanging_sign') || lower.endsWith('_wall_hanging_sign');
              }
              
              function escapeHtml(text) {
                if (!text) {
                  return '';
                }
                return text
                  .replace(/&/g, '&amp;')
                  .replace(/</g, '&lt;')
                  .replace(/>/g, '&gt;')
                  .replace(/\"/g, '&quot;')
                  .replace(/'/g, '&#039;');
              }

              function centerInScrollContainer(container, element) {
                if (!container || !element) {
                  return;
                }
                const targetLeft = element.offsetLeft - (container.clientWidth / 2) + (element.clientWidth / 2);
                const targetTop = element.offsetTop - (container.clientHeight / 2) + (element.clientHeight / 2);
                if (Number.isFinite(targetLeft)) {
                  container.scrollLeft = targetLeft;
                }
                if (Number.isFinite(targetTop)) {
                  container.scrollTop = targetTop;
                }
              }

              function formatSignText(signText) {
                if (!signText) {
                  return '';
                }
                return `<div class="sign-text">${escapeHtml(signText)}</div>`;
              }
              
              async function setScanSigns(enabled) {
                const previous = Boolean(configData?.scanSigns);
                const signsToggle = document.getElementById('signs-toggle');
                const signsPanel = document.getElementById('signs-panel');
                if (signsPanel) {
                  signsPanel.classList.toggle('hidden', !enabled);
                }
                try {
                  const response = await fetch(apiUrl('/api/config'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ scanSigns: Boolean(enabled) })
                  });
                  const payload = await response.json();
                  if (!response.ok) {
                    throw new Error(payload?.error || 'Failed to update sign scanning');
                  }
                  configData = payload;
                  updateConfigForm(configData);
                  await fetchData();
                } catch (error) {
                  console.error('Error updating sign scanning:', error);
                  if (signsToggle) {
                    signsToggle.checked = previous;
                  }
                  if (signsPanel) {
                    signsPanel.classList.toggle('hidden', !previous);
                  }
                  alert('Failed to update sign scanning. Please try again.');
                }
              }
               
              function setupConfigListeners() {
                const targetBlocksInput = document.getElementById('target-blocks');
                const signsToggle = document.getElementById('signs-toggle');
                const signsPanel = document.getElementById('signs-panel');
                const toggleButton = document.getElementById('toggle-button');
                const saveButton = document.getElementById('config-save');
                const clearButton = document.getElementById('clear-button');
                
                if (targetBlocksInput) {
                  targetBlocksInput.addEventListener('input', () => {
                    const blocks = parseTargetBlocks(targetBlocksInput.value);
                    updateBlockWarning(blocks);
                  });
                }
                
                if (toggleButton) {
                  toggleButton.addEventListener('click', toggleScanning);
                }
                if (saveButton) {
                  saveButton.addEventListener('click', saveConfig);
                }
                if (clearButton) {
                  clearButton.addEventListener('click', confirmClearData);
                }
                
                document.querySelectorAll('.tab').forEach(tab => {
                  tab.addEventListener('click', event => {
                    const dimension = event.currentTarget.dataset.dimension;
                    switchDimension(dimension, event);
                  });
                });
                
                if (signsToggle && signsPanel) {
                  signsToggle.addEventListener('change', () => setScanSigns(signsToggle.checked));
                  signsPanel.classList.toggle('hidden', !signsToggle.checked);
                }
                
                document.addEventListener('click', event => {
                  const item = event.target.closest && event.target.closest('.block-item');
                  if (!item) {
                    return;
                  }
                  const tp = item.getAttribute('data-tp');
                  if (!tp) {
                    return;
                  }
                  navigator.clipboard.writeText(tp).catch(err => {
                    console.error('Failed to copy to clipboard:', err);
                  });
                });
              }
              
              async function confirmClearData() {
                const statusEl = document.getElementById('clear-status');
                if (statusEl) {
                  statusEl.textContent = '';
                }
                if (!confirm('This will delete all scan data for this server.')) {
                  return;
                }
                if (!confirm('Are you really, really, really sure you want to do this?')) {
                  return;
                }
            
                if (statusEl) {
                  statusEl.textContent = 'Clearing...';
                }
                try {
                  const response = await fetch(apiUrl('/api/clear'), {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/json'
                    }
                  });
            
                  const payload = await response.json();
                  if (!response.ok) {
                    if (statusEl) {
                      statusEl.textContent = payload?.error || 'Failed to clear data';
                    }
                    return;
                  }
            
                  if (statusEl) {
                    statusEl.textContent = 'Cleared';
                  }
                  await fetchData();
                } catch (error) {
                  console.error('Error clearing data:', error);
                  if (statusEl) {
                    statusEl.textContent = 'Clear failed';
                  }
                }
              }
              
              async function toggleScanning() {
                const toggleButton = document.getElementById('toggle-button');
                const originalText = toggleButton?.textContent;
                
                if (toggleButton) {
                  toggleButton.disabled = true;
                  toggleButton.textContent = 'Please wait...';
                }
                
                try {
                  const response = await fetch(apiUrl('/api/toggle'), {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/json',
                    }
                  });
                  
                  if (response.ok) {
                    const result = await response.json();
                    console.log('Toggle result:', result.message, 'scanning=', result.scanning);
                    await fetchData();
                  } else {
                    console.error('Failed to toggle scanning:', response.statusText);
                    alert('Failed to toggle scanning. Please try again.');
                  }
                } catch (error) {
                  console.error('Error toggling scanning:', error);
                  alert('Error communicating with server. Please try again.');
                } finally {
                  if (toggleButton) {
                    toggleButton.disabled = false;
                    toggleButton.textContent = originalText;
                  }
                }
              }
              
              setupConfigListeners();
              fetchData();
              fetchConfig();
              setInterval(fetchData, 3000);
            }
            """;
    }
}
