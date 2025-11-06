package com.wcholmes.modupdater.config;

import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates ModUpdater configuration and reports issues to server admins.
 */
public class ConfigValidator {

    /**
     * Validation result containing errors and warnings.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String message) {
            errors.add(message);
        }

        public void addWarning(String message) {
            warnings.add(message);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public boolean isValid() {
            return !hasErrors();
        }

        /**
         * Logs all errors and warnings.
         */
        public void logResults(Logger logger) {
            if (hasErrors()) {
                logger.error("=".repeat(60));
                logger.error("CONFIG VALIDATION ERRORS:");
                for (String error : errors) {
                    logger.error("  - {}", error);
                }
                logger.error("=".repeat(60));
            }

            if (hasWarnings()) {
                logger.warn("=".repeat(60));
                logger.warn("CONFIG VALIDATION WARNINGS:");
                for (String warning : warnings) {
                    logger.warn("  - {}", warning);
                }
                logger.warn("=".repeat(60));
            }
        }
    }

    /**
     * Validates the entire UpdaterConfig.
     */
    public static ValidationResult validate(UpdaterConfig config) {
        ValidationResult result = new ValidationResult();

        if (config == null) {
            result.addError("Configuration is null");
            return result;
        }

        // Check if enabled but no managed mods
        if (config.isEnabled() && config.getManagedMods().isEmpty()) {
            result.addWarning("ModUpdater is enabled but no managed mods are configured");
        }

        // Validate timeout values
        if (config.getDownloadTimeoutSeconds() < 10) {
            result.addWarning("Download timeout is very low (" + config.getDownloadTimeoutSeconds() + "s). Recommended: 30-300s");
        } else if (config.getDownloadTimeoutSeconds() > 600) {
            result.addWarning("Download timeout is very high (" + config.getDownloadTimeoutSeconds() + "s). Consider reducing it.");
        }

        // Validate check interval
        if (config.isPeriodicCheckEnabled()) {
            if (config.getCheckIntervalMinutes() < 5) {
                result.addWarning("Check interval is very frequent (" + config.getCheckIntervalMinutes() + " minutes). This may hit GitHub rate limits.");
            } else if (config.getCheckIntervalMinutes() > 1440) {
                result.addWarning("Check interval is very long (" + config.getCheckIntervalMinutes() + " minutes / " +
                                (config.getCheckIntervalMinutes() / 60) + " hours)");
            }
        }

        // Warn if periodic checks enabled (not yet implemented)
        if (config.isPeriodicCheckEnabled()) {
            result.addWarning("periodicCheckEnabled is true, but periodic checking is not yet implemented. This setting will be ignored.");
        }

        // Validate managed mods
        for (ManagedModConfig mod : config.getManagedMods()) {
            validateManagedMod(mod, result);
        }

        // Check for duplicate mod IDs
        List<String> modIds = new ArrayList<>();
        for (ManagedModConfig mod : config.getManagedMods()) {
            if (modIds.contains(mod.getModId())) {
                result.addError("Duplicate mod ID found: " + mod.getModId());
            }
            modIds.add(mod.getModId());
        }

        return result;
    }

    /**
     * Validates a single managed mod configuration.
     */
    private static void validateManagedMod(ManagedModConfig mod, ValidationResult result) {
        String modId = mod.getModId() != null ? mod.getModId() : "UNKNOWN";

        // Required fields
        if (mod.getModId() == null || mod.getModId().trim().isEmpty()) {
            result.addError("Mod has no modId specified");
            return; // Can't continue validation without mod ID
        }

        if (mod.getGithubRepo() == null || mod.getGithubRepo().trim().isEmpty()) {
            result.addError("Mod '" + modId + "' has no githubRepo specified");
        } else {
            // Validate GitHub repo format (owner/repo)
            String repo = mod.getGithubRepo();
            if (!repo.matches("^[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$")) {
                result.addError("Mod '" + modId + "' has invalid githubRepo format: '" + repo + "'. Expected format: 'owner/repo'");
            }
        }

        if (mod.getJarPattern() == null || mod.getJarPattern().trim().isEmpty()) {
            result.addError("Mod '" + modId + "' has no jarPattern specified");
        } else {
            // Validate JAR pattern contains {version}
            if (!mod.getJarPattern().contains("{version}")) {
                result.addError("Mod '" + modId + "' jarPattern must contain {version} placeholder: '" + mod.getJarPattern() + "'");
            }

            // Validate JAR pattern ends with .jar
            if (!mod.getJarPattern().endsWith(".jar")) {
                result.addWarning("Mod '" + modId + "' jarPattern should end with '.jar': '" + mod.getJarPattern() + "'");
            }
        }

        // Validate update channel
        String channel = mod.getUpdateChannel();
        if (channel != null && !channel.equals("stable") && !channel.equals("prerelease")) {
            result.addWarning("Mod '" + modId + "' has unknown updateChannel: '" + channel + "'. Valid values: 'stable', 'prerelease'");
        }

        // Warn if mod is disabled
        if (!mod.isEnabled()) {
            result.addWarning("Mod '" + modId + "' is disabled");
        }
    }

    /**
     * Validates a ConfigSource.
     */
    public static ValidationResult validateConfigSource(UpdaterConfig.ConfigSource source) {
        ValidationResult result = new ValidationResult();

        if (source == null) {
            result.addError("ConfigSource is null");
            return result;
        }

        if (!"github".equals(source.getType())) {
            result.addError("ConfigSource type must be 'github', got: '" + source.getType() + "'");
        }

        if (source.getRepo() == null || source.getRepo().trim().isEmpty()) {
            result.addError("ConfigSource repo is not specified");
        } else if (!source.getRepo().matches("^[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$")) {
            result.addError("ConfigSource repo has invalid format: '" + source.getRepo() + "'. Expected format: 'owner/repo'");
        }

        if (source.getPath() == null || source.getPath().trim().isEmpty()) {
            result.addError("ConfigSource path is not specified");
        }

        if (source.getBranch() == null || source.getBranch().trim().isEmpty()) {
            result.addWarning("ConfigSource branch is not specified, defaulting to 'main'");
        }

        return result;
    }
}
