# JAR Signing Setup Guide

This guide explains how to set up JAR signing for the Mod Auto-Updater with GitHub Actions CI/CD.

## Overview

JAR signing provides cryptographic verification that:
1. The JAR was created by you (not an attacker)
2. The JAR hasn't been modified since signing
3. The updater can verify authenticity before installation

## Quick Start

1. Generate a keystore (one-time setup)
2. Add secrets to GitHub repository
3. Push a version tag to trigger release
4. GitHub Actions builds, signs, and releases automatically

---

## Step 1: Generate Keystore (One-Time Setup)

### On Your Local Machine

```bash
# Generate a new keystore with RSA 4096-bit key
keytool -genkeypair \
  -alias modupdater \
  -keyalg RSA \
  -keysize 4096 \
  -validity 3650 \
  -keystore modupdater-keystore.jks \
  -storepass "YOUR_STRONG_PASSWORD_HERE" \
  -keypass "YOUR_STRONG_PASSWORD_HERE" \
  -dname "CN=modupdater, OU=Minecraft Mods, O=wcholmes42, L=City, ST=State, C=US"
```

**Important:**
- Replace `YOUR_STRONG_PASSWORD_HERE` with a strong password (save it!)
- Keep this keystore file **SECRET** - never commit it to git
- Store it in a password manager
- Validity is 10 years (3650 days)

### Parameters Explained

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `-alias` | modupdater | Name to reference this key |
| `-keyalg` | RSA | Encryption algorithm |
| `-keysize` | 4096 | Key strength (4096 bits = very strong) |
| `-validity` | 3650 | Valid for 10 years |
| `-dname` | CN=modupdater... | Your identity info |

**CN (Common Name):** This is what the updater checks for signature validation!

### Verify Keystore

```bash
# List keystore contents
keytool -list -v -keystore modupdater-keystore.jks -storepass YOUR_PASSWORD

# You should see:
# Alias name: modupdater
# Entry type: PrivateKeyEntry
# Certificate chain length: 1
```

---

## Step 2: Encode Keystore for GitHub

GitHub Secrets can't store binary files directly, so we encode it as Base64:

```bash
# Linux/Mac
base64 -w 0 modupdater-keystore.jks > keystore.b64

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("modupdater-keystore.jks")) | Out-File -Encoding ASCII keystore.b64

# Windows (Git Bash)
base64 -w 0 modupdater-keystore.jks > keystore.b64
```

This creates `keystore.b64` containing the Base64-encoded keystore.

---

## Step 3: Add GitHub Secrets

### Navigate to Repository Settings

1. Go to your GitHub repository
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**

### Add Three Secrets

#### Secret 1: SIGNING_KEYSTORE_BASE64

- **Name:** `SIGNING_KEYSTORE_BASE64`
- **Value:** Contents of `keystore.b64` file (copy entire contents)
- Click **Add secret**

#### Secret 2: SIGNING_PASSWORD

- **Name:** `SIGNING_PASSWORD`
- **Value:** The password you used when creating the keystore
- Click **Add secret**

#### Secret 3: SIGNING_ALIAS

- **Name:** `SIGNING_ALIAS`
- **Value:** `modupdater` (or whatever alias you used)
- Click **Add secret**

### Verify Secrets

You should now have three secrets:
- ✅ `SIGNING_KEYSTORE_BASE64`
- ✅ `SIGNING_PASSWORD`
- ✅ `SIGNING_ALIAS`

---

## Step 4: Test the Workflow

### Create a Release Tag

```bash
# Commit your changes
git add .
git commit -m "Add JAR signing support"

# Create and push a version tag
git tag v1.0.0
git push origin v1.0.0
```

### Watch the Build

1. Go to **Actions** tab in GitHub
2. You should see "Build and Release" workflow running
3. Expand steps to see progress:
   - ✅ Checkout code
   - ✅ Set up JDK 17
   - ✅ Decode keystore from secret
   - ✅ Build and sign JAR
   - ✅ Verify JAR signature
   - ✅ Create Release

