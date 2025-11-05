package com.wcholmes.modupdater.version;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a semantic version (major.minor.patch) with comparison support.
 * Supports versions like: 1.0.0, 2.3.5, v1.2.3, 1.0, etc.
 */
public class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[-+].*)?");

    private final int major;
    private final int minor;
    private final int patch;
    private final String original;

    private SemanticVersion(int major, int minor, int patch, String original) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.original = original;
    }

    /**
     * Parses a version string into a SemanticVersion.
     * Supports formats like: 1.0.0, v2.3.5, 1.2, v3, etc.
     *
     * @param versionString the version string to parse
     * @return the parsed SemanticVersion
     * @throws IllegalArgumentException if the version string is invalid
     */
    public static SemanticVersion parse(String versionString) {
        if (versionString == null || versionString.isEmpty()) {
            throw new IllegalArgumentException("Version string cannot be null or empty");
        }

        Matcher matcher = VERSION_PATTERN.matcher(versionString.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version format: " + versionString);
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

        return new SemanticVersion(major, minor, patch, versionString);
    }

    /**
     * Attempts to parse a version string, returning null if invalid.
     *
     * @param versionString the version string to parse
     * @return the parsed SemanticVersion, or null if invalid
     */
    public static SemanticVersion tryParse(String versionString) {
        try {
            return parse(versionString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Compares this version to another.
     * Returns: negative if this < other, 0 if equal, positive if this > other
     */
    @Override
    public int compareTo(SemanticVersion other) {
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }
        return Integer.compare(this.patch, other.patch);
    }

    /**
     * Checks if this version is greater than another.
     */
    public boolean isGreaterThan(SemanticVersion other) {
        return this.compareTo(other) > 0;
    }

    /**
     * Checks if this version is less than another.
     */
    public boolean isLessThan(SemanticVersion other) {
        return this.compareTo(other) < 0;
    }

    /**
     * Checks if this version is equal to another.
     */
    public boolean isEqualTo(SemanticVersion other) {
        return this.compareTo(other) == 0;
    }

    /**
     * Checks if this version is greater than or equal to another.
     */
    public boolean isGreaterThanOrEqualTo(SemanticVersion other) {
        return this.compareTo(other) >= 0;
    }

    /**
     * Checks if this version is less than or equal to another.
     */
    public boolean isLessThanOrEqualTo(SemanticVersion other) {
        return this.compareTo(other) <= 0;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public String getOriginal() {
        return original;
    }

    /**
     * Returns the normalized version string (e.g., "1.2.3")
     */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SemanticVersion that = (SemanticVersion) o;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }
}
