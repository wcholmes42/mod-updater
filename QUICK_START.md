# Quick Start Guide

## First Time Setup (5 minutes)

### 1. Generate Signing Key

```bash
keytool -genkeypair \
  -alias modupdater \
  -keyalg RSA \
  -keysize 4096 \
  -validity 3650 \
  -keystore modupdater-keystore.jks \
  -storepass "YourStrongPassword123!" \
  -dname "CN=modupdater, O=wcholmes42"
```

### 2. Encode for GitHub

```bash
# Linux/Mac/Git Bash
base64 -w 0 modupdater-keystore.jks > keystore.b64

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("modupdater-keystore.jks")) | Out-File -Encoding ASCII keystore.b64
```

### 3. Add GitHub Secrets

Go to: **Repository → Settings → Secrets → Actions → New secret**

Add three secrets:
- `SIGNING_KEYSTORE_BASE64` = contents of keystore.b64
- `SIGNING_PASSWORD` = your keystore password
- `SIGNING_ALIAS` = `modupdater`

### 4. Test Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

Watch **Actions** tab for build progress. Done!

---

## Every Release (30 seconds)

```bash
# Update version in build.gradle if needed
# Commit changes
git add .
git commit -m "Prepare v1.1.0 release"

# Tag and push
git tag v1.1.0
git push && git push --tags
```

GitHub Actions automatically:
- ✅ Builds JAR
- ✅ Signs JAR
- ✅ Verifies signature
- ✅ Creates GitHub Release
- ✅ Uploads signed JAR

---

## Client Setup (Users)

### Install Updater

1. Download `modupdater-1.0.0.jar` from releases
2. Place in `mods/` folder
3. Create `config/modupdater.json`:

```json
{
  "enabled": true,
  "autoDownload": true,
  "autoInstall": true,
  "managedMods": [
    {
      "modId": "landscaper",
      "githubRepo": "wcholmes42/minecraft-landscaper",
      "jarPattern": "landscaper-{version}.jar",
      "minVersion": "1.0.0"
    }
  ]
}
```

4. Launch Minecraft
5. Updates happen automatically!

---

## For Mod Developers

### Integrate Your Mod

**Option 1: Config Only (No code changes)**

Tell users to add to their `config/modupdater.json`:

```json
{
  "modId": "yourmod",
  "githubRepo": "yourname/your-mod-repo",
  "jarPattern": "yourmod-{version}.jar"
}
```

**Option 2: Server Version Enforcement**

```java
import com.wcholmes.modupdater.api.ModUpdaterAPI;

@SubscribeEvent
public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    ServerPlayer player = (ServerPlayer) event.getEntity();
    ModUpdaterAPI.requireVersion(player, "yourmod", "2.0.0");
}
```

### GitHub Release Checklist

- ✅ Tag format: `v1.0.0` (with 'v' prefix)
- ✅ JAR name: `yourmod-1.0.0.jar` (matches pattern)
- ✅ Release is public (not draft)
- ✅ Asset is uploaded

---

## Security

### ✅ Implemented

- **JAR Signing:** All releases cryptographically signed
- **Signature Verification:** Client validates before install
- **HTTPS Only:** All downloads encrypted
- **Size Validation:** Rejects too-small/too-large JARs
- **Structure Validation:** Verifies JAR format & Forge metadata
- **Downgrade Protection:** Enforces `minVersion` setting

### 🔒 You Must Do

1. **Enable 2FA on GitHub** (CRITICAL!)
2. **Use strong keystore password**
3. **Never commit keystore to git** (already in .gitignore)
4. **Backup keystore securely**

### ⚠️ Users Should Do

```json
{
  "managedMods": [
    {
      "modId": "landscaper",
      "minVersion": "2.0.0"  // ← Prevents rollback attacks
    }
  ]
}
```

---

## Architecture

```
Player launches Minecraft
   ↓
Updater checks GitHub API for each mod
   ↓
Compares GitHub version vs local version
   ↓
If update available → Download to mods/.mod-update.tmp
   ↓
Verify signature (CN=modupdater check)
   ↓
If valid → Delete old JAR, rename new JAR
   ↓
Prompt restart
   ↓
Play with latest version!
```

**Server Sync (Optional):**
```
Player joins server
   ↓
Server sends: "I need landscaper v2.0.0"
   ↓
Client checks local version
   ↓
If mismatch → Download v2.0.0
   ↓
Everyone on same version!
```

---

## Troubleshooting

### Updates Not Working

1. Check logs: `logs/latest.log`
2. Look for: `[Mod Updater]` lines
3. Common issues:
   - Wrong `githubRepo` (typo?)
   - Wrong `jarPattern` (doesn't match actual JAR name?)
   - GitHub release not public
   - No matching asset in release

### Signature Verification Failed

```
❌ JAR signature is not from trusted signer
```

**Fix:** Check keystore DN matches ModInstaller.java expectations
```bash
keytool -list -v -keystore modupdater-keystore.jks
# Look for: Owner: CN=modupdater
```

### Build Fails in GitHub Actions

1. Check Actions tab for error
2. Common issues:
   - `SIGNING_KEYSTORE_BASE64` truncated (re-encode)
   - `SIGNING_PASSWORD` wrong
   - Workflow file syntax error

### JAR Not Signed

```bash
# Check if JAR is signed
jarsigner -verify modupdater-1.0.0.jar

# Should see:
# jar verified.

# If unsigned:
# jar is unsigned.
```

**Fix:** Ensure GitHub Secrets are configured

---

## File Structure

```
Updater/
├── .github/workflows/release.yml  # CI/CD automation
├── build.gradle                    # Build config with signing
├── src/main/java/.../
│   ├── ModUpdater.java             # Main mod class
│   ├── api/ModUpdaterAPI.java      # Public API
│   ├── download/ModInstaller.java  # Signature verification
│   └── ...
├── README.md                       # User documentation
├── INTEGRATION.md                  # Developer guide
├── SECURITY.md                     # Security details
├── JAR_SIGNING_SETUP.md            # Complete signing setup
└── QUICK_START.md                  # This file
```

---

## Links

- **Full Security Documentation:** [SECURITY.md](SECURITY.md)
- **JAR Signing Setup:** [JAR_SIGNING_SETUP.md](JAR_SIGNING_SETUP.md)
- **Integration Guide:** [INTEGRATION.md](INTEGRATION.md)
- **Main README:** [README.md](README.md)

---

## Support

- **Issues:** https://github.com/wcholmes42/mod-updater/issues
- **Discussions:** https://github.com/wcholmes42/mod-updater/discussions

---

**That's it! Happy auto-updating! 🎉**
