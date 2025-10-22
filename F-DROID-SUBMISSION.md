# F-Droid Submission Checklist

This document outlines the F-Droid readiness status of AirheadWaves/CosmicCast.

## ✅ Completed Requirements

### 1. **All Dependencies Are FOSS** ✅
All dependencies have been reviewed and are Free and Open Source Software:

- **AndroidX libraries** (Apache 2.0): Core, Lifecycle, Activity, Compose BOM
- **Jetpack Compose** (Apache 2.0): UI, Material3, Navigation
- **Kotlin & Kotlinx** (Apache 2.0): Kotlin stdlib, kotlinx-serialization-json
- **Material Icons Extended** (Apache 2.0)
- **Test libraries** (Apache 2.0): JUnit, Espresso

**Repositories used:**
- Google Maven (for AndroidX/Compose - all Apache 2.0 licensed)
- Maven Central (for Kotlin libraries)

**No proprietary dependencies detected.**

### 2. **License** ✅
- **License**: MIT License
- **Location**: `/LICENSE` file in repository root
- **README**: Includes license information

The MIT license is GPL-compatible and acceptable for F-Droid.

### 3. **No Tracking/Analytics/Telemetry** ✅
Verified that the app contains:
- ❌ No Firebase
- ❌ No Google Analytics
- ❌ No Crashlytics
- ❌ No advertising frameworks
- ❌ No tracking libraries
- ❌ No proprietary crash reporting
- ❌ No network requests except user-initiated audio streaming

**Privacy**: The app only makes network connections for its core functionality (streaming audio to user-configured servers). No data is sent to third parties.

### 4. **Build Configuration** ✅
- **Reproducible builds**: Standard Gradle build system
- **Version management**: Uses `versionCode` and `versionName` in `build.gradle.kts`
- **No build flavors requiring Play Services**
- **No obfuscation**: `isMinifyEnabled = false` in release builds
- **Target SDK**: 36 (current)
- **Min SDK**: 29 (Android 10+)

### 5. **Fastlane Metadata** ✅
Created metadata structure at `fastlane/metadata/android/en-US/`:
- `title.txt` - App name
- `short_description.txt` - 30-50 character summary
- `full_description.txt` - Detailed description with features
- `images/phoneScreenshots/` - Directory ready for screenshots

### 6. **Source Code Availability** ✅
- Clean, readable source code
- No binary blobs
- No proprietary components
- All resources are original or properly licensed

### 7. **Permissions** ✅
All permissions are justified and necessary:
- `RECORD_AUDIO` - Required for audio capture
- `FOREGROUND_SERVICE` - For background audio streaming
- `INTERNET` - For TCP audio streaming to servers
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` - Android 14+ requirement
- `POST_NOTIFICATIONS` - For foreground service notification

## 📋 Submission Steps

### 1. Prepare Screenshots
Add at least 2-8 screenshots to:
```
fastlane/metadata/android/en-US/images/phoneScreenshots/
```

Screenshot guidelines:
- PNG or JPG format
- Filename format: `01.png`, `02.png`, etc.
- Show key features: main screen, settings, profile management, theme options
- No device frames needed (F-Droid generates them)

### 2. Optional: Add Feature Graphic
Add to: `fastlane/metadata/android/en-US/images/featureGraphic.png`
- Dimensions: 1024x500 px
- Showcases the app prominently

### 3. Optional: Add Icon
Add to: `fastlane/metadata/android/en-US/images/icon.png`
- 512x512 px
- High-resolution version of launcher icon

### 4. Create F-Droid Metadata File
Fork the [fdroiddata repository](https://gitlab.com/fdroid/fdroiddata) and create:
```
metadata/space.ring0.airheadwaves.yml
```

Example metadata file:
```yaml
Categories:
  - Multimedia
  - Internet

License: MIT

AuthorName: Stephen Brady
AuthorEmail: stephen.mk.brady@email.com
AuthorWebSite: https://gitlab.com/stephen.mk.brady-group/AirheadWaves

SourceCode: https://gitlab.com/stephen.mk.brady-group/AirheadWaves
IssueTracker: https://gitlab.com/stephen.mk.brady-group/AirheadWaves/-/issues
Changelog: https://gitlab.com/stephen.mk.brady-group/AirheadWaves/-/releases

AutoName: AirheadWaves

RepoType: git
Repo: https://gitlab.com/stephen.mk.brady-group/AirheadWaves

Builds:
  - versionName: '1.0'
    versionCode: 1
    commit: v1.0
    subdir: app
    gradle:
      - yes
```

**About the `commit` field:**
- This should be a git tag (e.g., `v1.0`) or a commit hash
- F-Droid will checkout this exact commit to build your app
- You need to create and push this tag before submitting

### 5. Submit Merge Request
1. Fork https://gitlab.com/fdroid/fdroiddata
2. Add your metadata file to the `metadata/` directory
3. Test build locally with `fdroid build -l space.ring0.airheadwave !s`
4. Create merge request with clear description
5. Respond to any feedback from F-Droid reviewers

### 6. Post-Submission
- F-Droid will review and build your app
- Review time: typically 1-2 weeks
- Updates: Push tags to your repo, F-Droid will automatically detect and build them

## 🔍 Anti-Features: None

F-Droid labels certain features as "Anti-Features". AirheadWaves has:
- ✅ No ads
- ✅ No tracking
- ✅ No non-free dependencies
- ✅ No non-free add-ons
- ✅ No upstream non-free components
- ✅ No known security vulnerabilities
- ✅ No non-free network services (streams only to user's own servers)

## 📝 Additional Notes

### Update Strategy
- Tag releases in git with semantic versioning (v1.0, v1.1, etc.)
- Update `versionCode` and `versionName` in `build.gradle.kts`
- F-Droid will automatically detect and build new versions
- Metadata in fastlane folder will be automatically updated

### Build Reproducibility
The app uses:
- Standard Gradle build
- No proprietary build tools
- No binary dependencies
- Deterministic build process

This ensures F-Droid can reproducibly build the app from source.

## 📚 Resources

- [F-Droid Submission Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [F-Droid Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/)
- [Fastlane Metadata Format](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)

## ✨ Summary

**AirheadWaves is ready for F-Droid submission!** All technical requirements are met:
- ✅ 100% FOSS dependencies
- ✅ No tracking or analytics
- ✅ Proper licensing (MIT)
- ✅ Fastlane metadata prepared
- ✅ Clean, auditable source code
- ✅ No anti-features

**Remaining steps:**
1. Add screenshots to `fastlane/metadata/android/en-US/images/phoneScreenshots/`
2. Create metadata YAML file in fdroiddata repository
3. Submit merge request to F-Droid
