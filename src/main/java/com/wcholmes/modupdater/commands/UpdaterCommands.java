package com.wcholmes.modupdater.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.wcholmes.modupdater.ModUpdater;
import com.wcholmes.modupdater.config.UpdaterConfig;
import com.wcholmes.modupdater.network.ServerConfigPacket;
import com.wcholmes.modupdater.network.UpdaterPackets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands for the Mod Updater.
 * /modupdater check - Check for updates locally
 * /modupdater sync - Force sync from server (client-side only)
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
     * Handles /modupdater check - Check for updates from GitHub.
     */
    private static int checkForUpdates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!UpdaterConfig.getInstance().isEnabled()) {
            source.sendFailure(Component.literal("[Mod Updater] Updater is disabled in config"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Mod Updater] Checking for updates..."), false);

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
     * Handles /modupdater sync - Force sync from server (client-side only).
     */
    private static int syncFromServer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // This command only makes sense on the client side
        if (source.getEntity() instanceof ServerPlayer) {
            // We're on the server - this command doesn't apply
            source.sendFailure(Component.literal("[Mod Updater] The 'sync' command is for clients only. Server already has the config."));
            return 0;
        }

        // Check if we're connected to a server
        if (source.getLevel().isClientSide) {
            source.sendSuccess(() -> Component.literal("[Mod Updater] Requesting configuration from server..."), false);

            // On client, we can't directly request from server in current implementation
            // The server automatically pushes config on login
            // So we'll just inform the user
            source.sendSuccess(() -> Component.literal("[Mod Updater] Note: Server automatically sends config on join. Use '/modupdater check' to check for updates now."), false);
            return 1;
        }

        source.sendFailure(Component.literal("[Mod Updater] Not connected to a server"));
        return 0;
    }
}
