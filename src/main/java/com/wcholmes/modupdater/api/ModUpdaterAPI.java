package com.wcholmes.modupdater.api;

import com.wcholmes.modupdater.config.ManagedModConfig;
import com.wcholmes.modupdater.network.ServerModVersionsPacket;
import com.wcholmes.modupdater.network.UpdaterPackets;
import com.wcholmes.modupdater.registry.ModRegistry;
import com.wcholmes.modupdater.version.ModVersionInfo;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Public API for other mods to interact with the Mod Updater.
 * This is the primary integration point for mods that want to use the updater.
 */
public class ModUpdaterAPI {

    /**
     * Registers a mod to be managed by the updater.
     * This is the programmatic way to register a mod (alternative to config file).
     *
     * @param modId the Forge mod ID
     * @param githubRepo the GitHub repository in format "owner/repo"
     * @param jarPattern the JAR filename pattern with {version} placeholder
     * @return true if successfully registered
     */
    public static boolean registerMod(String modId, String githubRepo, String jarPattern) {
        ManagedModConfig config = new ManagedModConfig(modId, githubRepo, jarPattern);
        return ModRegistry.getInstance().registerMod(config);
    }

    /**
     * Registers a mod with full configuration options.
     *
     * @param modConfig the complete mod configuration
     * @return true if successfully registered
     */
    public static boolean registerMod(ManagedModConfig modConfig) {
        return ModRegistry.getInstance().registerMod(modConfig);
    }

    /**
     * Sends version requirements to a player's client.
     * Use this on the server to tell clients which mod versions are required.
     *
     * @param player the player to send requirements to
     * @param modId the mod ID
     * @param version the required version
     * @param required whether this version is strictly required
     */
    public static void requireVersion(ServerPlayer player, String modId, String version, boolean required) {
        Map<String, ModVersionInfo> versions = new HashMap<>();
        versions.put(modId, new ModVersionInfo(modId, version, required));

        ServerModVersionsPacket packet = new ServerModVersionsPacket(versions);
        UpdaterPackets.sendToPlayer(packet, player);
    }

    /**
     * Sends version requirements to a player's client (non-required).
     *
     * @param player the player to send requirements to
     * @param modId the mod ID
     * @param version the required version
     */
    public static void requireVersion(ServerPlayer player, String modId, String version) {
        requireVersion(player, modId, version, false);
    }

    /**
     * Sends multiple version requirements to a player's client.
     *
     * @param player the player to send requirements to
     * @param versions map of mod ID to version string
     */
    public static void sendVersionRequirements(ServerPlayer player, Map<String, String> versions) {
        Map<String, ModVersionInfo> versionInfo = new HashMap<>();

        for (Map.Entry<String, String> entry : versions.entrySet()) {
            versionInfo.put(entry.getKey(), new ModVersionInfo(entry.getKey(), entry.getValue(), false));
        }

        ServerModVersionsPacket packet = new ServerModVersionsPacket(versionInfo);
        UpdaterPackets.sendToPlayer(packet, player);
    }

    /**
     * Checks if a mod is registered with the updater.
     *
     * @param modId the mod ID to check
     * @return true if the mod is registered
     */
    public static boolean isModRegistered(String modId) {
        return ModRegistry.getInstance().isRegistered(modId);
    }

    /**
     * Gets the configuration for a registered mod.
     *
     * @param modId the mod ID
     * @return the mod configuration, or null if not registered
     */
    public static ManagedModConfig getModConfig(String modId) {
        return ModRegistry.getInstance().getMod(modId);
    }

    /**
     * Gets the number of mods registered with the updater.
     *
     * @return the number of registered mods
     */
    public static int getRegisteredModCount() {
        return ModRegistry.getInstance().size();
    }
}
