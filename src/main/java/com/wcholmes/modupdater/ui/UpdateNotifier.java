package com.wcholmes.modupdater.ui;

import com.wcholmes.modupdater.download.DownloadQueue;
import com.wcholmes.modupdater.version.ModVersionInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Handles displaying update notifications to the player.
 */
public class UpdateNotifier {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PREFIX = "[Mod Updater] ";

    /**
     * Notifies about available updates.
     */
    public static void notifyUpdatesAvailable(List<ModVersionInfo> updates) {
        if (updates.isEmpty()) {
            return;
        }

        LOGGER.info("Updates available for {} mods", updates.size());

        sendMessage("Updates available for " + updates.size() + " mod(s):", ChatFormatting.YELLOW);

        for (ModVersionInfo info : updates) {
            String msg = String.format("  • %s: %s → %s",
                    info.getModId(),
                    info.getLocalVersion() != null ? info.getLocalVersion() : "not installed",
                    info.getTargetVersion());
            sendMessage(msg, ChatFormatting.GRAY);
        }
    }

    /**
     * Notifies that update checking is in progress.
     */
    public static void notifyCheckingUpdates(int modCount) {
        LOGGER.info("Checking {} mods for updates...", modCount);
        sendMessage("Checking " + modCount + " mod(s) for updates...", ChatFormatting.AQUA);
    }

    /**
     * Notifies that no updates were found.
     */
    public static void notifyNoUpdates() {
        LOGGER.info("All mods are up to date");
        sendMessage("All mods are up to date!", ChatFormatting.GREEN);
    }

    /**
     * Notifies that downloads are starting.
     */
    public static void notifyDownloadStarting(int count) {
        LOGGER.info("Starting download of {} mods...", count);
        sendMessage("Downloading " + count + " mod(s)...", ChatFormatting.AQUA);
    }

    /**
     * Notifies about download completion.
     */
    public static void notifyDownloadComplete(DownloadQueue.DownloadResults results) {
        int successCount = results.getSuccessCount();
        int failedCount = results.getFailedCount();

        if (successCount > 0) {
            sendMessage("✓ Updated " + successCount + " mod(s)! Restart Minecraft to apply.", ChatFormatting.GREEN);

            for (DownloadQueue.DownloadResult result : results.getSuccessful()) {
                String msg = String.format("  • %s → %s", result.getModId(), result.version());
                sendMessage(msg, ChatFormatting.GRAY);
            }
        }

        if (failedCount > 0) {
            sendMessage("✗ Failed to update " + failedCount + " mod(s)", ChatFormatting.RED);

            for (DownloadQueue.DownloadResult result : results.getFailed()) {
                String msg = String.format("  • %s: %s", result.getModId(),
                        result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error");
                sendMessage(msg, ChatFormatting.GRAY);
            }

            sendMessage("Will retry failed updates on next launch.", ChatFormatting.YELLOW);
        }
    }

    /**
     * Notifies about an error during update checking.
     */
    public static void notifyError(String message) {
        LOGGER.error("Update error: {}", message);
        sendMessage("Error: " + message, ChatFormatting.RED);
    }

    /**
     * Sends a chat message to the player (client-side only).
     */
    private static void sendMessage(String message, ChatFormatting color) {
        // Only execute on client side to avoid loading client-only classes on server
        if (FMLEnvironment.dist == Dist.CLIENT) {
            sendClientMessage(message, color);
        }
    }

    /**
     * Client-side only: sends message to player's chat.
     * This method is isolated to prevent loading client classes on the server.
     */
    private static void sendClientMessage(String message, ChatFormatting color) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) {
                return;
            }

            net.minecraft.client.player.LocalPlayer player = mc.player;
            if (player != null) {
                Component text = Component.literal(PREFIX + message).withStyle(color);
                player.displayClientMessage(text, false);
            }
        } catch (Exception e) {
            // Catch any exceptions to prevent crashes
            LOGGER.warn("Failed to send client message: {}", e.getMessage());
        }
    }

    /**
     * Logs a message without sending to chat.
     */
    public static void log(String message) {
        LOGGER.info(message);
    }
}
