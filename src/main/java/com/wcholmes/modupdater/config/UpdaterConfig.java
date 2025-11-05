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

    // Flag indicating if this config was received from server (transient = not saved to JSON)
    private transient boolean serverProvided = false;

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
     * Loads the configuration from disk, or creates a minimal default if it doesn't exist.
     * On clients, config file is optional - server will push config when player joins.
     * @return the loaded or default configuration
     */
    private static UpdaterConfig load() {
        File configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            LOGGER.info("Config file not found. On server, create config file. On client, server will push config.");
            UpdaterConfig defaultConfig = createMinimal();
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
            LOGGER.error("Failed to load config from {}, using minimal config", CONFIG_FILE, e);
            return createMinimal();
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
     * Creates a minimal configuration with no managed mods.
     * Used on clients when no config file exists (server will push config).
     * @return the minimal configuration
     */
    private static UpdaterConfig createMinimal() {
        UpdaterConfig config = new UpdaterConfig();
        // Empty managed mods list - will be populated by server
        return config;
    }

    /**
     * Applies configuration received from server.
     * This allows drop-and-go deployment where clients don't need config files.
     * @param managedMods the managed mods from server
     * @param autoDownload auto download setting from server
     * @param autoInstall auto install setting from server
     * @param checkIntervalMinutes check interval from server
     * @param downloadTimeoutSeconds download timeout from server
     */
    public void applyServerConfig(List<ManagedModConfig> managedMods,
                                  boolean autoDownload,
                                  boolean autoInstall,
                                  int checkIntervalMinutes,
                                  int downloadTimeoutSeconds) {
        LOGGER.info("Applying configuration from server: {} managed mods", managedMods.size());

        this.managedMods = new ArrayList<>(managedMods);
        this.autoDownload = autoDownload;
        this.autoInstall = autoInstall;
        this.checkIntervalMinutes = checkIntervalMinutes;
        this.downloadTimeoutSeconds = downloadTimeoutSeconds;
        this.serverProvided = true;

        // Don't save server-provided config to disk
    }

    /**
     * Checks if this configuration was provided by the server.
     * @return true if server-provided, false if local config
     */
    public boolean isServerProvided() {
        return serverProvided;
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
