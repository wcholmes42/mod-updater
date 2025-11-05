package com.wcholmes.modupdater.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.wcholmes.modupdater.ModUpdater;
import com.wcholmes.modupdater.config.UpdaterConfig;
import com.wcholmes.modupdater.network.RequestConfigPacket;
import com.wcholmes.modupdater.network.UpdaterPackets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands for the Mod Updater.
 * /modupdater check - Check for updates and update mods (works on both client and server)
 * /modupdater sync - Pull latest server config, then check for updates (client-only)
 */
public class UpdaterCommands {

    /**
     * Registers all updater commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("modupdater")
                .then(Commands.literal("check")
                    .executes(UpdaterCommands::checkForUpdates))
                .then(Commands.literal("sync")
                    .executes(UpdaterCommands::syncFromServer))
        );
    }

    /**
     * Handles /modupdater check - Check for updates from GitHub and download.
     * On server: Updates server-side mods based on server config.
     * On client: Updates client-side mods based on current config (local or server-provided).
     */
    private static int checkForUpdates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!UpdaterConfig.getInstance().isEnabled()) {
            source.sendFailure(Component.literal("[Mod Updater] Updater is disabled in config"));
            return 0;
        }

        String side = source.getEntity() instanceof ServerPlayer ? "server" : "client";
        source.sendSuccess(() -> Component.literal("[Mod Updater] Checking for updates on " + side + "..."), false);

        // Run update check asynchronously
        new Thread(() -> {
            try {
                ModUpdater.checkForUpdates();
            } catch (Exception e) {
                source.sendFailure(Component.literal("[Mod Updater] Error checking for updates: " + e.getMessage()));
            }
        }, "ModUpdater-Check").start();

        return 1;
    }

    /**
     * Handles /modupdater sync - Pull latest config from server, then check for updates (client-only).
     * This requests the server's current configuration and automatically checks for updates afterward.
     */
    private static int syncFromServer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // This command only makes sense on the client side
        if (source.getEntity() instanceof ServerPlayer) {
            // We're on the server - this command doesn't apply
            source.sendFailure(Component.literal("[Mod Updater] The 'sync' command is for clients only. Use '/modupdater check' to update server mods."));
            return 0;
        }

        // Client-side execution
        if (source.getLevel().isClientSide) {
            source.sendSuccess(() -> Component.literal("[Mod Updater] Requesting configuration from server..."), false);

            // Send request to server
            UpdaterPackets.sendToServer(new RequestConfigPacket());

            // Server will respond with config, which triggers automatic update check
            return 1;
        }

        source.sendFailure(Component.literal("[Mod Updater] Not connected to a server"));
        return 0;
    }
}
