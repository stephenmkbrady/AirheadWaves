# Claude Code Instructions: GitLab CI/CD for Android

## Overview
This document provides Claude Code with instructions to automatically configure an existing Android project for GitLab CI/CD with signed APK builds.

---

## What Claude Code Should Do

Claude Code should perform these tasks when given this document and an Android project:

1. **Analyze the existing Android project structure**
2. **Modify `app/build.gradle`** to add signing configuration
3. **Create `.gitlab-ci.yml`** in project root
4. **Generate keystore** (if user provides keystore details)
5. **Convert keystore to base64** (if keystore exists)
6. **Provide next steps** for GitLab variable configuration

---

## Prerequisites Check

Before starting, Claude Code should verify:

```
- [ ] File exists: `settings.gradle` or `settings.gradle.kts`
- [ ] File exists: `app/build.gradle` or `app/build.gradle.kts`
- [ ] File exists: `gradlew`
- [ ] Directory exists: `app/src/`
```

If any of these are missing, inform the user this is not a valid Android project.

---

## Task 1: Modify app/build.gradle

### Location
`app/build.gradle` (or `app/build.gradle.kts` for Kotlin DSL)

### What to Add

Insert the `signingConfigs` block and update `buildTypes` in the `android { }` block:

```gradle
android {
    // ... existing config (compileSdk, defaultConfig, etc.) ...

    signingConfigs {
        release {
            // CI/CD will inject these properties at build time
            if (System.getProperty("android.injected.signing.store.file")) {
                storeFile file(System.getProperty("android.injected.signing.store.file"))
                storePassword System.getProperty("android.injected.signing.store.password")
                keyAlias System.getProperty("android.injected.signing.key.alias")
                keyPassword System.getProperty("android.injected.signing.key.password")
            }
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        debug {
            // Debug builds don't need signing
        }
    }

    // ... rest of android config ...
}
```

### Implementation Notes

- **If `signingConfigs` block already exists:** Merge the new `release` config with existing configs
- **If `buildTypes.release` already exists:** Add `signingConfig signingConfigs.release` to it
- **Preserve existing configuration:** Don't remove any existing settings
- **Handle both Groovy and Kotlin DSL:** Adapt syntax accordingly

---

## Task 2: Create .gitlab-ci.yml

### Location
`.gitlab-ci.yml` (in project root directory, same level as `settings.gradle`)

### Complete File Contents

```yaml
# Docker image with Android SDK pre-installed
image: mingc/android-build-box:latest

# Pipeline stages
stages:
  - test
  - build

# Cache Gradle dependencies to speed up builds
cache:
  key: ${CI_PROJECT_ID}
  paths:
    - .gradle/wrapper
    - .gradle/caches

# Gradle configuration
variables:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false"

# Runs before every job - decodes and prepares keystore
before_script:
  - echo $KEYSTORE_FILE | base64 -d > my-release-key.keystore
  - export KEYSTORE_PATH=$(pwd)/my-release-key.keystore

# Job: Run unit tests
test:
  stage: test
  script:
    - chmod +x ./gradlew
    - ./gradlew test
  artifacts:
    when: always
    reports:
      junit: app/build/test-results/test*/TEST-*.xml
  allow_failure: true

# Job: Build debug APK (runs on all branches)
build_debug:
  stage: build
  script:
    - chmod +x ./gradlew
    - ./gradlew assembleDebug
  artifacts:
    paths:
      - app/build/outputs/apk/debug/*.apk
    expire_in: 7 days

# Job: Build signed release APK (runs only on main/master branch)
build_release:
  stage: build
  script:
    - chmod +x ./gradlew
    - ./gradlew assembleRelease
      -Pandroid.injected.signing.store.file=$KEYSTORE_PATH
      -Pandroid.injected.signing.store.password=$KEYSTORE_PASSWORD
      -Pandroid.injected.signing.key.alias=$KEY_ALIAS
      -Pandroid.injected.signing.key.password=$KEY_PASSWORD
  artifacts:
    paths:
      - app/build/outputs/apk/release/*.apk
    expire_in: 30 days
  only:
    - main
    - master
    - tags
```

