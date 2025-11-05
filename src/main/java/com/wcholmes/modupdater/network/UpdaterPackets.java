package com.wcholmes.modupdater.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Manages network communication for the mod updater.
 */
public class UpdaterPackets {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("modupdater", "main"),
            () -> PROTOCOL_VERSION,
            serverVersion -> true,  // Accept any server version
            clientVersion -> true   // Accept any client version
    );

    private static int packetId = 0;

    /**
     * Registers all network packets.
     */
    public static void register() {
        CHANNEL.messageBuilder(ServerModVersionsPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerModVersionsPacket::encode)
                .decoder(ServerModVersionsPacket::decode)
                .consumerMainThread(UpdaterPackets::handleServerModVersionsPacket)
                .add();

        CHANNEL.messageBuilder(ServerConfigPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerConfigPacket::encode)
                .decoder(ServerConfigPacket::decode)
                .consumerMainThread(UpdaterPackets::handleServerConfigPacket)
                .add();

        CHANNEL.messageBuilder(RequestConfigPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestConfigPacket::encode)
                .decoder(RequestConfigPacket::decode)
                .consumerMainThread(UpdaterPackets::handleRequestConfigPacket)
                .add();
    }

    /**
     * Handles the ServerModVersionsPacket on the client.
     */
    private static void handleServerModVersionsPacket(ServerModVersionsPacket packet,
                                                      java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctxSupplier) {
        net.minecraftforge.network.NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Import here to avoid class loading issues
            com.wcholmes.modupdater.ModUpdater.handleServerVersions(packet.getModVersions());
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Handles the ServerConfigPacket on the client.
     */
    private static void handleServerConfigPacket(ServerConfigPacket packet,
                                                 java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctxSupplier) {
        net.minecraftforge.network.NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Import here to avoid class loading issues
            com.wcholmes.modupdater.ModUpdater.handleServerConfig(packet);
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Sends a packet to a specific player.
     */
    public static void sendToPlayer(ServerModVersionsPacket packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Sends a config packet to a specific player.
     */
    public static void sendToPlayer(ServerConfigPacket packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Handles the RequestConfigPacket on the server.
     */
    private static void handleRequestConfigPacket(RequestConfigPacket packet,
                                                  java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctxSupplier) {
        net.minecraftforge.network.NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                com.wcholmes.modupdater.ModUpdater.sendConfigToPlayer(player);
            }
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Sends a packet from client to server.
     */
    public static void sendToServer(RequestConfigPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
