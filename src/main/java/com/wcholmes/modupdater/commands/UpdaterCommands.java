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
 * /modupdater sync - Client-only: Pull server config and update client mods from GitHub
 * /modupdater updateserver - Server-only (OP required): Update server mods from GitHub
 */
public class UpdaterCommands {

    /**
     * Registers all updater commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("modupdater")
                .then(Commands.literal("sync")
                    .executes(UpdaterCommands::syncClientMods))
                .then(Commands.literal("updateserver")
                    .requires(source -> source.hasPermission(2)) // Requires OP level 2
                    .executes(UpdaterCommands::updateServerMods))
        );
    }

    /**
     * Handles /modupdater sync - Client-only: Pull server config and update client mods from GitHub.
     * This requests the server's current configuration and automatically checks for updates afterward.
     */
    private static int syncClientMods(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // This command only makes sense on the client side
        if (source.getEntity() instanceof ServerPlayer) {
            // We're on the server - this command doesn't apply
            source.sendFailure(Component.literal("[Mod Updater] The 'sync' command is for clients only. Use '/modupdater updateserver' to update server mods."));
            return 0;
        }

        // Client-side execution
        if (source.getLevel().isClientSide) {
            source.sendSuccess(() -> Component.literal("[Mod Updater] Pulling server config and checking for updates..."), false);

            // Send request to server
            UpdaterPackets.sendToServer(new RequestConfigPacket());

            // Server will respond with config, which triggers automatic update check in ModUpdater.handleServerConfig()
            return 1;
        }

        source.sendFailure(Component.literal("[Mod Updater] Not connected to a server"));
        return 0;
    }

    /**
     * Handles /modupdater updateserver - Server-only (OP required): Update server mods from GitHub.
     * Checks GitHub based on server's config and downloads any available updates.
     */
    private static int updateServerMods(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // This command only makes sense on the server side
        if (!(source.getEntity() instanceof ServerPlayer)) {
            source.sendFailure(Component.literal("[Mod Updater] The 'updateserver' command is for server operators only. Use '/modupdater sync' on the client."));
            return 0;
        }

        if (!UpdaterConfig.getInstance().isEnabled()) {
            source.sendFailure(Component.literal("[Mod Updater] Updater is disabled in config"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Mod Updater] Checking GitHub for server mod updates..."), true);

        // Run update check asynchronously
        new Thread(() -> {
            try {
                ModUpdater.checkForUpdates();
                source.sendSuccess(() -> Component.literal("[Mod Updater] Server update check complete. Check logs for details."), true);
            } catch (Exception e) {
                source.sendFailure(Component.literal("[Mod Updater] Error checking for updates: " + e.getMessage()));
            }
        }, "ModUpdater-ServerUpdate").start();

        return 1;
    }
}
