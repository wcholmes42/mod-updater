# Integration Guide

This guide shows how to integrate your mod with the Mod Auto-Updater.

## Quick Start

The simplest integration requires **zero code changes** to your mod!

### Step 1: Configure Your Build

In your `build.gradle`:

```gradle
version = '1.0.0'

jar {
    archiveBaseName = 'mymod'
    archiveVersion = project.version
}
// This produces: mymod-1.0.0.jar
```

### Step 2: Create GitHub Releases

1. Create a git tag: `git tag v1.0.0`
2. Push the tag: `git push origin v1.0.0`
3. Create a GitHub release for the tag
4. Upload your JAR file as a release asset

### Step 3: Add to Config

Users add this to their `config/modupdater.json`:

```json
{
  "managedMods": [
    {
      "modId": "mymod",
      "githubRepo": "yourusername/your-repo",
      "jarPattern": "mymod-{version}.jar"
    }
  ]
}
```

**Done!** Your mod will now auto-update from GitHub releases.

---

## Integration Options

### Option 1: Config-Only (Recommended)

**Pros:**
- No code needed
- User-configurable
- Simple

**Cons:**
- User must manually add to config
- No version enforcement

**Use when:** You want users to opt-in to updates

### Option 2: IMC Registration

**Pros:**
- Automatic registration
- No user config needed
- Clean API

**Cons:**
- Requires mod code changes
- Still no version enforcement

**Implementation:**

```java
package com.example.mymod;

import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MyMod {
    @SubscribeEvent
    public void onInterModEnqueue(InterModEnqueueEvent event) {
        InterModComms.sendTo("modupdater", "register_mod", () -> {
            Map<String, Object> info = new HashMap<>();
            info.put("mod_id", "mymod");
            info.put("github_repo", "yourusername/your-repo");
            info.put("jar_pattern", "mymod-{version}.jar");
            info.put("enabled", true);
            info.put("update_channel", "latest");
            return info;
        });
    }
}
```

### Option 3: Server Version Enforcement

**Pros:**
- Server controls client versions
- Ensures compatibility
- Can be combined with other options

**Cons:**
- Requires server-side code
- Only works when connected to server

**Implementation:**

```java
package com.example.mymod;

import com.wcholmes.modupdater.api.ModUpdaterAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MyModServerEvents {
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();

        // Get server's mod version
        String serverVersion = getMyModVersion();

        // Tell client to update if needed
        ModUpdaterAPI.requireVersion(player, "mymod", serverVersion, false);
    }

    private String getMyModVersion() {
        return net.minecraftforge.fml.ModList.get()
            .getModContainerById("mymod")
            .map(mc -> mc.getModInfo().getVersion().toString())
            .orElse("1.0.0");
    }
}
```

---

## Complete Example: Landscaper Mod

### Project Structure

```
minecraft-landscaper/
├── build.gradle
├── src/main/java/com/wcholmes/landscaper/
│   ├── Landscaper.java
│   └── LandscaperEvents.java
└── src/main/resources/META-INF/
    └── mods.toml
```

### build.gradle

```gradle
version = '2.0.0'
group = 'com.wcholmes.landscaper'

base {
    archivesName = 'landscaper'
}

jar {
    manifest {
        attributes([
            "Implementation-Version": project.version
        ])
    }
}
```

### LandscaperEvents.java (Server-Side)

```java
package com.wcholmes.landscaper;

import com.wcholmes.modupdater.api.ModUpdaterAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "landscaper")
public class LandscaperEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();

        // Tell client they need version 2.0.0
        ModUpdaterAPI.requireVersion(player, "landscaper", "2.0.0");
    }
}
```

### GitHub Release Workflow

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2

      - name: Set up JDK 17
        uses: actions/setup-java@v2
        with:
          java-version: '17'

      - name: Build with Gradle
        run: ./gradlew build

      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: build/libs/landscaper-*.jar
