# Mod Auto-Updater

A generic zero-touch auto-updater framework for Minecraft Forge 1.20.1 mods. Designed for family server environments where you want all your custom mods to update automatically from GitHub releases.

## Features

- **Drop-and-Go for Clients**: No configuration needed on clients - server pushes all settings automatically
- **Multi-Mod Support**: Manages updates for any number of mods from different GitHub repos
- **Zero-Touch Updates**: Automatic download and installation with no user interaction required
- **Server-Controlled Configuration**: Server operator manages all update settings for connected clients
- **Parallel Downloads**: Downloads multiple mods concurrently for faster updates
- **Flexible Configuration**: Configure via JSON file or programmatic API (server-side only)
- **Pattern-Based JAR Naming**: Supports any JAR filename pattern
- **Minimal Footprint**: < 75KB, no external dependencies
- **GitHub Release Integration**: Works directly with GitHub releases API

## Installation

### Drop-and-Go Mode (Recommended for Clients)

**For clients connecting to a server:**
1. Download the latest `modupdater-x.x.x.jar` from releases
2. Place in your `mods/` folder
3. **That's it!** The server will push the configuration automatically

The server operator controls all update settings, making deployment effortless for clients.

### Server Installation

**For servers or standalone clients:**
1. Download the latest `modupdater-x.x.x.jar` from releases
2. Place in your `mods/` folder
3. On first launch, a config file will be created at `config/modupdater.json`
4. Configure your managed mods (see Configuration section)
5. When players connect, they'll receive your configuration automatically

## Configuration

**Note:** Configuration is only required on the server or for standalone clients. Clients connecting to a server will receive the configuration automatically.

### Basic Configuration (Server-Side)

Edit `config/modupdater.json`:

```json
{
  "enabled": true,
  "autoDownload": true,
  "autoInstall": true,
  "checkOnStartup": true,
  "checkOnServerJoin": true,
  "checkIntervalMinutes": 60,
  "downloadTimeoutSeconds": 30,
  "verboseLogging": false,
  "backupOldVersions": false,
  "managedMods": [
    {
      "modId": "landscaper",
      "githubRepo": "wcholmes42/minecraft-landscaper",
      "jarPattern": "landscaper-{version}.jar",
      "enabled": true,
      "updateChannel": "latest"
    }
  ]
}
```

### Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `enabled` | Enable/disable the updater | `true` |
| `autoDownload` | Automatically download updates | `true` |
| `autoInstall` | Automatically install downloads | `true` |
| `checkOnStartup` | Check for updates on game start | `true` |
| `checkOnServerJoin` | Check when joining a server | `true` |
| `checkIntervalMinutes` | Background check interval | `60` |
| `downloadTimeoutSeconds` | Download timeout | `30` |
| `verboseLogging` | Enable debug logging | `false` |
| `backupOldVersions` | Keep old JAR files | `false` |

### Managed Mod Configuration

| Field | Description | Required |
|-------|-------------|----------|
| `modId` | Forge mod ID | Yes |
| `githubRepo` | GitHub repo (owner/name) | Yes |
| `jarPattern` | JAR filename with `{version}` | Yes |
| `enabled` | Enable updates for this mod | No (default: true) |
| `minVersion` | Minimum version to maintain | No |
| `updateChannel` | `"latest"` or `"prerelease"` | No (default: "latest") |
| `required` | Fail if update fails | No (default: false) |

## Integration

### Option 1: Configuration Only (Simplest)

Just add your mod to the config file. No code changes needed!

```json
{
  "managedMods": [
    {
      "modId": "mymod",
      "githubRepo": "username/my-minecraft-mod",
      "jarPattern": "mymod-{version}.jar"
    }
  ]
}
```

**Requirements:**
- Your GitHub releases must use tags like `v1.0.0`, `v2.0.0`, etc.
- Your release assets must match the `jarPattern` (e.g., `mymod-1.0.0.jar`)

### Option 2: Programmatic Registration (Advanced)

Register your mod via Inter-Mod Communication:

```java
@SubscribeEvent
public void onInterModEnqueue(InterModEnqueueEvent event) {
    InterModComms.sendTo("modupdater", "register_mod", () -> {
        Map<String, Object> info = new HashMap<>();
        info.put("mod_id", "mymod");
        info.put("github_repo", "username/my-minecraft-mod");
        info.put("jar_pattern", "mymod-{version}.jar");
        return info;
    });
}
```

### Option 3: Server-Side Version Enforcement

Tell clients which version is required:

```java
import com.wcholmes.modupdater.api.ModUpdaterAPI;

@SubscribeEvent
public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    ServerPlayer player = (ServerPlayer) event.getEntity();

    // Require specific version
    ModUpdaterAPI.requireVersion(player, "mymod", "2.0.0");

    // Or send multiple requirements
    Map<String, String> versions = new HashMap<>();
    versions.put("mymod", "2.0.0");
    versions.put("anothermod", "1.5.0");
    ModUpdaterAPI.sendVersionRequirements(player, versions);
}
```

## GitHub Release Setup

### 1. Version Tags

Use semantic version tags with optional `v` prefix:
- `v1.0.0` (recommended)
- `1.0.0`
- `v2.3.5`

