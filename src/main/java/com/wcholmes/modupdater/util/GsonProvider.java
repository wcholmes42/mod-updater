package com.wcholmes.modupdater.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Provides a singleton Gson instance for JSON serialization/deserialization.
 * Centralizes Gson configuration to avoid duplicate initialization.
 */
public class GsonProvider {
    private static final Gson INSTANCE = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private GsonProvider() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets the shared Gson instance.
     * @return the Gson instance
     */
    public static Gson getGson() {
        return INSTANCE;
    }
}
