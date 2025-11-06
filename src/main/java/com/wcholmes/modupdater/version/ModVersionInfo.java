package com.wcholmes.modupdater.version;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tracks version information for a managed mod.
 */
public class ModVersionInfo {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String modId;
    private String localVersion;
    private String githubVersion;
    private String serverVersion;
    private String downloadUrl;
    private boolean updateAvailable;
    private boolean required;

    public ModVersionInfo(String modId) {
        this.modId = modId;
        this.updateAvailable = false;
        this.required = false;
    }

    public ModVersionInfo(String modId, String version, boolean required) {
        this.modId = modId;
        this.serverVersion = version;
        this.required = required;
    }

    /**
     * Determines the target version to update to.
     * Priority: server version > GitHub version
     *
     * @return the target version, or null if none available
     */
    public String getTargetVersion() {
        if (serverVersion != null && !serverVersion.isEmpty()) {
            return serverVersion;
        }
        return githubVersion;
    }

    /**
     * Checks if an update is needed based on available versions.
     */
    public void checkForUpdate() {
        String target = getTargetVersion();

        // No target version available - nothing to update to
        if (target == null) {
            LOGGER.info("[ModVersionInfo] {}: checkForUpdate() - target=null -> updateAvailable=false", modId);
            updateAvailable = false;
            return;
        }

        // Mod not installed but target exists - need to download
        if (localVersion == null) {
            LOGGER.info("[ModVersionInfo] {}: checkForUpdate() - local=null, target={} -> updateAvailable=true (not installed)", modId, target);
            updateAvailable = true;
            return;
        }

        try {
            SemanticVersion targetVer = SemanticVersion.parse(target);
            SemanticVersion localVer = SemanticVersion.parse(localVersion);
            updateAvailable = targetVer.isGreaterThan(localVer);
            LOGGER.info("[ModVersionInfo] {}: checkForUpdate() - target={}, local={} -> updateAvailable={} (semantic)", modId, target, localVersion, updateAvailable);
        } catch (IllegalArgumentException e) {
            // If version parsing fails, do string comparison
            updateAvailable = !target.equals(localVersion);
            LOGGER.info("[ModVersionInfo] {}: checkForUpdate() - target={}, local={} -> updateAvailable={} (string compare, parse failed: {})", modId, target, localVersion, updateAvailable, e.getMessage());
        }
    }

    // Getters and setters
    public String getModId() {
        return modId;
    }

    public String getLocalVersion() {
        return localVersion;
    }

    public void setLocalVersion(String localVersion) {
        this.localVersion = localVersion;
        checkForUpdate();
    }

    public String getGithubVersion() {
        return githubVersion;
    }

    public void setGithubVersion(String githubVersion) {
        this.githubVersion = githubVersion;
        checkForUpdate();
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
        checkForUpdate();
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public String toString() {
        return "ModVersionInfo{" +
                "modId='" + modId + '\'' +
                ", local=" + localVersion +
                ", github=" + githubVersion +
                ", server=" + serverVersion +
                ", target=" + getTargetVersion() +
                ", updateAvailable=" + updateAvailable +
                '}';
    }
}
