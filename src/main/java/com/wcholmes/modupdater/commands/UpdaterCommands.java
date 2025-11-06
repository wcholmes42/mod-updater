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
 * /modupdater - Client: Pull server config and update client mods from GitHub
 * /modupdater server - Server-only (OP required): Refresh config and update server mods from GitHub
 */
public class UpdaterCommands {

    /**
     * Registers all updater commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("modupdater")
                .executes(UpdaterCommands::syncClientMods)
                .then(Commands.literal("server")
                    .executes(UpdaterCommands::updateServerMods))
        );
    }

    /**
     * Handles /modupdater - Client command: Pull server config and update client mods from GitHub.
     * When a player runs this, the server sends its config to that player's client.
     */
    private static int syncClientMods(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Must be executed by a player (not console)
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[Mod Updater] This command must be executed by a player"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Mod Updater] Pulling server config and checking for updates..."), false);

        // Send config to the requesting player's client
        ModUpdater.sendConfigToPlayer(player);

        return 1;
    }

    /**
     * Handles /modupdater server - Server-only (OP required): Refresh config and update server mods.
     * Refreshes config from GitHub (if bootstrap), then checks for mod updates.
     */
    private static int updateServerMods(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // This command only makes sense on the server side
        if (!(source.getEntity() instanceof ServerPlayer)) {
            source.sendFailure(Component.literal("[Mod Updater] The 'server' command is for server operators only. Use '/modupdater' as a client."));
            return 0;
        }

        // Check if player has OP permissions (level 2 or higher)
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("[Mod Updater] You must be a server operator to use this command"));
            return 0;
        }

        if (!UpdaterConfig.getInstance().isEnabled()) {
            source.sendFailure(Component.literal("[Mod Updater] Updater is disabled in config"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Mod Updater] Refreshing config and checking for updates..."), true);

        // Run config refresh and update check asynchronously
        new Thread(() -> {
            try {
                // First, try to refresh config from GitHub (if bootstrap config exists)
                boolean configRefreshed = UpdaterConfig.refreshConfig();
                if (configRefreshed) {
                    source.sendSuccess(() -> Component.literal("[Mod Updater] Config refreshed from GitHub"), true);
                    // Reload mod registry to pick up new config
                    com.wcholmes.modupdater.registry.ModRegistry.getInstance().reload();
                } else {
                    // Not an error - just means no bootstrap config or refresh not needed
                    source.sendSuccess(() -> Component.literal("[Mod Updater] Using local config"), true);
                }

                // Check for mod updates (always fetches fresh from GitHub with cache-busting)
                ModUpdater.checkForUpdates();
                source.sendSuccess(() -> Component.literal("[Mod Updater] Server update check complete. Check logs for details."), true);
            } catch (Exception e) {
                source.sendFailure(Component.literal("[Mod Updater] Error during update: " + e.getMessage()));
            }
        }, "ModUpdater-ServerUpdate").start();

        return 1;
    }
}
