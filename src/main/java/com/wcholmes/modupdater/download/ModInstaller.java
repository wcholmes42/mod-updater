package com.wcholmes.modupdater.download;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wcholmes.modupdater.config.ManagedModConfig;
import com.wcholmes.modupdater.version.LocalModScanner;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles installation of downloaded mods and cleanup of old versions.
 */
public class ModInstaller {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STATE_FILE = "mods/.modupdater-state.json";

    /**
     * Installs a downloaded mod, removing old versions.
     *
     * @param newJar the downloaded JAR file
     * @param modConfig the mod configuration
     * @param newVersion the new version being installed
     * @return true if installation succeeded
     */
    public static boolean install(File newJar, ManagedModConfig modConfig, String newVersion) {
        if (!validateJar(newJar)) {
            LOGGER.error("Downloaded JAR is invalid: {}", newJar);
            return false;
        }

        String oldVersion = LocalModScanner.findInstalledVersion(modConfig);

        // Remove old versions
        List<File> oldJars = LocalModScanner.getAllVersions(modConfig);
        for (File oldJar : oldJars) {
            if (!oldJar.equals(newJar)) {
                LOGGER.info("Removing old version: {}", oldJar.getName());
                if (!oldJar.delete()) {
                    LOGGER.warn("Failed to delete old JAR: {}", oldJar.getName());
                }
            }
        }

        // Update state file
        updateStateFile(modConfig.getModId(), oldVersion, newVersion);

        LOGGER.info("Successfully installed {} version {}", modConfig.getModId(), newVersion);
        return true;
    }

