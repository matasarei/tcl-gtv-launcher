# tcl-gtv-launcher — notes for agents

A lightweight, distraction-free Google TV-style leanback launcher (`tv.cleanhome.gtv`) designed specifically for TCL Android TV devices running Android 11 (API 30) through Android 14 (API 34).

## Commands

| What | Command |
|---|---|
| Build Release APK | `cd launcher && ./gradlew assembleRelease` |
| Test | `cd launcher && ./gradlew test` |
| Lint & Checks | `cd launcher && ./gradlew check` |
| Deploy (Sideload) | `adb push launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/gtvhome.apk && adb shell "pm install -r /data/local/tmp/gtvhome.apk && pm grant tv.cleanhome.gtv android.permission.READ_TV_LISTINGS; pm grant tv.cleanhome.gtv android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true; appops set tv.cleanhome.gtv AUTO_START allow; appops set tv.cleanhome.gtv ACCESS_RESTRICTED_SETTINGS allow; cur=\$(settings get secure enabled_accessibility_services); case \"\$cur\" in *tv.cleanhome.gtv.GlobalKeyService*) ;; null|\"\") settings put secure enabled_accessibility_services tv.cleanhome.gtv/tv.cleanhome.gtv.GlobalKeyService ;; *) settings put secure enabled_accessibility_services \"\$cur:tv.cleanhome.gtv/tv.cleanhome.gtv.GlobalKeyService\" ;; esac; settings put secure accessibility_enabled 1; rm /data/local/tmp/gtvhome.apk"` |
| Restart Launcher | `adb shell am force-stop tv.cleanhome.gtv && adb shell monkey -p tv.cleanhome.gtv -c android.intent.category.HOME 1` |

## Architecture & Code Conventions

- **Language & Runtime:** Java 17, Android minSdk 26 (Android 8.0 Oreo), targetSdk 34 (Android 14), Gradle 8.7 with AGP 8.5.2.
- **Zero Runtime Dependencies:** The launcher deliberately avoids external third-party runtime libraries. Keep it lightweight, fast-booting, and robust.
- **Key Interception & Navigation:**
  - `GlobalKeyService.java` is an `AccessibilityService` that intercepts remote controller hardware keys (Settings, Live TV/Guide, Profile, YouTube). Keymappings must remain compatible across both Android 11 and Android 14 TCL remotes.
  - Remote key mappings are verified in unit tests: `launcher/app/src/test/java/tv/cleanhome/gtv/KeyMappingUnitTest.java`.
  - All launcher navigation and view focuses should maintain a single source of truth in `MainActivity.java`.
- **Memory & Resource Constraints:**
  - Leanback launchers run continuously on memory-constrained Smart TV hardware (~1.5–2GB RAM).
  - Background wallpaper handling must use `Bitmap.Config.RGB_565` and appropriate `inSampleSize` downsampling to prevent OutOfMemory errors.
  - Avoid memory leaks, long-lived static view references, or unbounded image caching.
- **Submodules & External Trees:**
  - `cleanyourtv/` (setup tools, ADB debloat scripts) is a git submodule. Do not modify submodule contents unless explicitly instructed.