### Implementation Notes

- Create this file exactly as shown
- Do not modify the structure
- If file already exists, ask user before overwriting

---

## Task 3: Keystore Handling

### If User Has Existing Keystore

Ask user for:
1. Path to keystore file
2. Key alias
3. Keystore password (optional - for base64 conversion)
4. Key password (optional)

Then convert to base64:

```bash
# Mac/Linux
base64 -i [keystore-file] | tr -d '\n' > keystore_base64.txt

# Windows (Git Bash)
base64 -w 0 [keystore-file] > keystore_base64.txt
```

### If User Needs New Keystore

Ask user for:
1. Keystore filename (default: `my-release-key.keystore`)
2. Key alias (default: `my-key-alias`)
3. Keystore password
4. Organization details (optional - can use defaults)

Then generate:

```bash
keytool -genkey -v -keystore [filename] \
  -alias [alias] \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass [password] \
  -keypass [password] \
  -dname "CN=Android, OU=Mobile, O=Company, L=City, ST=State, C=US"
```

Then convert to base64 as above.

### Output

Save the base64 string to `keystore_base64.txt` and inform user:
- This file contains the base64-encoded keystore
- Keep it secure (add to .gitignore if not already)
- Will be needed for GitLab variable configuration

---

## Task 4: Update .gitignore

Add these entries to `.gitignore` if not already present:

```
# Keystore files
*.keystore
*.jks
keystore_base64.txt

# CI/CD sensitive files
local.properties
```

---

## Task 5: Summary Report

After completing all tasks, Claude Code should provide this summary:

```
✅ GitLab CI/CD Configuration Complete!

Files Modified:
- app/build.gradle (added signing configuration)
- .gitlab-ci.yml (created)
- .gitignore (updated)
- keystore_base64.txt (created) [if keystore was processed]

Next Steps (MANUAL - User Must Do):

1. CONFIGURE GITLAB VARIABLES
   Go to: GitLab Project → Settings → CI/CD → Variables
   
   Add these 4 variables:
   
   a) KEYSTORE_FILE
      - Value: [paste contents of keystore_base64.txt]
      - Protect: ✅ Yes
      - Mask: ✅ Yes
   
   b) KEYSTORE_PASSWORD
      - Value: [your keystore password]
      - Protect: ✅ Yes
      - Mask: ✅ Yes
   
   c) KEY_ALIAS
      - Value: [your key alias, e.g., "my-key-alias"]
      - Protect: ✅ Yes
      - Mask: ❌ No
   
   d) KEY_PASSWORD
      - Value: [your key password]
      - Protect: ✅ Yes
      - Mask: ✅ Yes

2. COMMIT AND PUSH
   git add .gitlab-ci.yml app/build.gradle .gitignore
   git commit -m "Add GitLab CI/CD configuration"
   git push origin main

3. VERIFY PIPELINE
   - Go to GitLab → CI/CD → Pipelines
   - Watch the pipeline run
   - Download APK from job artifacts when complete

Keystore Information:
- Keystore file: [filename]
- Key alias: [alias]
- Base64 file: keystore_base64.txt

⚠️  SECURITY REMINDERS:
- Never commit keystore files to the repository
- Keep keystore backup in secure location
- Keystore passwords should be strong and unique
- Share keystore details only through secure channels
```

---

## Error Handling

### If app/build.gradle Not Found

```
❌ Error: Cannot find app/build.gradle

This doesn't appear to be a standard Android project structure.
Please ensure you're running this from the root of an Android project.

Expected structure:
your-project/
├── app/
│   └── build.gradle  ← This file is missing
├── settings.gradle
└── gradlew
```

### If gradlew Not Found

