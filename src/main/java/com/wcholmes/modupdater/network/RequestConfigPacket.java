package com.wcholmes.modupdater.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet sent from client to server requesting the configuration.
 * Used by /modupdater sync command.
 */
public class RequestConfigPacket {

    public RequestConfigPacket() {
        // Empty packet - just a signal
    }

    /**
     * Encodes the packet to a byte buffer.
     */
    public static void encode(RequestConfigPacket packet, FriendlyByteBuf buf) {
        // Empty packet - nothing to encode
    }

    /**
     * Decodes the packet from a byte buffer.
     */
    public static RequestConfigPacket decode(FriendlyByteBuf buf) {
        return new RequestConfigPacket();
    }
}
