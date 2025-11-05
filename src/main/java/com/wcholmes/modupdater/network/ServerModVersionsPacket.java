package com.wcholmes.modupdater.network;

import com.wcholmes.modupdater.version.ModVersionInfo;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * Packet sent from server to client containing required mod versions.
 */
public class ServerModVersionsPacket {
    private final Map<String, ModVersionInfo> modVersions;

    public ServerModVersionsPacket(Map<String, ModVersionInfo> modVersions) {
        this.modVersions = modVersions;
    }

    /**
     * Encodes the packet to a byte buffer.
     */
    public static void encode(ServerModVersionsPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.modVersions.size());

        for (Map.Entry<String, ModVersionInfo> entry : packet.modVersions.entrySet()) {
            String modId = entry.getKey();
            ModVersionInfo info = entry.getValue();

            buf.writeUtf(modId);
            buf.writeUtf(info.getServerVersion() != null ? info.getServerVersion() : "");
            buf.writeBoolean(info.isRequired());
        }
    }

    /**
     * Decodes the packet from a byte buffer.
     */
    public static ServerModVersionsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<String, ModVersionInfo> modVersions = new HashMap<>();

        for (int i = 0; i < size; i++) {
            String modId = buf.readUtf();
            String version = buf.readUtf();
            boolean required = buf.readBoolean();

            ModVersionInfo info = new ModVersionInfo(modId, version, required);
            modVersions.put(modId, info);
        }

        return new ServerModVersionsPacket(modVersions);
    }

    public Map<String, ModVersionInfo> getModVersions() {
        return modVersions;
    }
}
