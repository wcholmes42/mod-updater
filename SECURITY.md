# Security Considerations

## Overview

The Mod Auto-Updater downloads and executes code (JAR files) from GitHub. This document outlines security considerations and mitigations.

## Requirements

### GitHub Repository Access

**Public repositories are REQUIRED** for the updater to function.

- ✅ Public repos: API access works without authentication
- ❌ Private repos: Would require authentication tokens (not secure for client-side)

```
https://api.github.com/repos/wcholmes42/minecraft-landscaper/releases/latest
```

## Threat Model & Mitigations

### Threat 1: Compromised GitHub Account

**Attack Scenario:**
```
Attacker gains access to your GitHub account
   ↓
Uploads malicious JAR to releases
   ↓
Users auto-download malware
```

**Mitigations Implemented:**

1. **JAR Validation** (Lines 62-102 in ModInstaller.java)
   - ✅ Validates JAR structure (valid ZIP format)
   - ✅ Requires valid manifest
   - ✅ Requires META-INF/mods.toml (Forge mod requirement)
   - ✅ Size checks (1KB minimum, 100MB maximum)
   - ❌ Does NOT verify code signatures

2. **Downgrade Protection** (Lines 37-53 in DownloadQueue.java)
   - ✅ Checks `minVersion` config setting
   - ✅ Refuses to install versions below minimum
   - ✅ Prevents rollback to vulnerable versions

**Recommended User Actions:**

1. **Enable 2FA on GitHub** (CRITICAL)
   - Settings → Password and authentication → Two-factor authentication
   - Use hardware key (YubiKey) or authenticator app
   - Makes account takeover extremely difficult

2. **Use Strong Passwords**
   - Unique password per service
   - Password manager (1Password, Bitwarden, etc.)

3. **Review GitHub Security Log**
   - Settings → Security log
   - Monitor for suspicious logins

### Threat 2: Man-in-the-Middle (MITM)

**Attack Scenario:**
```
Player on compromised network
   ↓
Attacker intercepts HTTP traffic
   ↓
Redirects download to malicious JAR
```

**Mitigations Implemented:**

1. **HTTPS Everywhere**
   ```java
   // API calls
   https://api.github.com/repos/.../releases/latest

   // Downloads
   https://github.com/.../landscaper-2.0.0.jar
   ```

2. **SSL Certificate Validation**
   - Java's HttpsURLConnection validates certs by default
   - Rejects self-signed or invalid certificates
   - Protects against basic MITM

**Protection Level:** ✅ Strong (HTTPS + cert pinning via Java defaults)

### Threat 3: Typosquatting

**Attack Scenario:**
```
User configures: "wcho1mes42/minecraft-landscaper" (typo)
Instead of:      "wcholmes42/minecraft-landscaper"
   ↓
Downloads malicious mod from attacker's repo
```

**Mitigations Implemented:**

❌ None (relies on user configuration)

**Recommended User Actions:**

1. **Verify Repository Names**
   - Copy-paste from official sources
   - Double-check spelling
   - Use trusted config files

2. **Pin to Trusted Owners**
   ```json
   {
     "managedMods": [
       {
         "modId": "landscaper",
         "githubRepo": "wcholmes42/minecraft-landscaper",
         "minVersion": "1.0.0"
       }
     ]
   }
   ```

### Threat 4: Malicious JAR Content

**Attack Scenario:**
```
JAR contains malicious code:
- Steals credentials
- Exfiltrates data
- Installs backdoors
```

**Mitigations Implemented:**

1. **Forge Mod Validation**
   - ✅ Requires META-INF/mods.toml
   - ✅ Valid JAR structure
   - ❌ Does NOT scan for malicious code

2. **Minecraft Sandbox**
   - Java security manager (limited in modern Java)
   - Minecraft/Forge mod environment
   - Not a strong sandbox

**Protection Level:** ⚠️ Limited

**Recommended Actions:**

