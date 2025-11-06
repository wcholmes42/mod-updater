package com.wcholmes.modupdater.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.wcholmes.modupdater.util.GsonProvider;
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
    private static final Gson GSON = GsonProvider.getGson();
    private static final String CONFIG_FILE = "config/modupdater.json";

    // Configuration fields
    private ConfigSource configSource = null; // Points to GitHub location of real config
    private boolean enabled = true;
    private boolean autoDownload = true;
    private boolean autoInstall = true;
    private boolean checkOnStartup = true;
    private boolean checkOnServerJoin = true;
    private boolean periodicCheckEnabled = false; // Periodic update checks (defaults to off)
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

            // Check if this is a bootstrap config that needs to fetch from GitHub
            if (config.configSource != null && config.configSource.isValid()) {
                // Validate bootstrap config source
                ConfigValidator.ValidationResult sourceValidation = ConfigValidator.validateConfigSource(config.configSource);
                if (sourceValidation.hasErrors()) {
                    LOGGER.error("Bootstrap config source validation failed:");
                    sourceValidation.logResults(LOGGER);
                    LOGGER.error("ModUpdater will be disabled for this session.");
                    UpdaterConfig disabledConfig = createMinimal();
                    disabledConfig.enabled = false;
                    return disabledConfig;
                }
                sourceValidation.logResults(LOGGER);
                LOGGER.info("Bootstrap config detected: {}", config.configSource);
                UpdaterConfig remoteConfig = fetchRemoteConfig(config.configSource);
                if (remoteConfig != null) {
                    return remoteConfig;
                } else {
                    LOGGER.error("Failed to fetch remote config from GitHub. ModUpdater will be disabled for this session.");
                    LOGGER.error("To fix: Check network connection and restart server, or use /modupdater server command when available.");
                    UpdaterConfig disabledConfig = createMinimal();
                    disabledConfig.enabled = false;
                    return disabledConfig;
                }
            }

            // Validate configuration
            ConfigValidator.ValidationResult validation = ConfigValidator.validate(config);
            validation.logResults(LOGGER);

            if (validation.hasErrors()) {
                LOGGER.error("Configuration validation failed. ModUpdater will be disabled for this session.");
                UpdaterConfig disabledConfig = createMinimal();
                disabledConfig.enabled = false;
                return disabledConfig;
            }

            return config;
        } catch (JsonSyntaxException e) {
            LOGGER.error("Failed to parse JSON in config file");
            JsonSyntaxHelper.diagnoseJsonError(configFile, e, LOGGER);
            LOGGER.error("ModUpdater will use minimal config and be disabled.");
            LOGGER.error("Please fix the JSON syntax errors and restart the server.");
            UpdaterConfig disabledConfig = createMinimal();
            disabledConfig.enabled = false;
            return disabledConfig;
        } catch (IOException e) {
            LOGGER.error("Failed to read config file from {}", CONFIG_FILE, e);
            LOGGER.error("Using minimal config");
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
     * Fetches the remote configuration from GitHub.
     * @param configSource the source to fetch from
     * @return the fetched configuration, or null if fetch failed
     */
    private static UpdaterConfig fetchRemoteConfig(ConfigSource configSource) {
        LOGGER.info("Fetching remote config from: {}/{} (branch: {})",
            configSource.getRepo(), configSource.getPath(), configSource.getBranch());

        try {
            com.wcholmes.modupdater.github.GitHubAPI api = new com.wcholmes.modupdater.github.GitHubAPI();
            String jsonContent = api.fetchRawFile(
                configSource.getRepo(),
                configSource.getPath(),
                configSource.getBranch()
            );

            if (jsonContent == null) {
                LOGGER.error("Failed to fetch remote config: GitHub returned null");
                return null;
            }

            UpdaterConfig config = GSON.fromJson(jsonContent, UpdaterConfig.class);
            if (config == null) {
                LOGGER.error("Failed to parse remote config: JSON parsing returned null");
                return null;
            }

            LOGGER.info("Successfully fetched and parsed remote config: {} managed mods", config.managedMods.size());

            // Validate configuration
            ConfigValidator.ValidationResult validation = ConfigValidator.validate(config);
            validation.logResults(LOGGER);

            if (validation.hasErrors()) {
                LOGGER.error("Remote configuration validation failed. Cannot use this config.");
                return null;
            }

            return config;
        } catch (JsonSyntaxException e) {
            LOGGER.error("=".repeat(70));
            LOGGER.error("JSON SYNTAX ERROR IN REMOTE CONFIG");
            LOGGER.error("Repository: {}/{}", configSource.getRepo(), configSource.getPath());
            LOGGER.error("Branch: {}", configSource.getBranch());
            LOGGER.error("=".repeat(70));
            LOGGER.error("Parse Error: {}", e.getMessage());
            LOGGER.error("");
            LOGGER.error("The pack config on GitHub has a JSON syntax error.");
            LOGGER.error("Please fix the JSON in the repository and try again.");
            LOGGER.error("");
            LOGGER.error("COMMON JSON MISTAKES:");
            LOGGER.error("  - Trailing commas after last property");
            LOGGER.error("  - Missing commas between properties");
            LOGGER.error("  - Using single quotes instead of double quotes");
            LOGGER.error("  - Comments (not allowed in JSON)");
            LOGGER.error("");
            LOGGER.error("TIP: Validate the config with jsonlint.com before committing");
            LOGGER.error("=".repeat(70));
            return null;
        } catch (Exception e) {
            LOGGER.error("Unexpected error fetching remote config", e);
            return null;
        }
    }

    /**
     * Refreshes the configuration by re-fetching from the remote source.
     * Only works if the current config has a configSource defined.
     * @return true if refresh was successful, false otherwise
     */
    public static boolean refreshConfig() {
        LOGGER.info("Manual config refresh requested");

        // First, reload the bootstrap config from disk
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            LOGGER.error("Cannot refresh: Bootstrap config file not found at {}", CONFIG_FILE);
            return false;
        }

        try (FileReader reader = new FileReader(configFile)) {
            UpdaterConfig bootstrapConfig = GSON.fromJson(reader, UpdaterConfig.class);

            if (bootstrapConfig.configSource == null || !bootstrapConfig.configSource.isValid()) {
                LOGGER.error("Cannot refresh: No valid configSource in bootstrap config");
                return false;
            }

            LOGGER.info("Refreshing from: {}", bootstrapConfig.configSource);
            UpdaterConfig newConfig = fetchRemoteConfig(bootstrapConfig.configSource);

            if (newConfig != null) {
                instance = newConfig;
                LOGGER.info("Config refresh successful");
                return true;
            } else {
                LOGGER.error("Config refresh failed");
                return false;
            }
        } catch (JsonSyntaxException e) {
            LOGGER.error("JSON syntax error in bootstrap config during refresh");
            JsonSyntaxHelper.diagnoseJsonError(configFile, e, LOGGER);
            return false;
        } catch (IOException e) {
            LOGGER.error("Failed to read bootstrap config file during refresh", e);
            return false;
        }
    }

    /**
     * Applies configuration received from server.
     * This allows drop-and-go deployment where clients don't need config files.
     * @param managedMods the managed mods from server
     * @param autoDownload auto download setting from server
     * @param autoInstall auto install setting from server
     * @param checkIntervalMinutes check interval from server
     * @param periodicCheckEnabled periodic check enabled setting from server
     * @param downloadTimeoutSeconds download timeout from server
     */
    public void applyServerConfig(List<ManagedModConfig> managedMods,
                                  boolean autoDownload,
                                  boolean autoInstall,
                                  int checkIntervalMinutes,
                                  boolean periodicCheckEnabled,
                                  int downloadTimeoutSeconds) {
        LOGGER.info("Applying configuration from server: {} managed mods", managedMods.size());

        this.managedMods = new ArrayList<>(managedMods);
        this.autoDownload = autoDownload;
        this.autoInstall = autoInstall;
        this.checkIntervalMinutes = checkIntervalMinutes;
        this.periodicCheckEnabled = periodicCheckEnabled;
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

    public boolean isPeriodicCheckEnabled() {
        return periodicCheckEnabled;
    }

    public void setPeriodicCheckEnabled(boolean periodicCheckEnabled) {
        this.periodicCheckEnabled = periodicCheckEnabled;
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

    public ConfigSource getConfigSource() {
        return configSource;
    }

    public void setConfigSource(ConfigSource configSource) {
        this.configSource = configSource;
    }

    /**
     * Configuration source that points to a remote config file (e.g., GitHub).
     * Used for bootstrap configs that fetch the real pack config from a remote location.
     */
    public static class ConfigSource {
        private String type = "github"; // Currently only "github" is supported
        private String repo; // Format: "owner/repo"
        private String path; // Path to config file in repo, e.g., "packs/family-pack.json"
        private String branch = "main"; // Branch to fetch from

        public ConfigSource() {}

        public ConfigSource(String repo, String path, String branch) {
            this.repo = repo;
            this.path = path;
            this.branch = branch;
        }

        public boolean isValid() {
            return repo != null && !repo.isEmpty()
                && path != null && !path.isEmpty()
                && branch != null && !branch.isEmpty()
                && "github".equals(type);
        }

        // Getters and setters
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getRepo() {
            return repo;
        }

        public void setRepo(String repo) {
            this.repo = repo;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getBranch() {
            return branch;
        }

        public void setBranch(String branch) {
            this.branch = branch;
        }

        @Override
        public String toString() {
            return String.format("ConfigSource{type=%s, repo=%s, path=%s, branch=%s}",
                type, repo, path, branch);
        }
    }
}