### Verify the Release

1. Go to **Releases** tab
2. You should see `v1.0.0` release
3. Download the JAR file
4. Verify signature locally:

```bash
# Verify the JAR is signed
jarsigner -verify -verbose modupdater-1.0.0.jar

# You should see:
# jar verified.
```

---

## Workflow Explained

### Trigger

```yaml
on:
  push:
    tags:
      - 'v*'  # Any tag starting with 'v'
```

Triggers when you push: `v1.0.0`, `v2.1.3`, `v1.0.0-beta`, etc.

### Build Steps

1. **Decode Keystore:** Base64 decode secret → temporary file
2. **Build JAR:** Run `./gradlew build`
3. **Sign JAR:** Gradle automatically signs using `jar.doLast` hook
4. **Verify:** Run `jarsigner -verify` to ensure signing worked
5. **Create Release:** Upload signed JAR to GitHub Releases
6. **Cleanup:** Delete temporary keystore file

### Security

- ✅ Keystore never stored in repository
- ✅ Keystore decoded only in GitHub Actions runner (temporary)
- ✅ Keystore deleted after build
- ✅ Secrets encrypted by GitHub
- ✅ Workflow logs redact secret values

---

## Local Development (Optional)

If you want to sign JARs locally for testing:

### Option 1: Gradle Properties (Recommended)

Create `gradle.properties` in project root (gitignored):

```properties
signing.keystore=/path/to/modupdater-keystore.jks
signing.password=YOUR_PASSWORD
signing.alias=modupdater
```

Build:
```bash
./gradlew build
```

### Option 2: Environment Variables

```bash
# Linux/Mac
export SIGNING_KEYSTORE=/path/to/modupdater-keystore.jks
export SIGNING_PASSWORD=YOUR_PASSWORD
export SIGNING_ALIAS=modupdater
./gradlew build

# Windows (PowerShell)
$env:SIGNING_KEYSTORE="C:\path\to\modupdater-keystore.jks"
$env:SIGNING_PASSWORD="YOUR_PASSWORD"
$env:SIGNING_ALIAS="modupdater"
./gradlew build
```

### Option 3: Skip Signing

If no keystore is configured, build still works but JAR is unsigned:

```bash
./gradlew build

# Output:
# WARNING: JAR signing skipped (no keystore configured)
```

---

## Signature Verification (Client-Side)

The updater automatically verifies signatures when downloading JARs.

### What It Checks

1. ✅ JAR is signed (not tampered with)
2. ✅ Signature is from trusted signer (CN=modupdater, CN=wcholmes42, or O=wcholmes42)
3. ✅ All entries are signed (no partial tampering)

### Behavior

| Scenario | Behavior |
|----------|----------|
| JAR is signed by wcholmes42 | ✅ Accepts and installs |
| JAR is signed by someone else | ❌ Rejects with error |
| JAR is unsigned | ⚠️ Warns but allows (backwards compatibility) |
| JAR signature is invalid | ❌ Rejects with error |

### Future: Require Signatures

To enforce signatures (reject unsigned JARs):

Edit `ModInstaller.java` line 194:
```java
// Change from:
return true;  // Allow unsigned

// To:
return false;  // Reject unsigned
```

Then unsigned JARs will be rejected completely.

---

## Troubleshooting

### Build Fails: "JAR signature verification failed"

**Cause:** Signing didn't work correctly

**Fix:**
1. Check GitHub Secrets are set correctly
2. Verify `SIGNING_KEYSTORE_BASE64` is complete (no truncation)
3. Check `SIGNING_PASSWORD` is correct
4. Re-encode keystore: `base64 -w 0 keystore.jks > keystore.b64`

### jarsigner: command not found

**Cause:** Java JDK not installed (only JRE)

