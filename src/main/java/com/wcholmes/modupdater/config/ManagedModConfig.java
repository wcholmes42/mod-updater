package com.wcholmes.modupdater.config;

/**
 * Configuration for a single managed mod.
 */
public class ManagedModConfig {
    /**
     * The Forge mod ID (e.g., "landscaper")
     */
    private String modId;

    /**
     * GitHub repository in format "owner/repo" (e.g., "wcholmes42/minecraft-landscaper")
     */
    private String githubRepo;

    /**
     * JAR filename pattern with {version} placeholder (e.g., "landscaper-{version}.jar")
     */
    private String jarPattern;

    /**
     * Whether updates are enabled for this mod
     */
    private boolean enabled = true;

    /**
     * Minimum version to maintain (optional, prevents downgrades)
     */
    private String minVersion = null;

    /**
     * Update channel: "latest" or "prerelease"
     */
    private String updateChannel = "latest";

    /**
     * Whether this mod is required (fail if can't update)
     */
    private boolean required = false;

    // Default constructor for Gson
    public ManagedModConfig() {}

    public ManagedModConfig(String modId, String githubRepo, String jarPattern) {
        this.modId = modId;
        this.githubRepo = githubRepo;
        this.jarPattern = jarPattern;
    }

    // Getters and setters
    public String getModId() {
        return modId;
    }

    public void setModId(String modId) {
        this.modId = modId;
    }

    public String getGithubRepo() {
        return githubRepo;
    }

    public void setGithubRepo(String githubRepo) {
        this.githubRepo = githubRepo;
    }

    public String getJarPattern() {
        return jarPattern;
    }

    public void setJarPattern(String jarPattern) {
        this.jarPattern = jarPattern;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = minVersion;
    }

    public String getUpdateChannel() {
        return updateChannel;
    }

    public void setUpdateChannel(String updateChannel) {
        this.updateChannel = updateChannel;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Validates this configuration.
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return modId != null && !modId.isEmpty() &&
               githubRepo != null && !githubRepo.isEmpty() &&
               jarPattern != null && !jarPattern.isEmpty() &&
               jarPattern.contains("{version}");
    }

    @Override
    public String toString() {
        return "ManagedModConfig{" +
                "modId='" + modId + '\'' +
                ", githubRepo='" + githubRepo + '\'' +
                ", jarPattern='" + jarPattern + '\'' +
                ", enabled=" + enabled +
                ", updateChannel='" + updateChannel + '\'' +
                '}';
    }
}
