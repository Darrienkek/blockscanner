package com.blockscanner.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Handles saving and loading scan configuration to/from JSON files.
 */
public class ConfigPersistence {
    private static final Path CONFIG_DIR = FabricLoader.getInstance()
        .getConfigDir().resolve("blockscanner");
    private static final String CONFIG_FILE_NAME = "scan-config.json";

    private final Gson gson;

    public ConfigPersistence() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    public ScanConfig load() throws IOException {
        Path configFile = getConfigFile();
        if (!Files.exists(configFile)) {
            return null;
        }

        String json = Files.readString(configFile);
        return gson.fromJson(json, ScanConfig.class);
    }

    public void save(ScanConfig config) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }

        Files.createDirectories(CONFIG_DIR);

        String json = gson.toJson(config);
        Path configFile = getConfigFile();
        Path tempFile = configFile.resolveSibling(configFile.getFileName() + ".tmp");

        Files.writeString(tempFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path getConfigFile() {
        return CONFIG_DIR.resolve(CONFIG_FILE_NAME);
    }
}
