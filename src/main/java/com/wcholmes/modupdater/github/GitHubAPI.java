package com.wcholmes.modupdater.github;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.wcholmes.modupdater.util.GsonProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

/**
 * Client for interacting with the GitHub API to fetch release information.
 */
public class GitHubAPI {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = GsonProvider.getGson();
    private static final String API_BASE = "https://api.github.com";
    private static final String RAW_BASE = "https://raw.githubusercontent.com";
    private static final int TIMEOUT_MS = 10000; // 10 seconds

    /**
     * Gets the latest release for a repository (non-prerelease).
     *
     * @param repo the repository in format "owner/repo"
     * @return CompletableFuture with the latest release, or null if not found
     */
    public CompletableFuture<Release> getLatestRelease(String repo) {
        return getLatestRelease(repo, false);
    }

    /**
     * Gets the latest release for a repository.
     *
     * @param repo the repository in format "owner/repo"
     * @param includePrerelease whether to include pre-releases
     * @return CompletableFuture with the latest release, or null if not found
     */
    public CompletableFuture<Release> getLatestRelease(String repo, boolean includePrerelease) {
        return CompletableFuture.supplyAsync(() -> {
            String endpoint = includePrerelease
                    ? String.format("%s/repos/%s/releases", API_BASE, repo)
                    : String.format("%s/repos/%s/releases/latest", API_BASE, repo);

            try {
                Release release = fetchRelease(endpoint, includePrerelease);
                if (release != null) {
                    LOGGER.info("Fetched latest release for {}: {}", repo, release.getTagName());
                }
                return release;
            } catch (IOException e) {
                LOGGER.error("Failed to fetch release for {}: {}", repo, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Fetches release data from GitHub API.
     */
    private Release fetchRelease(String endpoint, boolean includePrerelease) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "ModUpdater/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode == 404) {
                LOGGER.warn("No releases found at: {}", endpoint);
                return null;
            } else if (responseCode != 200) {
                LOGGER.error("GitHub API returned {}: {}", responseCode, endpoint);
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String json = response.toString();

            // If fetching all releases (for prerelease), parse array and get first
            if (includePrerelease && json.trim().startsWith("[")) {
                Release[] releases = GSON.fromJson(json, Release[].class);
                return (releases != null && releases.length > 0) ? releases[0] : null;
            } else {
                return GSON.fromJson(json, Release.class);
            }

        } catch (JsonSyntaxException e) {
            LOGGER.error("Failed to parse GitHub API response: {}", e.getMessage());
            return null;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Fetches raw file content from a GitHub repository.
     * Used for fetching config files from GitHub.
     *
     * @param repo the repository in format "owner/repo"
     * @param path the path to the file in the repo (e.g., "packs/family-pack.json")
     * @param branch the branch to fetch from (e.g., "main")
     * @return the raw file content as a string, or null if not found or error
     */
    public String fetchRawFile(String repo, String path, String branch) {
        // URL format: https://raw.githubusercontent.com/owner/repo/branch/path
        // Add cache-busting parameter to force fresh fetch
        String cacheBuster = String.valueOf(System.currentTimeMillis());
        String url = String.format("%s/%s/%s/%s?cb=%s", RAW_BASE, repo, branch, path, cacheBuster);

        LOGGER.info("Fetching raw file from GitHub: {}", url);

        try {
            URL urlObj = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();

            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "ModUpdater/1.0");

                int responseCode = conn.getResponseCode();
                if (responseCode == 404) {
                    LOGGER.warn("File not found at: {}", url);
                    return null;
                } else if (responseCode != 200) {
                    LOGGER.error("GitHub returned {}: {}", responseCode, url);
                    return null;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    response.append('\n');
                }
                reader.close();

                LOGGER.info("Successfully fetched raw file from GitHub ({} bytes)", response.length());
                return response.toString();

            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to fetch raw file from GitHub: {}", e.getMessage());
            return null;
        }
    }

}
