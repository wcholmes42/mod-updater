package com.wcholmes.modupdater.download;

import com.wcholmes.modupdater.config.UpdaterConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Downloads a single mod from a URL.
 */
public class ModDownloader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int BUFFER_SIZE = 8192;

    private final String modId;
    private final String version;
    private final String downloadUrl;
    private final String jarPattern;
    private Consumer<DownloadProgress> progressCallback;

    public ModDownloader(String modId, String version, String downloadUrl, String jarPattern) {
        this.modId = modId;
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.jarPattern = jarPattern;
    }

    /**
     * Sets a callback to receive download progress updates.
     */
    public void setProgressCallback(Consumer<DownloadProgress> callback) {
        this.progressCallback = callback;
    }

    /**
     * Downloads the mod file asynchronously.
     *
     * @return CompletableFuture with the downloaded file, or null if failed
     */
    public CompletableFuture<File> download() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return downloadSync();
            } catch (IOException e) {
                LOGGER.error("Failed to download {} version {}: {}", modId, version, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Downloads the mod file synchronously.
     */
    private File downloadSync() throws IOException {
        Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
        modsDir.toFile().mkdirs();

        // Temporary download location
        String tempFileName = "." + modId + "-update.tmp";
        File tempFile = modsDir.resolve(tempFileName).toFile();

        LOGGER.info("Downloading {} version {} from {}", modId, version, downloadUrl);

        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(UpdaterConfig.getInstance().getDownloadTimeoutSeconds() * 1000);
            conn.setReadTimeout(UpdaterConfig.getInstance().getDownloadTimeoutSeconds() * 1000);
            conn.setRequestProperty("User-Agent", "ModUpdater/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP response code: " + responseCode);
            }

            long totalSize = conn.getContentLengthLong();
            long downloadedSize = 0;

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    downloadedSize += bytesRead;

                    // Report progress
                    if (progressCallback != null && totalSize > 0) {
                        double progress = (double) downloadedSize / totalSize;
                        progressCallback.accept(new DownloadProgress(modId, downloadedSize, totalSize, progress));
                    }
                }
            }

            // Generate final filename from pattern
            String finalFileName = jarPattern.replace("{version}", version);
            File finalFile = modsDir.resolve(finalFileName).toFile();

            // Rename temp file to final name
            if (tempFile.renameTo(finalFile)) {
                LOGGER.info("Downloaded {} to {}", modId, finalFile.getName());
                return finalFile;
            } else {
                throw new IOException("Failed to rename temporary file");
            }

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Represents download progress information.
     */
    public static class DownloadProgress {
        private final String modId;
        private final long downloaded;
        private final long total;
        private final double percentage;

        public DownloadProgress(String modId, long downloaded, long total, double percentage) {
            this.modId = modId;
            this.downloaded = downloaded;
            this.total = total;
            this.percentage = percentage;
        }

        public String getModId() {
            return modId;
        }

        public long getDownloaded() {
            return downloaded;
        }

        public long getTotal() {
            return total;
        }

        public double getPercentage() {
            return percentage;
        }

        public int getPercentageInt() {
            return (int) (percentage * 100);
        }
    }
}
