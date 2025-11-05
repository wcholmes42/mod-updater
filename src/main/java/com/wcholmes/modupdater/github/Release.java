package com.wcholmes.modupdater.github;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a GitHub release.
 */
public class Release {
    @SerializedName("tag_name")
    private String tagName;

    private String name;

    @SerializedName("prerelease")
    private boolean preRelease;

    private List<Asset> assets = new ArrayList<>();

    @SerializedName("published_at")
    private String publishedAt;

    public String getTagName() {
        return tagName;
    }

    public String getName() {
        return name;
    }

    public boolean isPreRelease() {
        return preRelease;
    }

    public List<Asset> getAssets() {
        return assets;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    /**
     * Gets the version string from the tag name (removes 'v' prefix if present).
     */
    public String getVersion() {
        if (tagName == null) {
            return null;
        }
        return tagName.startsWith("v") ? tagName.substring(1) : tagName;
    }

    /**
     * Finds an asset matching the given JAR pattern.
     * Pattern example: "landscaper-{version}.jar" with version "2.0.0" looks for "landscaper-2.0.0.jar"
     *
     * @param jarPattern the pattern with {version} placeholder
     * @return the matching asset, or null if not found
     */
    public Asset findAsset(String jarPattern) {
        if (jarPattern == null || !jarPattern.contains("{version}")) {
            return null;
        }

        String version = getVersion();
        if (version == null) {
            return null;
        }

        String expectedName = jarPattern.replace("{version}", version);

        for (Asset asset : assets) {
            if (expectedName.equals(asset.getName())) {
                return asset;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "Release{" +
                "tagName='" + tagName + '\'' +
                ", name='" + name + '\'' +
                ", preRelease=" + preRelease +
                ", assets=" + assets.size() +
                '}';
    }

    /**
     * Represents a release asset (downloadable file).
     */
    public static class Asset {
        private String name;

        @SerializedName("browser_download_url")
        private String browserDownloadUrl;

        private long size;

        @SerializedName("content_type")
        private String contentType;

        public String getName() {
            return name;
        }

        public String getBrowserDownloadUrl() {
            return browserDownloadUrl;
        }

        public long getSize() {
            return size;
        }

        public String getContentType() {
            return contentType;
        }

        @Override
        public String toString() {
            return "Asset{" +
                    "name='" + name + '\'' +
                    ", size=" + size +
                    '}';
        }
    }
}