**Fix:** Install JDK 17+
```bash
# Linux (Ubuntu)
sudo apt install openjdk-17-jdk

# Mac
brew install openjdk@17

# Windows
# Download from: https://adoptium.net/
```

### Keystore was tampered with, or password was incorrect

**Cause:** Wrong password or corrupted keystore

**Fix:**
1. Verify password is correct
2. Re-generate keystore if corrupted
3. Update `SIGNING_PASSWORD` secret in GitHub

### Client Rejects Signed JAR: "signature is not from trusted signer"

**Cause:** Certificate DN doesn't match expected values

**Fix:**
Check certificate DN:
```bash
keytool -list -v -keystore modupdater-keystore.jks

# Look for: Owner: CN=modupdater, ...
```

Update `ModInstaller.java` line 157 to match your DN:
```java
if (dn.contains("CN=YOUR_NAME") ||
    dn.contains("CN=modupdater") ||
    dn.contains("O=YOUR_ORG")) {
```

---

## Best Practices

### Security

1. **Never commit keystore to git**
   - Add to `.gitignore`: `*.jks`, `*.keystore`
   - Store securely in password manager

2. **Use strong passwords**
   - Minimum 20 characters
   - Mix of letters, numbers, symbols
   - Use password generator

3. **Backup keystore**
   - Store in multiple secure locations
   - If lost, can't sign future releases with same key

4. **Rotate keys periodically**
   - Generate new keystore every 2-3 years
   - Transition period: sign with both keys
   - Update verification logic to accept both

### GitHub Actions

1. **Use environment-specific secrets**
   - Production: Strong keys, 10-year validity
   - Testing: Separate keys, shorter validity

2. **Monitor workflow runs**
   - Check for failed signature verifications
   - Review successful releases

3. **Branch protection**
   - Require PR reviews
   - Prevent force pushes to main
   - Tag protection rules

---

## Migration Guide

### Already Have Unsigned Releases

**Problem:** Old releases are unsigned, new releases will be signed

**Solution:** Backwards compatibility built-in!

Current behavior:
- ✅ Signed JARs from new releases → verified and installed
- ⚠️ Unsigned JARs from old releases → warned but allowed

Future migration:
```java
// In ModInstaller.java, change line 194 to enforce signatures:
return false;  // Reject unsigned JARs
```

But only after all users have updated past the last unsigned version.

### Changing Signing Key

**Scenario:** Lost keystore or rotating keys

**Steps:**
1. Generate new keystore
2. Update GitHub Secrets
3. Update `ModInstaller.java` to accept both old and new CN:
```java
if (dn.contains("CN=modupdater") ||       // New key
    dn.contains("CN=old-modupdater")) {   // Old key (temp)
```
4. Release several versions with new key
5. After everyone updates, remove old CN check

---

## FAQ

**Q: Do I need to re-sign every release?**
A: No, GitHub Actions signs automatically when you push a tag.

**Q: Can I use the same keystore for multiple mods?**
A: Yes, but different aliases recommended for tracking.

**Q: What if I lose the keystore?**
A: Generate a new one, update secrets, modify verification logic to accept both (temporarily).

**Q: Do users need to install anything?**
A: No, verification happens automatically in the updater.

**Q: Can I disable signing?**
A: Yes, just don't configure the secrets. Build still works, JARs are unsigned.

**Q: How do I know if my JAR is signed?**
A: Run `jarsigner -verify modupdater-1.0.0.jar`

---

## Summary

✅ **One-time setup:**
1. Generate keystore
2. Add GitHub Secrets
3. Push to confirm workflow works

✅ **Every release:**
1. Create tag: `git tag v1.0.0`
2. Push tag: `git push origin v1.0.0`
3. GitHub Actions handles the rest

✅ **Security benefits:**
- Verified authenticity
- Tamper detection
- Trust chain validation

**You're done!** All future releases will be automatically signed and verified.
