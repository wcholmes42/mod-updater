package com.wcholmes.modupdater.version;

import com.wcholmes.modupdater.config.ManagedModConfig;
import com.wcholmes.modupdater.config.UpdaterConfig;
import com.wcholmes.modupdater.github.GitHubAPI;
import com.wcholmes.modupdater.github.Release;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates version checking for all managed mods.
 */
public class VersionChecker {
    private static final Logger LOGGER = LogManager.getLogger();

    private final GitHubAPI githubAPI;
    private final Map<String, ModVersionInfo> versionInfo;

    public VersionChecker() {
        this.githubAPI = new GitHubAPI();
        this.versionInfo = new HashMap<>();
    }

    /**
     * Checks for updates for all enabled managed mods.
     * @return CompletableFuture that completes when all checks are done
     */
    public CompletableFuture<Map<String, ModVersionInfo>> checkForUpdates() {
        UpdaterConfig config = UpdaterConfig.getInstance();
        List<ManagedModConfig> enabledMods = config.getEnabledMods();

        if (enabledMods.isEmpty()) {
            LOGGER.info("No managed mods enabled, skipping update check");
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        LOGGER.info("Checking {} mods for updates...", enabledMods.size());

        // Check all mods in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ManagedModConfig modConfig : enabledMods) {
            CompletableFuture<Void> future = checkModUpdate(modConfig);
            futures.add(future);
        }

        // Wait for all to complete
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> new HashMap<>(versionInfo));
    }

    /**
     * Checks for updates for a single mod.
     */
    private CompletableFuture<Void> checkModUpdate(ManagedModConfig modConfig) {
        String modId = modConfig.getModId();
        ModVersionInfo info = versionInfo.computeIfAbsent(modId, ModVersionInfo::new);

        // Get local version
        String localVersion = LocalModScanner.findInstalledVersion(modConfig);
        LOGGER.info("Detected local version for {}: {}", modId, localVersion != null ? localVersion : "NOT FOUND");
        info.setLocalVersion(localVersion);

        // Get GitHub version
        boolean includePrerelease = "prerelease".equalsIgnoreCase(modConfig.getUpdateChannel());
        return githubAPI.getLatestRelease(modConfig.getGithubRepo(), includePrerelease)
                .thenAccept(release -> {
                    if (release != null) {
                        String githubVersion = release.getVersion();
                        LOGGER.info("GitHub version for {}: {}", modId, githubVersion);
                        info.setGithubVersion(githubVersion);

                        LOGGER.info("Version comparison for {}: local={}, github={}, updateAvailable={}",
                                   modId, localVersion, githubVersion, info.isUpdateAvailable());

                        // Find matching asset
                        Release.Asset asset = release.findAsset(modConfig.getJarPattern());
                        if (asset != null) {
                            info.setDownloadUrl(asset.getBrowserDownloadUrl());
                            LOGGER.info("Found asset for {}: {} ({})", modId, asset.getName(), asset.getSize());
                        } else {
                            LOGGER.warn("No matching asset found for {} with pattern {}",
                                    modId, modConfig.getJarPattern());
                        }
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to check GitHub for {}: {}", modId, ex.getMessage());
                    return null;
                });
    }

    /**
     * Updates server version information for mods.
     * Called when server sends version requirements.
     */
    public void updateServerVersions(Map<String, ModVersionInfo> serverVersions) {
        for (Map.Entry<String, ModVersionInfo> entry : serverVersions.entrySet()) {
            String modId = entry.getKey();
            ModVersionInfo serverInfo = entry.getValue();

            ModVersionInfo info = versionInfo.computeIfAbsent(modId, ModVersionInfo::new);
            info.setServerVersion(serverInfo.getServerVersion());
            info.setRequired(serverInfo.isRequired());

            LOGGER.info("Server requires {} version {}", modId, serverInfo.getServerVersion());
        }
    }

    /**
     * Gets version information for a specific mod.
     */
    public ModVersionInfo getVersionInfo(String modId) {
        return versionInfo.get(modId);
    }

    /**
     * Gets all version information.
     */
    public Map<String, ModVersionInfo> getAllVersionInfo() {
        return new HashMap<>(versionInfo);
    }

    /**
     * Gets a list of mods that have updates available.
     */
    public List<ModVersionInfo> getModsWithUpdates() {
        return versionInfo.values().stream()
                .filter(ModVersionInfo::isUpdateAvailable)
                .toList();
    }

    /**
     * Clears all version information state.
     * Call this before running a fresh update check.
     */
    public void clearVersionInfo() {
        versionInfo.clear();
    }
}
