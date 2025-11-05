package com.wcholmes.modupdater;

import com.wcholmes.modupdater.commands.UpdaterCommands;
import com.wcholmes.modupdater.config.ManagedModConfig;
import com.wcholmes.modupdater.config.UpdaterConfig;
import com.wcholmes.modupdater.download.DownloadQueue;
import com.wcholmes.modupdater.network.UpdaterPackets;
import com.wcholmes.modupdater.registry.ModRegistry;
import com.wcholmes.modupdater.ui.UpdateNotifier;
import com.wcholmes.modupdater.version.ModVersionInfo;
import com.wcholmes.modupdater.version.VersionChecker;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Main mod class for the Mod Auto-Updater.
 * A generic framework for managing automatic updates of multiple mods from GitHub releases.
 */
@Mod("modupdater")
public class ModUpdater {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "modupdater";

    private static VersionChecker versionChecker;
    private static DownloadQueue downloadQueue;

    public ModUpdater() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup events
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::onInterModEnqueue);
        modEventBus.addListener(this::onInterModProcess);

        // Register to the Forge event bus
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Mod Updater initialized");
    }

    /**
     * Common setup - runs on both client and server.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Mod Updater common setup");

        // Register network packets
        UpdaterPackets.register();

        // Initialize systems
        versionChecker = new VersionChecker();
        downloadQueue = new DownloadQueue();

        // Load configuration
        UpdaterConfig config = UpdaterConfig.getInstance();
        LOGGER.info("Configuration loaded: {} managed mods", config.getManagedMods().size());

        // Initialize registry
        ModRegistry.getInstance();

        // Check for updates on startup (deferred to avoid blocking server start)
        event.enqueueWork(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // Wait 5 seconds for server to fully start
                    if (config.isEnabled()) {
                        LOGGER.info("Checking for updates on startup...");
                        checkForUpdates();
                    }
                } catch (InterruptedException e) {
                    LOGGER.error("Startup update check interrupted", e);
                }
            }, "ModUpdater-StartupCheck").start();
        });
    }

    /**
     * Client-side setup.
     */
    private void clientSetup(FMLClientSetupEvent event) {
        if (!UpdaterConfig.getInstance().isEnabled()) {
            LOGGER.info("Mod Updater is disabled in config");
            return;
        }

        if (!UpdaterConfig.getInstance().isCheckOnStartup()) {
            LOGGER.info("Startup update check disabled in config");
            return;
        }

        LOGGER.info("Starting client-side update check");

        // Check for updates on startup (async)
        event.enqueueWork(() -> {
            checkForUpdates();
        });
    }

    /**
     * Handles IMC enqueue - allows other mods to register themselves.
     */
    private void onInterModEnqueue(InterModEnqueueEvent event) {
        LOGGER.debug("IMC enqueue event");
        // Other mods can send registration messages here
    }

    /**
     * Handles IMC process - processes registration messages from other mods.
     */
    private void onInterModProcess(InterModProcessEvent event) {
        LOGGER.debug("Processing IMC messages");

        event.getIMCStream("register_mod"::equals).forEach(message -> {
            Object payload = message.messageSupplier().get();
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) payload;
                boolean success = ModRegistry.getInstance().registerFromIMC(data);
                if (success) {
                    LOGGER.info("Registered mod via IMC from {}: {}",
                            message.senderModId(), data.get("mod_id"));
                }
            }
        });
    }

    /**
     * Checks for updates for all managed mods.
     */
    public static void checkForUpdates() {
        LOGGER.info("checkForUpdates() called");
        UpdaterConfig config = UpdaterConfig.getInstance();

        if (!config.isEnabled()) {
            LOGGER.info("Config is not enabled, skipping update check");
            return;
        }

        List<ManagedModConfig> enabledMods = ModRegistry.getInstance().getEnabledMods();
        LOGGER.info("Found {} enabled mods to check", enabledMods.size());
        if (enabledMods.isEmpty()) {
            LOGGER.info("No enabled mods to check for updates");
            return;
        }

        UpdateNotifier.notifyCheckingUpdates(enabledMods.size());

        LOGGER.info("Starting async version check");
        versionChecker.checkForUpdates().thenAccept(versionInfo -> {
            LOGGER.info("Version check completed, processing results");
            List<ModVersionInfo> updates = versionChecker.getModsWithUpdates();
            LOGGER.info("Found {} mods with updates available", updates.size());

            if (updates.isEmpty()) {
                LOGGER.info("No updates available, notifying user");
                UpdateNotifier.notifyNoUpdates();
                return;
            }

            LOGGER.info("Notifying user of available updates");
            UpdateNotifier.notifyUpdatesAvailable(updates);

            // Queue downloads if auto-download is enabled
            if (config.isAutoDownload()) {
                LOGGER.info("Auto-download enabled, queueing downloads");
                queueDownloads(updates);
            }
        }).exceptionally(ex -> {
            LOGGER.error("Failed to check for updates", ex);
            UpdateNotifier.notifyError("Failed to check for updates: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Queues and starts downloads for mods with updates.
     */
    private static void queueDownloads(List<ModVersionInfo> updates) {
        downloadQueue.clear();

        for (ModVersionInfo versionInfo : updates) {
            ManagedModConfig modConfig = ModRegistry.getInstance().getMod(versionInfo.getModId());
            if (modConfig != null) {
                downloadQueue.queueDownload(modConfig, versionInfo);
            }
        }

        UpdateNotifier.notifyDownloadStarting(downloadQueue.size());

        downloadQueue.downloadAll().thenAccept(results -> {
            UpdateNotifier.notifyDownloadComplete(results);
        }).exceptionally(ex -> {
            LOGGER.error("Failed to download updates", ex);
            UpdateNotifier.notifyError("Failed to download updates: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Handles server configuration sent to the client (drop-and-go mode).
     * Called by the network packet handler.
     */
    public static void handleServerConfig(com.wcholmes.modupdater.network.ServerConfigPacket packet) {
        LOGGER.info("handleServerConfig() called");
        LOGGER.info("Received full configuration from server: {} managed mods", packet.getManagedMods().size());

        // Apply server config
        UpdaterConfig config = UpdaterConfig.getInstance();
        LOGGER.info("Applying server config - autoDownload: {}, autoInstall: {}",
                   packet.isAutoDownload(), packet.isAutoInstall());
        config.applyServerConfig(
            packet.getManagedMods(),
            packet.isAutoDownload(),
            packet.isAutoInstall(),
            packet.getCheckIntervalMinutes(),
            packet.getDownloadTimeoutSeconds()
        );

        // Reload the mod registry with new config
        LOGGER.info("Reloading mod registry");
        ModRegistry.getInstance().reload();

        // Automatically check for updates after receiving config
        LOGGER.info("Calling checkForUpdates() from handleServerConfig");
        // Clear GitHub API cache to ensure fresh data for manual sync command
        versionChecker.clearCache();
        checkForUpdates();
        LOGGER.info("Returned from checkForUpdates()");
    }

    /**
     * Handles server version requirements sent to the client.
     * Called by the network packet handler.
     */
    public static void handleServerVersions(Map<String, ModVersionInfo> serverVersions) {
        LOGGER.info("Received version requirements from server for {} mods", serverVersions.size());

        versionChecker.updateServerVersions(serverVersions);

        // Re-check for updates with server versions
        List<ModVersionInfo> updates = versionChecker.getModsWithUpdates();

        if (!updates.isEmpty()) {
            UpdateNotifier.notifyUpdatesAvailable(updates);

            if (UpdaterConfig.getInstance().isAutoDownload()) {
                queueDownloads(updates);
            }
        }
    }

    /**
     * Handles player login on server - sends configuration to the client.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) event.getEntity();
            sendConfigToPlayer(player);

            // Send welcome message showing mod version
            net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.literal(
                "[Mod Updater] Server running ModUpdater v1.2.24"
            ).withStyle(net.minecraft.ChatFormatting.AQUA);
            player.sendSystemMessage(message);
        }
    }

    /**
     * Sends configuration to a specific player.
     * Used on login and when client requests config via /modupdater sync.
     */
    public static void sendConfigToPlayer(net.minecraft.server.level.ServerPlayer player) {
        UpdaterConfig config = UpdaterConfig.getInstance();

        if (!config.isEnabled()) {
            return;
        }

        LOGGER.info("Sending configuration to player: {}", player.getName().getString());

        // Send full configuration
        com.wcholmes.modupdater.network.ServerConfigPacket configPacket =
            new com.wcholmes.modupdater.network.ServerConfigPacket(
                config.getManagedMods(),
                config.isAutoDownload(),
                config.isAutoInstall(),
                config.getCheckIntervalMinutes(),
                config.getDownloadTimeoutSeconds()
            );

        UpdaterPackets.sendToPlayer(configPacket, player);
    }

    /**
     * Registers commands.
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        UpdaterCommands.register(event.getDispatcher());
        LOGGER.info("Registered /modupdater commands");
    }

    /**
     * Gets the version checker instance.
     */
    public static VersionChecker getVersionChecker() {
        return versionChecker;
    }

    /**
     * Gets the download queue instance.
     */
    public static DownloadQueue getDownloadQueue() {
        return downloadQueue;
    }
}