### 2. Release Assets

Your JAR file must match the `jarPattern` from config:

**Example:**
- Pattern: `landscaper-{version}.jar`
- Tag: `v2.0.0`
- Asset: `landscaper-2.0.0.jar` ✓

### 3. Build Configuration

In your mod's `build.gradle`:

```gradle
version = '2.0.0'  // Will be used in JAR name

jar {
    archiveBaseName = 'landscaper'
    archiveVersion = project.version
    // Produces: landscaper-2.0.0.jar
}
```

## API Reference

### ModUpdaterAPI

```java
import com.wcholmes.modupdater.api.ModUpdaterAPI;

// Register a mod
ModUpdaterAPI.registerMod("mymod", "username/repo", "mymod-{version}.jar");

// Check if registered
boolean registered = ModUpdaterAPI.isModRegistered("mymod");

// Get mod config
ManagedModConfig config = ModUpdaterAPI.getModConfig("mymod");

// Server: require version
ModUpdaterAPI.requireVersion(player, "mymod", "2.0.0", true);
```

## User Experience

### Client-Side Messages

```
[Mod Updater] Checking 3 mod(s) for updates...
[Mod Updater] Updates available for 2 mod(s):
[Mod Updater]   • landscaper: 1.0.0 → 2.0.0
[Mod Updater]   • coolmod: 1.2.0 → 1.5.0
[Mod Updater] Downloading 2 mod(s)...
[Mod Updater] ✓ Updated 2 mod(s)! Restart Minecraft to apply.
[Mod Updater]   • landscaper → 2.0.0
[Mod Updater]   • coolmod → 1.5.0
```

### Error Handling

```
[Mod Updater] ✗ Failed to update landscaper: Network timeout
[Mod Updater] ✓ coolmod updated successfully
[Mod Updater] Will retry failed updates on next launch.
```

## Example: Landscaper Integration

### Landscaper's GitHub Setup

1. Repository: `wcholmes42/minecraft-landscaper`
2. Release tag: `v2.0.0`
3. Asset file: `landscaper-2.0.0.jar`

### Client Configuration

`config/modupdater.json`:
```json
{
  "managedMods": [
    {
      "modId": "landscaper",
      "githubRepo": "wcholmes42/minecraft-landscaper",
      "jarPattern": "landscaper-{version}.jar"
    }
  ]
}
```

### Server-Side (Optional)

```java
@SubscribeEvent
public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    ServerPlayer player = (ServerPlayer) event.getEntity();
    ModUpdaterAPI.requireVersion(player, "landscaper", "2.0.0");
}
```

**Result:** Clients automatically download and install Landscaper 2.0.0 from GitHub!

## Multiple Mods Example

```json
{
  "managedMods": [
    {
      "modId": "landscaper",
      "githubRepo": "wcholmes42/minecraft-landscaper",
      "jarPattern": "landscaper-{version}.jar"
    },
    {
      "modId": "coolbuilder",
      "githubRepo": "wcholmes42/cool-builder-mod",
      "jarPattern": "coolbuilder-{version}.jar"
    },
    {
      "modId": "familytools",
      "githubRepo": "wcholmes42/family-tools",
      "jarPattern": "familytools_{version}.jar"
    }
  ]
}
```

All three mods will be automatically managed and updated!

## Troubleshooting

### Updates Not Detected

1. Check GitHub release exists and is public
2. Verify `jarPattern` matches asset name exactly
3. Ensure version tag format is correct (e.g., `v1.0.0`)
4. Check logs for API errors

### Download Fails

1. Check internet connection
2. Verify GitHub release asset is publicly accessible
3. Increase `downloadTimeoutSeconds` in config
4. Check logs for detailed error messages

### Version Mismatch

1. Ensure JAR filename includes version (e.g., `mod-1.0.0.jar`)
2. Pattern must contain `{version}` placeholder
3. Check logs for version detection results

## Technical Details

### Architecture

```
ModUpdater (Main)
├── Config (UpdaterConfig, ManagedModConfig)
├── Registry (ModRegistry)
├── Version
│   ├── SemanticVersion
│   ├── ModVersionInfo
│   ├── LocalModScanner
│   └── VersionChecker
├── GitHub
│   ├── GitHubAPI
│   ├── Release
│   └── ReleaseCache
├── Download
│   ├── DownloadQueue
│   ├── ModDownloader
│   └── ModInstaller
├── Network
│   ├── UpdaterPackets
│   └── ServerModVersionsPacket
├── UI (UpdateNotifier)
└── API (ModUpdaterAPI)
```

### Update Decision Logic

For each mod:
1. Get GitHub latest version (A)
2. Get server required version (B)
3. Get local installed version (C)
4. Target = B if exists, else A
5. If target > local, queue download

### File Locations

- Config: `config/modupdater.json`
- Mods: `mods/*.jar`
- State: `mods/.modupdater-state.json`
- Temp downloads: `mods/.{modid}-update.tmp`

## License

MIT License - see LICENSE file

## Author

wcholmes42

## Support

Issues: https://github.com/wcholmes42/mod-updater/issues
