package com.wcholmes.modupdater.version;

/**
 * Tracks version information for a managed mod.
 */
public class ModVersionInfo {
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

        if (target == null || localVersion == null) {
            System.out.println("[ModVersionInfo] " + modId + ": checkForUpdate() - target=" + target + ", local=" + localVersion + " -> updateAvailable=false (null check)");
            updateAvailable = false;
            return;
        }

        try {
            SemanticVersion targetVer = SemanticVersion.parse(target);
            SemanticVersion localVer = SemanticVersion.parse(localVersion);
            updateAvailable = targetVer.isGreaterThan(localVer);
            System.out.println("[ModVersionInfo] " + modId + ": checkForUpdate() - target=" + target + ", local=" + localVersion + " -> updateAvailable=" + updateAvailable + " (semantic)");
        } catch (IllegalArgumentException e) {
            // If version parsing fails, do string comparison
            updateAvailable = !target.equals(localVersion);
            System.out.println("[ModVersionInfo] " + modId + ": checkForUpdate() - target=" + target + ", local=" + localVersion + " -> updateAvailable=" + updateAvailable + " (string compare, parse failed: " + e.getMessage() + ")");
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