```
⚠️  Warning: gradlew not found

The gradlew wrapper script is missing. This is needed for CI/CD.

To fix:
1. Generate wrapper: gradle wrapper
2. Ensure files are executable: chmod +x gradlew gradleww.bat
3. Commit wrapper files: git add gradlew gradlew.bat gradle/
```

### If Keystore Generation Fails

```
❌ Error: Keystore generation failed

Possible causes:
- keytool not in PATH (requires Java JDK)
- Invalid parameters provided
- Permission issues

To fix:
1. Ensure Java JDK is installed
2. Verify keytool is accessible: keytool -version
3. Check file write permissions
```

---

## File Structure Reference

Claude Code should recognize this structure:

```
android-project/
├── .gitlab-ci.yml          ← CREATE THIS
├── .gitignore              ← UPDATE THIS
├── settings.gradle         ← MUST EXIST
├── build.gradle            ← Project level (don't modify)
├── gradlew                 ← MUST EXIST
├── gradlew.bat
├── gradle/
├── app/
│   ├── build.gradle        ← MODIFY THIS
│   ├── proguard-rules.pro
│   └── src/
├── my-release-key.keystore ← GENERATE OR USE EXISTING
└── keystore_base64.txt     ← CREATE THIS
```

---

## Testing Checklist

After making changes, Claude Code should verify:

```bash
# Check files were created/modified
ls -la .gitlab-ci.yml
ls -la app/build.gradle

# Verify gradlew is executable
test -x gradlew && echo "✅ gradlew is executable" || echo "❌ gradlew not executable"

# Check .gitignore contains keystore exclusions
grep -q "*.keystore" .gitignore && echo "✅ .gitignore updated" || echo "⚠️  .gitignore needs update"

# Verify keystore exists (if generated)
test -f my-release-key.keystore && echo "✅ Keystore file exists" || echo "ℹ️  No keystore file (may be using existing)"

# Check base64 file was created
test -f keystore_base64.txt && echo "✅ Base64 file created" || echo "ℹ️  No base64 file created"
```

---

## Adaptation Notes

### For Kotlin DSL (build.gradle.kts)

If the project uses `build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            System.getProperty("android.injected.signing.store.file")?.let {
                storeFile = file(it)
                storePassword = System.getProperty("android.injected.signing.store.password")
                keyAlias = System.getProperty("android.injected.signing.key.alias")
                keyPassword = System.getProperty("android.injected.signing.key.password")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

### For Multi-Module Projects

If the project has multiple app modules:
- Ask user which module to configure
- Apply changes to the selected module's build.gradle
- Update .gitlab-ci.yml to build the correct module

### For Product Flavors

If build.gradle contains product flavors:
- Preserve all flavor configurations
- The signing config will apply to all release variants automatically

---

## Important Reminders for Claude Code

1. **Always preserve existing configuration** - Don't delete or overwrite user's existing settings
2. **Ask before overwriting** - If .gitlab-ci.yml exists, confirm with user
3. **Validate before creating** - Check that this is actually an Android project
4. **Provide clear next steps** - User must manually configure GitLab variables
5. **Security first** - Remind user never to commit keystores or passwords
6. **Test locally** - Suggest user test build locally before pushing

---

## Example Claude Code Workflow

```
1. User provides this document and Android project
   ↓
2. Claude Code reads document and analyzes project
   ↓
3. Claude Code checks prerequisites (settings.gradle, app/build.gradle, etc.)
   ↓
4. Claude Code asks user about keystore (have one? or create new?)
   ↓
5. Claude Code modifies app/build.gradle
   ↓
6. Claude Code creates .gitlab-ci.yml
   ↓
7. Claude Code handles keystore (generate or convert to base64)
   ↓
8. Claude Code updates .gitignore
   ↓
9. Claude Code provides summary with next steps
   ↓
10. User configures GitLab variables manually
    ↓
11. User commits and pushes
    ↓
12. GitLab CI/CD pipeline runs automatically
```

---

**END OF INSTRUCTIONS**