```

### Client Configuration

Users add to `config/modupdater.json`:

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

### What Happens

1. User starts Minecraft
2. Updater checks GitHub for latest release
3. Finds `v2.0.0` with asset `landscaper-2.0.0.jar`
4. Compares with local `landscaper-1.0.0.jar`
5. Downloads and installs `landscaper-2.0.0.jar`
6. Deletes old `landscaper-1.0.0.jar`
7. Prompts user to restart

When user connects to server:
1. Server sends version requirement: `landscaper 2.0.0`
2. Client already has 2.0.0, no action needed
3. If client had older version, would trigger update

---

## Advanced Patterns

### Multiple Mods in One Repo

If you have multiple mods in one repository:

```json
{
  "managedMods": [
    {
      "modId": "mymod-core",
      "githubRepo": "username/my-mods",
      "jarPattern": "mymod-core-{version}.jar"
    },
    {
      "modId": "mymod-addon",
      "githubRepo": "username/my-mods",
      "jarPattern": "mymod-addon-{version}.jar"
    }
  ]
}
```

Your release should include both JARs:
- `mymod-core-1.0.0.jar`
- `mymod-addon-1.0.0.jar`

### Different JAR Naming Conventions

**Underscores instead of hyphens:**
```json
{
  "jarPattern": "mymod_{version}.jar"
}
```
Matches: `mymod_1.0.0.jar`

**Prefix:**
```json
{
  "jarPattern": "forge-mymod-{version}.jar"
}
```
Matches: `forge-mymod-1.0.0.jar`

**Custom format:**
```json
{
  "jarPattern": "MyAwesomeMod-mc1.20.1-{version}.jar"
}
```
Matches: `MyAwesomeMod-mc1.20.1-2.0.0.jar`

### Pre-release Channel

```json
{
  "modId": "mymod",
  "githubRepo": "username/mymod",
  "jarPattern": "mymod-{version}.jar",
  "updateChannel": "prerelease"
}
```

This will pull the latest release even if marked as pre-release on GitHub.

### Minimum Version Protection

```json
{
  "modId": "mymod",
  "githubRepo": "username/mymod",
  "jarPattern": "mymod-{version}.jar",
  "minVersion": "1.5.0"
}
```

Updater will never downgrade below version 1.5.0.

---

## Testing Your Integration

### 1. Local Testing

Create a test release:

1. Tag: `v1.0.0-test`
2. Mark as pre-release
3. Upload your JAR
4. Set `updateChannel` to `"prerelease"` in config
5. Launch Minecraft and watch logs

### 2. Check Logs

Look for these messages:

```
[Mod Updater] Registered mod from config: mymod
[Mod Updater] Checking 1 mods for updates...
[Mod Updater] Fetched latest release for username/mymod: v1.0.0-test
[Mod Updater] Found asset for mymod: mymod-1.0.0-test.jar (12345)
```

### 3. Verify Download

Check `mods/` folder:
- Old JAR should be deleted
- New JAR should be present
- Temp file `.mymod-update.tmp` should be gone

### 4. Test Version Detection

Check logs for:
```
[Mod Updater] Found mymod version 1.0.0-test from filename
```

---

## Common Issues

### Issue: Asset Not Found

**Symptom:**
```
[Mod Updater] No matching asset found for mymod with pattern mymod-{version}.jar
```

**Solution:**
- Check your JAR filename exactly matches pattern
- Pattern: `mymod-{version}.jar`
- Tag: `v1.0.0`
- File must be: `mymod-1.0.0.jar` (NOT `mymod-v1.0.0.jar`)

### Issue: Version Not Detected

**Symptom:**
```
[Mod Updater] Could not determine version for JAR: mymod-weird-name.jar
```

**Solution:**
- Ensure JAR name includes version
- Update `jarPattern` to match your actual JAR name
- Or rename your JAR to match pattern

### Issue: Downloads But Doesn't Install

**Symptom:** Download succeeds but old version remains

**Solution:**
- Check `autoInstall` is `true` in config
- Check file permissions in `mods/` folder
- Look for errors in logs

### Issue: No Updates Detected

**Symptom:** Updater says "no updates" but you know there's a new version

**Solution:**
- Verify GitHub release is NOT marked as draft
- Check release is public (not private repo)
- Clear cache: delete `mods/.modupdater-state.json`
- Increase logging: set `verboseLogging: true`

---

## API Reference

### ModUpdaterAPI Methods

```java
// Register a mod
boolean registerMod(String modId, String githubRepo, String jarPattern)

// Register with full config
boolean registerMod(ManagedModConfig config)

// Check if registered
boolean isModRegistered(String modId)

// Get config
ManagedModConfig getModConfig(String modId)

// Server: require version (non-required)
void requireVersion(ServerPlayer player, String modId, String version)

// Server: require version (with required flag)
void requireVersion(ServerPlayer player, String modId, String version, boolean required)

// Server: send multiple requirements
void sendVersionRequirements(ServerPlayer player, Map<String, String> versions)
```

---

## Best Practices

1. **Use Semantic Versioning**
   - Tags: `v1.0.0`, `v1.1.0`, `v2.0.0`
   - Incremental and predictable

2. **Test Releases First**
   - Use pre-releases for testing
   - Mark stable releases appropriately

3. **Include Version in JAR**
   - Add to manifest: `Implementation-Version`
   - Makes debugging easier

4. **Document for Users**
   - Tell users updater is available
   - Provide example config

5. **Server Enforcement**
   - Use for critical compatibility updates
   - Don't enforce for optional features

6. **Clear Release Notes**
   - Users can see what's changing
   - Build trust in auto-updates

---

## Support

Questions? Check:
- Main README.md
- GitHub Issues
- Example mods in `examples/` folder
