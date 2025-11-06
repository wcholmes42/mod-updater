package com.wcholmes.modupdater.network;

import com.wcholmes.modupdater.config.ManagedModConfig;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet sent from server to client containing the complete server configuration.
 * This allows drop-and-go deployment where clients don't need their own config.
 */
public class ServerConfigPacket {
    private final List<ManagedModConfig> managedMods;
    private final boolean autoDownload;
    private final boolean autoInstall;
    private final int checkIntervalMinutes;
    private final boolean periodicCheckEnabled;
    private final int downloadTimeoutSeconds;

    public ServerConfigPacket(List<ManagedModConfig> managedMods,
                             boolean autoDownload,
                             boolean autoInstall,
                             int checkIntervalMinutes,
                             boolean periodicCheckEnabled,
                             int downloadTimeoutSeconds) {
        this.managedMods = managedMods;
        this.autoDownload = autoDownload;
        this.autoInstall = autoInstall;
        this.checkIntervalMinutes = checkIntervalMinutes;
        this.periodicCheckEnabled = periodicCheckEnabled;
        this.downloadTimeoutSeconds = downloadTimeoutSeconds;
    }

    /**
     * Encodes the packet to a byte buffer.
     */
    public static void encode(ServerConfigPacket packet, FriendlyByteBuf buf) {
        // Write global settings
        buf.writeBoolean(packet.autoDownload);
        buf.writeBoolean(packet.autoInstall);
        buf.writeBoolean(packet.periodicCheckEnabled);
        buf.writeInt(packet.checkIntervalMinutes);
        buf.writeInt(packet.downloadTimeoutSeconds);

        // Write managed mods
        buf.writeInt(packet.managedMods.size());
        for (ManagedModConfig mod : packet.managedMods) {
            buf.writeUtf(mod.getModId());
            buf.writeUtf(mod.getGithubRepo());
            buf.writeUtf(mod.getJarPattern());
            buf.writeBoolean(mod.isEnabled());
            buf.writeUtf(mod.getMinVersion() != null ? mod.getMinVersion() : "");
            buf.writeUtf(mod.getUpdateChannel());
            buf.writeBoolean(mod.isRequired());
        }
    }

    /**
     * Decodes the packet from a byte buffer.
     */
    public static ServerConfigPacket decode(FriendlyByteBuf buf) {
        // Read global settings
        boolean autoDownload = buf.readBoolean();
        boolean autoInstall = buf.readBoolean();
        boolean periodicCheckEnabled = buf.readBoolean();
        int checkIntervalMinutes = buf.readInt();
        int downloadTimeoutSeconds = buf.readInt();

        // Read managed mods
        int modCount = buf.readInt();
        List<ManagedModConfig> managedMods = new ArrayList<>();

        for (int i = 0; i < modCount; i++) {
            ManagedModConfig mod = new ManagedModConfig();
            mod.setModId(buf.readUtf());
            mod.setGithubRepo(buf.readUtf());
            mod.setJarPattern(buf.readUtf());
            mod.setEnabled(buf.readBoolean());

            String minVersion = buf.readUtf();
            if (!minVersion.isEmpty()) {
                mod.setMinVersion(minVersion);
            }

            mod.setUpdateChannel(buf.readUtf());
            mod.setRequired(buf.readBoolean());

            managedMods.add(mod);
        }

        return new ServerConfigPacket(managedMods, autoDownload, autoInstall,
                                     checkIntervalMinutes, periodicCheckEnabled, downloadTimeoutSeconds);
    }

    public List<ManagedModConfig> getManagedMods() {
        return managedMods;
    }

    public boolean isAutoDownload() {
        return autoDownload;
    }

    public boolean isAutoInstall() {
        return autoInstall;
    }

    public int getCheckIntervalMinutes() {
        return checkIntervalMinutes;
    }


    public boolean isPeriodicCheckEnabled() {
        return periodicCheckEnabled;
    }

    public int getDownloadTimeoutSeconds() {
        return downloadTimeoutSeconds;
    }
}
