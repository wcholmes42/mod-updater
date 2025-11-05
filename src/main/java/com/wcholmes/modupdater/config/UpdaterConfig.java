package com.wcholmes.modupdater.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main configuration for the Mod Updater.
 * Loads from config/modupdater.json
 */
public class UpdaterConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "config/modupdater.json";

    // Configuration fields
    private boolean enabled = true;
    private boolean autoDownload = true;
    private boolean autoInstall = true;
    private boolean checkOnStartup = true;
    private boolean checkOnServerJoin = true;
    private int checkIntervalMinutes = 60;
    private int downloadTimeoutSeconds = 30;
    private boolean verboseLogging = false;
    private boolean backupOldVersions = false;
    private List<ManagedModConfig> managedMods = new ArrayList<>();

    // Singleton instance
    private static UpdaterConfig instance;

    // Private constructor
    private UpdaterConfig() {}

    /**
     * Gets the singleton config instance.
     * @return the config instance
     */
    public static UpdaterConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * Loads the configuration from disk, or creates a default if it doesn't exist.
     * @return the loaded or default configuration
     */
    private static UpdaterConfig load() {
        File configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            LOGGER.info("Config file not found, creating default: {}", CONFIG_FILE);
            UpdaterConfig defaultConfig = createDefault();
            defaultConfig.save();
            return defaultConfig;
        }

        try (FileReader reader = new FileReader(configFile)) {
            UpdaterConfig config = GSON.fromJson(reader, UpdaterConfig.class);
            LOGGER.info("Loaded configuration from {}", CONFIG_FILE);

            // Validate managed mods
            config.managedMods.removeIf(mod -> {
                if (!mod.isValid()) {
                    LOGGER.warn("Removing invalid mod config: {}", mod);
                    return true;
                }
                return false;
            });

            return config;
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load config from {}, using default", CONFIG_FILE, e);
            return createDefault();
        }
    }

    /**
     * Creates a default configuration with example mods.
     * @return the default configuration
     */
    private static UpdaterConfig createDefault() {
        UpdaterConfig config = new UpdaterConfig();

        // Add example mod configuration
        ManagedModConfig exampleMod = new ManagedModConfig();
        exampleMod.setModId("landscaper");
        exampleMod.setGithubRepo("wcholmes42/minecraft-landscaper");
        exampleMod.setJarPattern("landscaper-{version}.jar");
        exampleMod.setEnabled(false); // Disabled by default in example

        config.managedMods.add(exampleMod);

        return config;
    }

    /**
     * Saves the current configuration to disk.
     */
    public void save() {
        File configFile = new File(CONFIG_FILE);

        // Create config directory if it doesn't exist
        configFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(this, writer);
            LOGGER.info("Saved configuration to {}", CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.error("Failed to save config to {}", CONFIG_FILE, e);
        }
    }

    /**
     * Reloads the configuration from disk.
     */
    public static void reload() {
        instance = load();
    }

    /**
     * Adds a managed mod to the configuration.
     * @param modConfig the mod configuration to add
     */
    public void addManagedMod(ManagedModConfig modConfig) {
        if (modConfig.isValid()) {
            // Remove existing config for same mod if present
            managedMods.removeIf(m -> m.getModId().equals(modConfig.getModId()));
            managedMods.add(modConfig);
            save();
        } else {
            LOGGER.warn("Attempted to add invalid mod config: {}", modConfig);
        }
    }

    /**
     * Gets all enabled managed mods.
     * @return list of enabled managed mod configurations
     */
    public List<ManagedModConfig> getEnabledMods() {
        return managedMods.stream()
                .filter(ManagedModConfig::isEnabled)
                .toList();
    }

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoDownload() {
        return autoDownload;
    }

    public void setAutoDownload(boolean autoDownload) {
        this.autoDownload = autoDownload;
    }

    public boolean isAutoInstall() {
        return autoInstall;
    }

    public void setAutoInstall(boolean autoInstall) {
        this.autoInstall = autoInstall;
    }

    public boolean isCheckOnStartup() {
        return checkOnStartup;
    }

    public void setCheckOnStartup(boolean checkOnStartup) {
        this.checkOnStartup = checkOnStartup;
    }

    public boolean isCheckOnServerJoin() {
        return checkOnServerJoin;
    }

    public void setCheckOnServerJoin(boolean checkOnServerJoin) {
        this.checkOnServerJoin = checkOnServerJoin;
    }

    public int getCheckIntervalMinutes() {
        return checkIntervalMinutes;
    }

    public void setCheckIntervalMinutes(int checkIntervalMinutes) {
        this.checkIntervalMinutes = checkIntervalMinutes;
    }

    public int getDownloadTimeoutSeconds() {
        return downloadTimeoutSeconds;
    }

    public void setDownloadTimeoutSeconds(int downloadTimeoutSeconds) {
        this.downloadTimeoutSeconds = downloadTimeoutSeconds;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    public boolean isBackupOldVersions() {
        return backupOldVersions;
    }

    public void setBackupOldVersions(boolean backupOldVersions) {
        this.backupOldVersions = backupOldVersions;
    }

    public List<ManagedModConfig> getManagedMods() {
        return new ArrayList<>(managedMods);
    }

    public void setManagedMods(List<ManagedModConfig> managedMods) {
        this.managedMods = managedMods;
    }
}
