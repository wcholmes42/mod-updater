package com.wcholmes.modupdater.registry;

import com.wcholmes.modupdater.config.ManagedModConfig;
import com.wcholmes.modupdater.config.UpdaterConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Registry for all managed mods (from config + IMC registrations).
 */
public class ModRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static ModRegistry instance;

    private final Map<String, ManagedModConfig> registeredMods = new HashMap<>();

    private ModRegistry() {
        loadFromConfig();
    }

    /**
     * Gets the singleton registry instance.
     */
    public static ModRegistry getInstance() {
        if (instance == null) {
            instance = new ModRegistry();
        }
        return instance;
    }

    /**
     * Loads managed mods from configuration.
     */
    private void loadFromConfig() {
        UpdaterConfig config = UpdaterConfig.getInstance();
        List<ManagedModConfig> configMods = config.getManagedMods();

        for (ManagedModConfig mod : configMods) {
            if (mod.isValid()) {
                registeredMods.put(mod.getModId(), mod);
                LOGGER.debug("Registered mod from config: {}", mod.getModId());
            }
        }

        LOGGER.info("Loaded {} mods from configuration", registeredMods.size());
    }

    /**
     * Registers a mod programmatically (e.g., via IMC).
     *
     * @param modConfig the mod configuration to register
     * @return true if successfully registered
     */
    public boolean registerMod(ManagedModConfig modConfig) {
        if (!modConfig.isValid()) {
            LOGGER.warn("Attempted to register invalid mod config: {}", modConfig);
            return false;
        }

        registeredMods.put(modConfig.getModId(), modConfig);
        LOGGER.info("Registered mod via API: {}", modConfig.getModId());

        // Also add to config for persistence
        UpdaterConfig.getInstance().addManagedMod(modConfig);

        return true;
    }

    /**
     * Registers a mod from IMC data.
     *
     * @param data IMC data containing mod registration info
     * @return true if successfully registered
     */
    public boolean registerFromIMC(Map<String, Object> data) {
        try {
            String modId = (String) data.get("mod_id");
            String githubRepo = (String) data.get("github_repo");
            String jarPattern = (String) data.get("jar_pattern");

            if (modId == null || githubRepo == null || jarPattern == null) {
                LOGGER.warn("Incomplete IMC registration data: {}", data);
                return false;
            }

            ManagedModConfig config = new ManagedModConfig(modId, githubRepo, jarPattern);

            // Optional fields
            if (data.containsKey("enabled")) {
                config.setEnabled((Boolean) data.get("enabled"));
            }
            if (data.containsKey("min_version")) {
                config.setMinVersion((String) data.get("min_version"));
            }
            if (data.containsKey("update_channel")) {
                config.setUpdateChannel((String) data.get("update_channel"));
            }
            if (data.containsKey("required")) {
                config.setRequired((Boolean) data.get("required"));
            }

            return registerMod(config);

        } catch (ClassCastException | NullPointerException e) {
            LOGGER.error("Failed to parse IMC registration data: {}", data, e);
            return false;
        }
    }

    /**
     * Gets a registered mod by ID.
     */
    public ManagedModConfig getMod(String modId) {
        return registeredMods.get(modId);
    }

    /**
     * Gets all registered mods.
     */
    public Collection<ManagedModConfig> getAllMods() {
        return new ArrayList<>(registeredMods.values());
    }

    /**
     * Gets all enabled mods.
     */
    public List<ManagedModConfig> getEnabledMods() {
        return registeredMods.values().stream()
                .filter(ManagedModConfig::isEnabled)
                .toList();
    }

    /**
     * Checks if a mod is registered.
     */
    public boolean isRegistered(String modId) {
        return registeredMods.containsKey(modId);
    }

    /**
     * Gets the number of registered mods.
     */
    public int size() {
        return registeredMods.size();
    }

    /**
     * Clears all registrations (mainly for testing).
     */
    public void clear() {
        registeredMods.clear();
    }

    /**
     * Reloads the registry from configuration.
     */
    public void reload() {
        registeredMods.clear();
        loadFromConfig();
    }
}