- **Only use mods from trusted sources**
- **Review mod source code** (they're open source!)
- **Check community reputation** (CurseForge comments, GitHub stars)

### Threat 5: Version Rollback Attack

**Attack Scenario:**
```
Attacker deletes recent releases
   ↓
Latest becomes older version with vulnerabilities
   ↓
Users "update" to vulnerable version
```

**Mitigations Implemented:**

1. **Minimum Version Enforcement** (NEW!)
   ```json
   {
     "managedMods": [
       {
         "modId": "landscaper",
         "minVersion": "2.0.0"
       }
     ]
   }
   ```
   - Refuses downloads below `minVersion`
   - Prevents rollback to vulnerable versions

2. **No Downgrades by Default**
   - Only updates if `target > local`
   - Won't replace newer with older

**Protection Level:** ✅ Strong (with minVersion configured)

### Threat 6: Supply Chain (GitHub Compromise)

**Attack Scenario:**
```
GitHub itself is compromised
   ↓
All releases potentially malicious
```

**Mitigations Implemented:**

❌ None (trust GitHub)

**Reality:** If GitHub is compromised, the entire open-source ecosystem is at risk. This is an acceptable risk.

## Security Best Practices

### For Mod Developers

1. **Enable 2FA on GitHub** (CRITICAL)
2. **Sign your JARs** (optional but recommended)
   ```gradle
   jar {
       doLast {
           ant.signjar(
               jar: archiveFile.get().asFile,
               alias: 'mykey',
               keystore: project.findProperty('keystore.path')
           )
       }
   }
   ```
3. **Pin dependency versions** in build.gradle
4. **Review PRs carefully** before merging
5. **Use branch protection** on main branch
6. **Monitor security alerts** in GitHub

### For Server Operators

1. **Verify repository names** before configuring
2. **Set minVersion** for critical mods
   ```json
   {
     "managedMods": [
       {
         "modId": "landscaper",
         "githubRepo": "wcholmes42/minecraft-landscaper",
         "jarPattern": "landscaper-{version}.jar",
         "minVersion": "2.0.0"
       }
     ]
   }
   ```
3. **Test updates** on staging server first
4. **Keep backups** of working mod versions
5. **Monitor logs** for suspicious activity
6. **Disable auto-install** if cautious
   ```json
   {
     "autoDownload": true,
     "autoInstall": false  // Download but don't install
   }
   ```

### For Players

1. **Only join servers you trust**
2. **Keep updater config** from trusted sources
3. **Report suspicious behavior** to server operators
4. **Review downloaded JARs** (check file sizes, dates)

## Validation Features

### Implemented Checks

✅ **Size Validation**
- Minimum: 1KB
- Maximum: 100MB
- Rejects suspiciously small/large files

✅ **Structure Validation**
- Valid JAR/ZIP format
- Contains manifest
- Contains META-INF/mods.toml

✅ **Version Validation**
- Semantic version parsing
- Downgrade prevention
- Minimum version enforcement

✅ **Transport Security**
- HTTPS for all connections
- SSL certificate validation
- Timeout protection (30 seconds default)

### Not Implemented

❌ **Code Signing**
- Would require signing infrastructure
- Complex to maintain
- Optional future enhancement

❌ **Checksum Verification**
- Would require per-release config updates
- Defeats purpose of auto-update
- Could be added for critical mods

❌ **Sandboxing**
- Mods run in Minecraft JVM
- No additional isolation
- Relies on Java/Forge security

## Configuration Security

### Safe Configuration Example

```json
{
  "enabled": true,
  "autoDownload": true,
  "autoInstall": true,
  "checkOnStartup": true,
  "checkOnServerJoin": true,
  "downloadTimeoutSeconds": 30,
  "verboseLogging": true,
  "managedMods": [
    {
      "modId": "landscaper",
      "githubRepo": "wcholmes42/minecraft-landscaper",
      "jarPattern": "landscaper-{version}.jar",
      "enabled": true,
      "minVersion": "2.0.0",
      "updateChannel": "latest",
      "required": false
    }
  ]
}
```

### Paranoid Configuration

```json
{
  "enabled": true,
  "autoDownload": true,
  "autoInstall": false,  // ← Manual install approval
  "checkOnStartup": false,
  "checkOnServerJoin": true,
  "downloadTimeoutSeconds": 10,
  "verboseLogging": true,
  "managedMods": [
    {
      "modId": "landscaper",
      "githubRepo": "wcholmes42/minecraft-landscaper",
      "jarPattern": "landscaper-{version}.jar",
      "enabled": true,
      "minVersion": "2.0.0",  // ← Prevent rollbacks
      "updateChannel": "latest",  // ← No prereleases
      "required": true  // ← Fail if can't verify
    }
  ]
}
```

## Incident Response

### If You Suspect a Compromised Account

1. **Immediately:**
   - Change GitHub password
   - Revoke all personal access tokens
   - Enable 2FA if not already
   - Review security log

2. **Check Releases:**
   - Look for unauthorized releases
   - Delete suspicious releases
   - Re-tag legitimate releases

3. **Notify Users:**
   - Post issue on GitHub
   - Update server MOTDs
   - Provide clean download links

4. **Investigate:**
   - Check what was downloaded
   - Scan for malware
   - Review mod logs

### If You Download a Suspicious JAR

1. **Don't panic** - validation catches most issues
2. **Check the log** - look for validation failures
3. **Delete the JAR** if suspicious
4. **Disable auto-update** temporarily
   ```json
   {"enabled": false}
   ```
5. **Report to mod developer**

## Audit Log

All update operations are logged to:
- **Console log** (real-time)
- **State file:** `mods/.modupdater-state.json`

Example state:
```json
{
  "last_update": "2025-11-04T23:30:00Z",
  "updated_mods": [
    {
      "mod_id": "landscaper",
      "old_version": "1.0.0",
      "new_version": "2.0.0",
      "updated_at": "2025-11-04T23:30:00Z"
    }
  ],
  "restart_required": true
}
```

Review this file to track all updates.

## Disclaimer

**This updater downloads and executes code from the internet.**

- Only configure repositories you trust
- Verify repository names carefully
- Enable GitHub 2FA
- Set `minVersion` for critical mods
- Monitor logs for suspicious activity

**Use at your own risk.**

The updater provides convenience, not absolute security. It's as secure as:
1. Your GitHub account
2. Your network connection
3. The mods you configure

## Reporting Security Issues

If you discover a security vulnerability in the updater itself:

1. **Do NOT create a public GitHub issue**
2. **Email:** wcholmes42@[your-domain]
3. **Include:**
   - Description of vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

We'll respond within 48 hours and work on a fix.

## Future Enhancements

Potential security improvements for future versions:

- JAR code signing verification
- Checksum validation (optional per-mod)
- Rate limiting on downloads
- Cryptographic verification of releases
- Offline mode with cached versions
- Audit trail with signatures

---

**Last Updated:** 2025-11-04
**Version:** 1.0.0
