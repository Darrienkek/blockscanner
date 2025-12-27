package com.blockscanner.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Handles saving and loading scan data to/from JSON files.
 * Organizes data by server address to keep results separate per server.
 */
public class DataPersistence {
    private static final Path CONFIG_DIR = FabricLoader.getInstance()
        .getConfigDir().resolve("blockscanner");
    
    private final Gson gson;

    public DataPersistence() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    /**
     * Saves the scan data store to a JSON file.
     * Creates the config directory if it doesn't exist.
     * 
     * @param dataStore The data store to save
     * @throws IOException if the file cannot be written
     */
    public void save(ScanDataStore dataStore) throws IOException {
        if (dataStore == null) {
            throw new IllegalArgumentException("dataStore cannot be null");
        }

        String serverAddress = dataStore.getCurrentServer();
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("Server address must be set before saving");
        }

        Files.createDirectories(CONFIG_DIR);

        ScanDataSnapshot snapshot = dataStore.getSnapshot();
        String json = gson.toJson(snapshot);

        Path dataFile = getServerDataFile(serverAddress);
        Path tempFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        
        Files.writeString(tempFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Loads scan data from a JSON file into the data store.
     * If the file doesn't exist or cannot be read, the data store remains unchanged.
     * 
     * @param dataStore The data store to load into
     * @param serverAddress The server address to load data for
     * @throws IOException if the file exists but cannot be read or parsed
     */
    public void load(ScanDataStore dataStore, String serverAddress) throws IOException {
        if (dataStore == null) {
            throw new IllegalArgumentException("dataStore cannot be null");
        }
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("serverAddress cannot be null or empty");
        }

        Path dataFile = getServerDataFile(serverAddress);
        
        if (!Files.exists(dataFile)) {
            return;
        }

        String json = Files.readString(dataFile);
        ScanDataSnapshot snapshot = gson.fromJson(json, ScanDataSnapshot.class);
        
        if (snapshot != null) {
            dataStore.loadFromSnapshot(snapshot);
        }
    }

    /**
     * Gets the file path for a server's data file.
     * Sanitizes the server address to create a safe filename.
     * 
     * @param serverAddress The server address
     * @return The path to the server's data file
     */
    public Path getServerDataFile(String serverAddress) {
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("serverAddress cannot be null or empty");
        }

        String sanitizedAddress = sanitizeServerAddress(serverAddress);
        return CONFIG_DIR.resolve(sanitizedAddress + ".json");
    }

    /**
     * Deletes the persisted data file for a server if it exists.
     *
     * @param serverAddress The server address to delete data for
     * @return true if a file was deleted, false otherwise
     * @throws IOException if the file cannot be deleted
     */
    public boolean delete(String serverAddress) throws IOException {
        Path dataFile = getServerDataFile(serverAddress);
        if (Files.exists(dataFile)) {
            Files.delete(dataFile);
            return true;
        }
        return false;
    }

    /**
     * Sanitizes a server address to create a safe filename.
     * Replaces invalid filename characters with underscores.
     * 
     * @param address The server address to sanitize
     * @return A safe filename string
     */
    protected String sanitizeServerAddress(String address) {
        if (address == null) {
            return "unknown";
        }
        
        String sanitized = address.replaceAll("[^a-zA-Z0-9._-]", "_")
            .replaceAll("_{2,}", "_")
            .replaceAll("^_+|_+$", "")
            .toLowerCase();
        return sanitized.isBlank() ? "unknown" : sanitized;
    }
}
