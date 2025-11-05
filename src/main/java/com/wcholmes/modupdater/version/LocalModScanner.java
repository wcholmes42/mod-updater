package com.wcholmes.modupdater.version;

import com.wcholmes.modupdater.config.ManagedModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans the mods folder to detect installed mod versions.
 */
public class LocalModScanner {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Gets the mods directory path.
     */
    private static File getModsDirectory() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        return gameDir.resolve("mods").toFile();
    }

    /**
     * Finds the installed version of a managed mod.
     *
     * @param modConfig the mod configuration
     * @return the installed version, or null if not found
     */
    public static String findInstalledVersion(ManagedModConfig modConfig) {
        File modsDir = getModsDirectory();
        LOGGER.info("Scanning for {}: modsDir={}, pattern={}", modConfig.getModId(), modsDir.getAbsolutePath(), modConfig.getJarPattern());

        if (!modsDir.exists() || !modsDir.isDirectory()) {
            LOGGER.warn("Mods directory not found: {}", modsDir);
            return null;
        }

        List<File> matchingFiles = findMatchingJars(modsDir, modConfig);
        LOGGER.info("Found {} matching JARs for {}", matchingFiles.size(), modConfig.getModId());
        for (File f : matchingFiles) {
            LOGGER.info("  - {}", f.getName());
        }

        if (matchingFiles.isEmpty()) {
            LOGGER.info("No JAR found for mod: {}", modConfig.getModId());
            return null;
        }

        // If multiple versions found, use the newest (by modification time)
        if (matchingFiles.size() > 1) {
            LOGGER.warn("Multiple versions found for {}: {}", modConfig.getModId(), matchingFiles.size());
        }

        File newestJar = matchingFiles.stream()
                .max((f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()))
                .orElse(null);

        if (newestJar == null) {
            return null;
        }

        LOGGER.info("Extracting version from: {}", newestJar.getName());

        // Try to extract version from filename using pattern
        String version = extractVersionFromFilename(newestJar.getName(), modConfig.getJarPattern());
        if (version != null) {
            LOGGER.info("Found {} version {} from filename", modConfig.getModId(), version);
            return version;
        }

        LOGGER.info("Failed to extract version from filename, trying JAR manifest");

        // Fallback: try to read from JAR manifest
        version = readVersionFromJar(newestJar);
        if (version != null) {
            LOGGER.info("Found {} version {} from JAR manifest", modConfig.getModId(), version);
            return version;
        }

        LOGGER.warn("Could not determine version for JAR: {}", newestJar.getName());
        return null;
    }

    /**
     * Finds all JAR files matching the mod's pattern.
     */
    public static List<File> findMatchingJars(File modsDir, ManagedModConfig modConfig) {
        List<File> matches = new ArrayList<>();
        Pattern pattern = createPatternFromJarPattern(modConfig.getJarPattern());

        File[] files = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null) {
            return matches;
        }

        for (File file : files) {
            if (pattern.matcher(file.getName()).matches()) {
                matches.add(file);
            }
        }

        return matches;
    }

    /**
     * Creates a regex pattern from a JAR pattern.
     * Example: "landscaper-{version}.jar" -> "landscaper-(.*)\\.jar"
     */
    private static Pattern createPatternFromJarPattern(String jarPattern) {
        // Escape special regex characters except {version}
        String regex = Pattern.quote(jarPattern).replace("\\{version\\}", "\\E(.*)\\Q");
        // Remove trailing \Q\E if present
        regex = regex.replaceAll("\\\\Q\\\\E$", "");
        return Pattern.compile(regex);
    }

    /**
     * Extracts the version from a filename using the JAR pattern.
     * Example: pattern="landscaper-{version}.jar", filename="landscaper-2.0.0.jar" -> "2.0.0"
     */
    private static String extractVersionFromFilename(String filename, String jarPattern) {
        Pattern pattern = createPatternFromJarPattern(jarPattern);
        Matcher matcher = pattern.matcher(filename);

        if (matcher.matches() && matcher.groupCount() >= 1) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Reads the version from a JAR file's manifest or mods.toml.
     */
    private static String readVersionFromJar(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            // Try manifest first
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String version = manifest.getMainAttributes().getValue("Implementation-Version");
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }

            // Could add mods.toml parsing here if needed
            // For now, manifest should be sufficient

        } catch (IOException e) {
            LOGGER.error("Failed to read JAR file {}: {}", jarFile.getName(), e.getMessage());
        }

        return null;
    }

    /**
     * Gets all JAR files for a specific mod (for cleanup during installation).
     */
    public static List<File> getAllVersions(ManagedModConfig modConfig) {
        File modsDir = getModsDirectory();
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            return new ArrayList<>();
        }

        return findMatchingJars(modsDir, modConfig);
    }
}