    /**
     * Validates that a JAR file is readable and has content.
     */
    private static boolean validateJar(File jarFile) {
        if (!jarFile.exists() || !jarFile.isFile()) {
            return false;
        }

        // Check file size is reasonable
        long size = jarFile.length();
        if (size < 1024) { // At least 1KB
            LOGGER.warn("JAR file suspiciously small: {} bytes", size);
            return false;
        }

        if (size > 100 * 1024 * 1024) { // More than 100MB is suspicious for a mod
            LOGGER.warn("JAR file suspiciously large: {} bytes", size);
            return false;
        }

        // Validate it's actually a JAR file (ZIP format with manifest)
        // Note: 'true' parameter enables signature verification
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile, true)) {
            // Check for manifest
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest == null) {
                LOGGER.error("JAR has no manifest: {}", jarFile.getName());
                return false;
            }

            // Check for mods.toml (Forge mod requirement)
            java.util.jar.JarEntry modsToml = jar.getJarEntry("META-INF/mods.toml");
            if (modsToml == null) {
                LOGGER.error("Not a valid Forge mod (missing META-INF/mods.toml): {}", jarFile.getName());
                return false;
            }

            // Verify JAR signature if present
            if (!verifyJarSignature(jar, jarFile.getName())) {
                return false;
            }

            LOGGER.debug("JAR validation passed: {}", jarFile.getName());
            return true;

        } catch (IOException e) {
            LOGGER.error("JAR validation failed for {}: {}", jarFile.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the JAR signature if signing is enabled.
     * @param jar the JAR file to verify
     * @param fileName the file name for logging
     * @return true if signature is valid or signing is not required, false if signature is invalid
     */
    private static boolean verifyJarSignature(java.util.jar.JarFile jar, String fileName) {
        try {
            // Check if JAR is signed
            boolean isSigned = false;
            boolean allEntriesValid = true;
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();

            // Read all entries to trigger signature verification
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();

                // Skip directories and META-INF files
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }

                // Read the entry completely to trigger signature verification
                try (java.io.InputStream is = jar.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    while (is.read(buffer) != -1) {
                        // Reading triggers signature verification in JarFile
                    }
                }

                // Check if this entry has a signature
                java.security.CodeSigner[] signers = entry.getCodeSigners();
                if (signers != null && signers.length > 0) {
                    isSigned = true;

                    // Verify the signature is from expected signer
                    boolean validSigner = false;
                    for (java.security.CodeSigner signer : signers) {
                        java.security.cert.CertPath certPath = signer.getSignerCertPath();
                        if (certPath != null && !certPath.getCertificates().isEmpty()) {
                            java.security.cert.Certificate cert = certPath.getCertificates().get(0);
                            if (cert instanceof java.security.cert.X509Certificate) {
                                java.security.cert.X509Certificate x509 = (java.security.cert.X509Certificate) cert;
                                String dn = x509.getSubjectX500Principal().getName();

                                // Check if it's signed by wcholmes42
                                // Accept CN=modupdater, CN=wcholmes42, or O=wcholmes42
                                if (dn.contains("CN=modupdater") ||
                                    dn.contains("CN=wcholmes42") ||
                                    dn.contains("O=wcholmes42")) {
                                    validSigner = true;
                                    LOGGER.debug("Valid signature found: {}", dn);
                                    break;
                                }
                            }
                        }
                    }

                    if (!validSigner) {
                        LOGGER.error("JAR signature is not from trusted signer: {}", fileName);
                        allEntriesValid = false;
                        break;
                    }
                } else if (isSigned) {
                    // Some entries signed, but this one isn't - JAR has been tampered with
                    LOGGER.error("JAR has unsigned entries (possible tampering): {}", fileName);
                    allEntriesValid = false;
                    break;
                }
            }

            if (isSigned) {
                if (allEntriesValid) {
                    LOGGER.info("✅ JAR signature verified: {}", fileName);
                    return true;
                } else {
                    LOGGER.error("❌ JAR signature verification failed: {}", fileName);
                    return false;
                }
            } else {
                // JAR is not signed
                // For backwards compatibility, allow unsigned JARs with a warning
                // In the future, you can change this to 'return false' to require signatures
                LOGGER.warn("⚠️  JAR is not signed: {} (signatures will be required in future versions)", fileName);
                return true;  // Change to 'false' to enforce signature requirement
            }

        } catch (Exception e) {
            LOGGER.error("JAR signature verification failed for {}: {}", fileName, e.getMessage());
            return false;
        }
    }

    /**
     * Updates the state file with installation information.
     */
    private static void updateStateFile(String modId, String oldVersion, String newVersion) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        File stateFile = gameDir.resolve(STATE_FILE).toFile();

        UpdaterState state;
        if (stateFile.exists()) {
            try (FileReader reader = new FileReader(stateFile)) {
                state = GSON.fromJson(reader, UpdaterState.class);
            } catch (IOException e) {
                LOGGER.warn("Failed to read state file, creating new one");
                state = new UpdaterState();
            }
        } else {
            state = new UpdaterState();
        }

        // Add update record
        UpdateRecord record = new UpdateRecord();
        record.modId = modId;
        record.oldVersion = oldVersion;
        record.newVersion = newVersion;
        record.updatedAt = Instant.now().toString();

        state.updatedMods.add(record);
        state.lastUpdate = Instant.now().toString();
        state.restartRequired = true;

        // Save state
        stateFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(stateFile)) {
            GSON.toJson(state, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save state file", e);
        }
    }

    /**
     * Checks if a restart is required based on the state file.
     */
    public static boolean isRestartRequired() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        File stateFile = gameDir.resolve(STATE_FILE).toFile();

        if (!stateFile.exists()) {
            return false;
        }

        try (FileReader reader = new FileReader(stateFile)) {
            UpdaterState state = GSON.fromJson(reader, UpdaterState.class);
            return state != null && state.restartRequired;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Clears the restart required flag.
     */
    public static void clearRestartFlag() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        File stateFile = gameDir.resolve(STATE_FILE).toFile();

        if (!stateFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(stateFile)) {
            UpdaterState state = GSON.fromJson(reader, UpdaterState.class);
            if (state != null) {
                state.restartRequired = false;
                try (FileWriter writer = new FileWriter(stateFile)) {
                    GSON.toJson(state, writer);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to clear restart flag", e);
        }
    }

    /**
     * State file structure
     */
    private static class UpdaterState {
        String lastUpdate;
        List<UpdateRecord> updatedMods = new ArrayList<>();
        boolean restartRequired;
    }

    /**
     * Individual update record
     */
    private static class UpdateRecord {
        String modId;
        String oldVersion;
        String newVersion;
        String updatedAt;
    }
}
