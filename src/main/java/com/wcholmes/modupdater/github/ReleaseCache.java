package com.wcholmes.modupdater.github;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches GitHub release data to avoid hitting rate limits.
 */
public class ReleaseCache {
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    private final Map<String, CacheEntry> cache = new HashMap<>();

    /**
     * Gets a cached release if available and not expired.
     *
     * @param repo the repository in format "owner/repo"
     * @return the cached release, or null if not cached or expired
     */
    public Release get(String repo) {
        CacheEntry entry = cache.get(repo);
        if (entry == null) {
            return null;
        }

        if (System.currentTimeMillis() - entry.timestamp > CACHE_DURATION_MS) {
            cache.remove(repo);
            return null;
        }

        return entry.release;
    }

    /**
     * Stores a release in the cache.
     *
     * @param repo the repository in format "owner/repo"
     * @param release the release to cache
     */
    public void put(String repo, Release release) {
        cache.put(repo, new CacheEntry(release, System.currentTimeMillis()));
    }

    /**
     * Clears the entire cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Clears the cache entry for a specific repository.
     *
     * @param repo the repository in format "owner/repo"
     */
    public void clearRepo(String repo) {
        cache.remove(repo);
    }

    private static class CacheEntry {
        final Release release;
        final long timestamp;

        CacheEntry(Release release, long timestamp) {
            this.release = release;
            this.timestamp = timestamp;
        }
    }
}
