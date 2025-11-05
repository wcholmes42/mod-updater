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
     * Handles /modupdater sync - Client command: Pull server config and update client mods from GitHub.
     * When a player runs this, the server sends its config to that player's client.
     */
    private static int syncClientMods(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Must be executed by a player (not console)
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[Mod Updater] This command must be executed by a player"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Mod Updater] Sending server config and checking for updates..."), false);

        // Send config to the requesting player's client
        ModUpdater.sendConfigToPlayer(player);

        return 1;
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
