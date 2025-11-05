package com.wcholmes.modupdater.download;

import com.wcholmes.modupdater.config.ManagedModConfig;
import com.wcholmes.modupdater.config.UpdaterConfig;
import com.wcholmes.modupdater.version.ModVersionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * Manages downloading multiple mods in parallel with a concurrency limit.
 */
public class DownloadQueue {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_CONCURRENT_DOWNLOADS = 3;

    private final Map<String, DownloadTask> tasks = new HashMap<>();
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_DOWNLOADS);

    /**
     * Adds a mod to the download queue.
     */
    public void queueDownload(ManagedModConfig modConfig, ModVersionInfo versionInfo) {
        String modId = modConfig.getModId();
        String targetVersion = versionInfo.getTargetVersion();
        String downloadUrl = versionInfo.getDownloadUrl();

        if (downloadUrl == null || downloadUrl.isEmpty()) {
            LOGGER.error("No download URL for {}", modId);
            return;
        }

        // Check minVersion restriction (prevent downgrades to vulnerable versions)
        if (modConfig.getMinVersion() != null && !modConfig.getMinVersion().isEmpty()) {
            try {
                com.wcholmes.modupdater.version.SemanticVersion targetVer =
                    com.wcholmes.modupdater.version.SemanticVersion.parse(targetVersion);
                com.wcholmes.modupdater.version.SemanticVersion minVer =
                    com.wcholmes.modupdater.version.SemanticVersion.parse(modConfig.getMinVersion());

                if (targetVer.isLessThan(minVer)) {
                    LOGGER.warn("Refusing to download {} version {} (below minimum version {})",
                        modId, targetVersion, modConfig.getMinVersion());
                    return;
                }
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Failed to parse versions for minVersion check: {}", e.getMessage());
            }
        }

        DownloadTask task = new DownloadTask(modConfig, targetVersion, downloadUrl);
        tasks.put(modId, task);

        LOGGER.info("Queued download: {} version {}", modId, targetVersion);
    }

    /**
     * Starts downloading all queued mods.
     *
     * @return CompletableFuture that completes when all downloads finish
     */
    public CompletableFuture<DownloadResults> downloadAll() {
        if (tasks.isEmpty()) {
            LOGGER.info("No downloads queued");
            return CompletableFuture.completedFuture(new DownloadResults());
        }

        LOGGER.info("Starting download of {} mods...", tasks.size());

        List<CompletableFuture<DownloadResult>> futures = new ArrayList<>();

        for (DownloadTask task : tasks.values()) {
            CompletableFuture<DownloadResult> future = downloadWithLimit(task);
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<DownloadResult> results = futures.stream()
                            .map(CompletableFuture::join)
                            .toList();
                    return new DownloadResults(results);
                });
    }

    /**
     * Downloads a single mod with concurrency limiting.
     */
    private CompletableFuture<DownloadResult> downloadWithLimit(DownloadTask task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                semaphore.acquire();
                LOGGER.debug("Downloading {} (slot acquired)", task.modConfig.getModId());

                ModDownloader downloader = new ModDownloader(
                        task.modConfig.getModId(),
                        task.version,
                        task.downloadUrl,
                        task.modConfig.getJarPattern()
                );

                File downloadedFile = downloader.download().join();

                if (downloadedFile != null && UpdaterConfig.getInstance().isAutoInstall()) {
                    boolean installed = ModInstaller.install(
                            downloadedFile,
                            task.modConfig,
                            task.version
                    );

                    if (installed) {
                        return new DownloadResult(task.modConfig.getModId(), task.version, true, null);
                    } else {
                        return new DownloadResult(task.modConfig.getModId(), task.version, false, "Installation failed");
                    }
                } else if (downloadedFile != null) {
                    return new DownloadResult(task.modConfig.getModId(), task.version, true, null);
                } else {
                    return new DownloadResult(task.modConfig.getModId(), task.version, false, "Download failed");
                }

            } catch (InterruptedException e) {
                LOGGER.error("Download interrupted for {}", task.modConfig.getModId());
                Thread.currentThread().interrupt();
                return new DownloadResult(task.modConfig.getModId(), task.version, false, "Interrupted");
            } finally {
                semaphore.release();
            }
        });
    }

    /**
     * Clears all queued downloads.
     */
    public void clear() {
        tasks.clear();
    }

    /**
     * Gets the number of queued downloads.
     */
    public int size() {
        return tasks.size();
    }

    /**
     * Represents a download task.
     */
    private static class DownloadTask {
        final ManagedModConfig modConfig;
        final String version;
        final String downloadUrl;

        DownloadTask(ManagedModConfig modConfig, String version, String downloadUrl) {
            this.modConfig = modConfig;
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }

    /**
     * Result of a single download.
     */
    public static class DownloadResult {
        private final String modId;
        private final String version;
        private final boolean success;
        private final String errorMessage;

        public DownloadResult(String modId, String version, boolean success, String errorMessage) {
            this.modId = modId;
            this.version = version;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public String getModId() {
            return modId;
        }

        public String version() {
            return version;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Results of all downloads.
     */
    public static class DownloadResults {
        private final List<DownloadResult> results;

        public DownloadResults() {
            this.results = new ArrayList<>();
        }

        public DownloadResults(List<DownloadResult> results) {
            this.results = results;
        }

        public List<DownloadResult> getSuccessful() {
            return results.stream().filter(DownloadResult::isSuccess).toList();
        }

        public List<DownloadResult> getFailed() {
            return results.stream().filter(r -> !r.isSuccess()).toList();
        }

        public int getSuccessCount() {
            return (int) results.stream().filter(DownloadResult::isSuccess).count();
        }

        public int getFailedCount() {
            return (int) results.stream().filter(r -> !r.isSuccess()).count();
        }

        public boolean hasFailures() {
            return getFailedCount() > 0;
        }

        public List<DownloadResult> getAll() {
            return new ArrayList<>(results);
        }
    }
}
