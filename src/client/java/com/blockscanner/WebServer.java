package com.blockscanner;

import com.blockscanner.data.ConfigPersistence;
import com.blockscanner.data.ScanConfig;
import com.blockscanner.data.ScanDataStore;
import com.blockscanner.data.ScanResult;
import com.blockscanner.data.ScannedChunk;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

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
    private final Gson gson;
    
    public WebServer(int port, ScanDataStore dataStore, ScanController scanController, ConfigPersistence configPersistence) {
        this.port = port;
        this.dataStore = dataStore;
        this.scanController = scanController;
        this.configPersistence = configPersistence;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * Starts the HTTP server on the configured port.
     * 
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", this::handleRoot);
        server.createContext("/api/blocks", this::handleBlocks);
        server.createContext("/api/chunks", this::handleChunks);
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/player", this::handlePlayer);
        server.createContext("/api/toggle", this::handleToggle);
        server.createContext("/api/config", this::handleConfig);
        
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
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "text/html", html);
    }
    
    /**
     * Handles requests to /api/blocks - returns found blocks as JSON.
     */
    private void handleBlocks(HttpExchange exchange) throws IOException {
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
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        
        Set<ScannedChunk> chunks = dataStore.getScannedChunks();
        String json = gson.toJson(chunks);
        
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/json", json);
    }
    
    /**
     * Handles requests to /api/status - returns scanning status and counts.
     */
    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        
        Map<String, Object> status = new HashMap<>();
        status.put("scanning", scanController.isActive());
        status.put("serverAddress", dataStore.getCurrentServer() != null ? dataStore.getCurrentServer() : "unknown");
        if (scanController.getSearchOriginChunk() != null) {
            status.put("searchOriginChunkX", scanController.getSearchOriginChunk().x);
            status.put("searchOriginChunkZ", scanController.getSearchOriginChunk().z);
        }
        if (scanController.getLastTargetChunk() != null) {
            status.put("targetChunkX", scanController.getLastTargetChunk().x);
            status.put("targetChunkZ", scanController.getLastTargetChunk().z);
        }
        
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
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        
        scanController.toggle();
        
        Map<String, Object> response = new HashMap<>();
        response.put("scanning", scanController.isActive());
        response.put("message", scanController.isActive() ? "Scanning started" : "Scanning stopped");
        
        String json = gson.toJson(response);
        
        addCorsHeaders(exchange);
        sendResponse(exchange, 200, "application/json", json);
    }
    
    /**
     * Handles requests to /api/config - gets or updates scanning configuration.
     */
    private void handleConfig(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
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
        Integer searchAreaBlocks = null;
        if (payload != null && payload.has("searchAreaBlocks")) {
            searchAreaBlocks = toInteger(payload.get("searchAreaBlocks"));
        }

        String error = scanController.updateConfig(searchAreaBlocks);
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

    private String readRequestBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        return new String(body, StandardCharsets.UTF_8);
    }

    private Integer toInteger(com.google.gson.JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive prim = value.getAsJsonPrimitive();
            if (prim.isNumber()) {
                return prim.getAsInt();
            }
            if (prim.isString()) {
                try {
                    return Integer.parseInt(prim.getAsString().trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Map<String, Object> buildConfigResponse() {
        Map<String, Object> config = new HashMap<>();
        config.put("searchAreaBlocks", scanController.getSearchAreaBlocks());
        config.put("searchAreaChunkRadius", scanController.getSearchAreaChunkRadius());
        config.put("minSearchAreaBlocks", scanController.getMinSearchAreaBlocks());
        config.put("maxSearchAreaBlocks", scanController.getMaxSearchAreaBlocks());
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
                    .chunk-cell.player {
                        background-color: #2196F3;
                        border: 2px solid #ffffff;
                    }
                    .chunk-cell.target {
                        background-color: #f5a623;
                        border: 2px solid #1a1a1a;
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
                    .config-row input {
                        width: 140px;
                        padding: 6px 8px;
                        border-radius: 4px;
                        border: 1px solid #444;
                        background-color: #1f1f1f;
                        color: #ffffff;
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
                    .config-warning {
                        font-size: 12px;
                        color: #f5a623;
                        min-height: 16px;
                    }
                        font-size: 12px;
                        color: #888888;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Block Scanner Visualization</h1>
                        <div id="status" class="status">
                            <span id="status-text">Loading...</span>
                            <button id="toggle-button" class="toggle-button" onclick="toggleScanning()">
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
                                <div class="stat">
                                    <div id="barrier-count" class="stat-value">0</div>
                                    <div class="stat-label">Barriers</div>
                                </div>
                                <div class="stat">
                                    <div id="command-count" class="stat-value">0</div>
                                    <div class="stat-label">Command Blocks</div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="panel">
                            <h2>Chunk Coverage</h2>
                            <div class="dimension-tabs">
                                <button class="tab active" onclick="switchDimension('minecraft:overworld')">Overworld</button>
                                <button class="tab" onclick="switchDimension('minecraft:the_nether')">Nether</button>
                                <button class="tab" onclick="switchDimension('minecraft:the_end')">End</button>
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

                    <div class="panel config-panel">
                        <h2>Scan Settings</h2>
                        <div class="config-form">
                            <div class="config-row">
                                <label for="search-area">Search area width (blocks)</label>
                                <input id="search-area" type="number" min="16" max="200000" step="16" value="10000" />
                            </div>
                            <div class="config-note" id="search-radius-note">Search radius: 0 chunks</div>
                            <div class="config-warning" id="config-warning"></div>
                            <div class="config-actions">
                                <button class="config-save" onclick="saveConfig()">Save Settings</button>
                                <div class="config-status" id="config-status"></div>
                            </div>
                        </div>
                    </div>
                </div>

                <script>
                    let currentDimension = 'minecraft:overworld';
                    let blocksData = [];
                    let chunksData = [];
                    let playerData = {};
                    let configData = {};
                    let statusData = {};
                    
                    async function fetchData() {
                        try {
                            const [statusRes, blocksRes, chunksRes, playerRes] = await Promise.all([
                                fetch('/api/status'),
                                fetch('/api/blocks'),
                                fetch('/api/chunks'),
                                fetch('/api/player')
                            ]);
                            
                            statusData = await statusRes.json();
                            blocksData = await blocksRes.json();
                            chunksData = await chunksRes.json();
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
                        }
                    }
                    
                    async function fetchConfig() {
                        try {
                            const configRes = await fetch('/api/config');
                            configData = await configRes.json();
                            updateConfigForm(configData);
                        } catch (error) {
                            console.error('Error fetching config:', error);
                        }
                    }

                    function getConfigLimits(config) {
                        return {
                            minSearchArea: config?.minSearchAreaBlocks ?? 16,
                            maxSearchArea: config?.maxSearchAreaBlocks ?? 200000
                        };
                    }

                    function updateConfigForm(config) {
                        const searchAreaInput = document.getElementById('search-area');
                        const searchRadiusNote = document.getElementById('search-radius-note');

                        if (!config) {
                            return;
                        }

                        const limits = getConfigLimits(config);
                        searchAreaInput.min = limits.minSearchArea;
                        searchAreaInput.max = limits.maxSearchArea;

                        searchAreaInput.value = config.searchAreaBlocks ?? searchAreaInput.value;
                        if (typeof config.searchAreaChunkRadius === 'number') {
                            searchRadiusNote.textContent = `Search radius: ${config.searchAreaChunkRadius} chunks`;
                        }

                        updateConfigWarning(
                            parseInt(searchAreaInput.value, 10),
                            limits
                        );
                    }

                    function updateConfigWarning(searchAreaBlocks, limits) {
                        const warningEl = document.getElementById('config-warning');
                        const warnings = [];

                        if (Number.isFinite(searchAreaBlocks) && searchAreaBlocks > Math.max(64000, limits.maxSearchArea / 2)) {
                            warnings.push('Large search areas will take a long time to complete.');
                        }

                        warningEl.textContent = warnings.join(' ');
                    }

                    function validateConfigInputs(searchAreaBlocks, limits) {
                        if (!Number.isFinite(searchAreaBlocks) || !Number.isInteger(searchAreaBlocks)) {
                            return 'Search area must be a whole number.';
                        }
                        if (searchAreaBlocks < limits.minSearchArea || searchAreaBlocks > limits.maxSearchArea) {
                            return `Search area must be between ${limits.minSearchArea} and ${limits.maxSearchArea}.`;
                        }
                        if (searchAreaBlocks % 16 !== 0) {
                            return 'Search area must be a multiple of 16.';
                        }
                        return null;
                    }

                    async function saveConfig() {
                        const statusEl = document.getElementById('config-status');
                        const warningEl = document.getElementById('config-warning');
                        statusEl.textContent = 'Saving...';

                        const searchAreaBlocks = parseInt(document.getElementById('search-area').value, 10);
                        const limits = getConfigLimits(configData);

                        const validationError = validateConfigInputs(searchAreaBlocks, limits);
                        if (validationError) {
                            statusEl.textContent = validationError;
                            updateConfigWarning(searchAreaBlocks, limits);
                            return;
                        }

                        try {
                            const response = await fetch('/api/config', {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json'
                                },
                                body: JSON.stringify({
                                    searchAreaBlocks: searchAreaBlocks
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
                        
                        const targetText = status.targetChunkX !== undefined ? ` - Target: ${status.targetChunkX}, ${status.targetChunkZ}` : "";
                        
                        if (status.scanning) {
                            statusEl.className = 'status active';
                            statusText.textContent = `Scanning Active - Server: ${status.serverAddress} - Dimension: ${dimensionName}${targetText}`;
                            toggleButton.textContent = 'Stop Scanning';
                            toggleButton.className = 'toggle-button stop';
                        } else {
                            statusEl.className = 'status inactive';
                            statusText.textContent = `Scanning Inactive - Server: ${status.serverAddress} - Dimension: ${dimensionName}${targetText}`;
                            toggleButton.textContent = 'Start Scanning';
                            toggleButton.className = 'toggle-button';
                        }
                        
                        document.getElementById('total-blocks').textContent = status.totalBlocksFound;
                        document.getElementById('barrier-count').textContent = status.blockCounts.barrier || 0;
                        
                        const commandCount = (status.blockCounts.command_block || 0) + 
                                           (status.blockCounts.chain_command_block || 0) + 
                                           (status.blockCounts.repeating_command_block || 0);
                        document.getElementById('command-count').textContent = commandCount;
                        document.getElementById('total-chunks').textContent = status.totalChunksScanned;
                    }
                    
                    function updateBlockList() {
                        const blockList = document.getElementById('block-list');
                        
                        if (blocksData.length === 0) {
                            blockList.innerHTML = '<div style="text-align: center; color: #888;">No blocks found yet</div>';
                            return;
                        }
                        
                        const groupedBlocks = {};
                        blocksData.forEach(block => {
                            if (!groupedBlocks[block.blockType]) {
                                groupedBlocks[block.blockType] = [];
                            }
                            groupedBlocks[block.blockType].push(block);
                        });
                        
                        let html = '';
                        Object.keys(groupedBlocks).sort().forEach(blockType => {
                            html += `<h3>${blockType.replace('_', ' ').toUpperCase()}</h3>`;
                            groupedBlocks[blockType].forEach(block => {
                                const teleportCmd = `/tp ${block.x} ${block.y} ${block.z}`;
                                html += `
                                    <div class="block-item" onclick="copyToClipboard('${teleportCmd}')">
                                        <div class="block-type">${block.blockType}</div>
                                        <div class="coordinates">${block.x}, ${block.y}, ${block.z}</div>
                                        <div style="font-size: 12px; color: #888;">${block.dimension}</div>
                                    </div>
                                `;
                            });
                        });
                        
                        blockList.innerHTML = html;
                    }
                    
                    function updateChunkMap() {
                        const chunkMap = document.getElementById('chunk-map');
                        
                        const dimensionChunks = chunksData.filter(chunk => chunk.dimension === currentDimension);
                        const hasPlayer = playerData.dimension === currentDimension;
                        const hasTarget = statusData.targetChunkX !== undefined && statusData.targetChunkZ !== undefined;
                        
                        if (dimensionChunks.length === 0 && !hasPlayer && !hasTarget) {
                            chunkMap.innerHTML = '<div style="text-align: center; color: #888; grid-column: 1 / -1;">No chunks scanned in this dimension</div>';
                            return;
                        }
                        
                        let minX;
                        let maxX;
                        let minZ;
                        let maxZ;
                        if (dimensionChunks.length > 0) {
                            minX = Math.min(...dimensionChunks.map(c => c.chunkX));
                            maxX = Math.max(...dimensionChunks.map(c => c.chunkX));
                            minZ = Math.min(...dimensionChunks.map(c => c.chunkZ));
                            maxZ = Math.max(...dimensionChunks.map(c => c.chunkZ));
                        } else if (hasPlayer) {
                            minX = playerData.chunkX;
                            maxX = playerData.chunkX;
                            minZ = playerData.chunkZ;
                            maxZ = playerData.chunkZ;
                        } else {
                            minX = statusData.targetChunkX;
                            maxX = statusData.targetChunkX;
                            minZ = statusData.targetChunkZ;
                            maxZ = statusData.targetChunkZ;
                        }
                        
                        if (hasPlayer) {
                            minX = Math.min(minX, playerData.chunkX);
                            maxX = Math.max(maxX, playerData.chunkX);
                            minZ = Math.min(minZ, playerData.chunkZ);
                            maxZ = Math.max(maxZ, playerData.chunkZ);
                        }
                        
                        if (statusData.targetChunkX !== undefined && statusData.targetChunkZ !== undefined) {
                            minX = Math.min(minX, statusData.targetChunkX);
                            maxX = Math.max(maxX, statusData.targetChunkX);
                            minZ = Math.min(minZ, statusData.targetChunkZ);
                            maxZ = Math.max(maxZ, statusData.targetChunkZ);
                        }

                        const maxViewRadius = 32;
                        let centerX = Math.round((minX + maxX) / 2);
                        let centerZ = Math.round((minZ + maxZ) / 2);
                        if (hasPlayer) {
                            centerX = playerData.chunkX;
                            centerZ = playerData.chunkZ;
                        } else if (hasTarget) {
                            centerX = statusData.targetChunkX;
                            centerZ = statusData.targetChunkZ;
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
                                const isPlayer = playerData.dimension === currentDimension && 
                                               playerData.chunkX === x && playerData.chunkZ === z;
                                const isTarget = statusData.targetChunkX === x && statusData.targetChunkZ === z;
                                
                                let className = 'chunk-cell';
                                if (isTarget) className += ' target';
                                else if (isPlayer) className += ' player';
                                else if (isScanned) className += ' scanned';
                                
                                html += `<div class="${className}" title="Chunk ${x}, ${z}"></div>`;
                            }
                        }
                        
                        chunkMap.innerHTML = html;
                        const playerCell = chunkMap.querySelector('.chunk-cell.player');
                        if (playerCell) {
                            playerCell.scrollIntoView({ block: 'center', inline: 'center' });
                        }
                    }
                    
                    function switchDimension(dimension) {
                        currentDimension = dimension;
                        
                        document.querySelectorAll('.tab').forEach(tab => {
                            tab.classList.remove('active');
                        });
                        event.target.classList.add('active');
                        
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
                        const searchAreaInput = document.getElementById('search-area');
                        const handler = () => {
                            const limits = getConfigLimits(configData);
                            updateConfigWarning(
                                parseInt(searchAreaInput.value, 10),
                                limits
                            );
                        };

                        searchAreaInput.addEventListener('input', handler);
                    }

                    async function toggleScanning() {
                        const toggleButton = document.getElementById('toggle-button');
                        const originalText = toggleButton.textContent;
                        
                        toggleButton.disabled = true;
                        toggleButton.textContent = 'Please wait...';
                        
                        try {
                            const response = await fetch('/api/toggle', {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json',
                                }
                            });
                            
                            if (response.ok) {
                                const result = await response.json();
                                console.log('Toggle result:', result.message);
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
                        }
                    }
                    
                    setupConfigListeners();
                    fetchData();
                    fetchConfig();
                    setInterval(fetchData, 3000);
                </script>
            </body>
            </html>
            """;
    }
}
