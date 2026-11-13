package tv.cleanhome.gtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Single source of truth for resolving and launching apps, screensaver,
 * TV input selector, system settings, and key shortcuts.
 *
 * Guarantees cross-platform compatibility across both target environments:
 * 1. TV 1: TCL Android 11 (Realtek RTD2851 platform)
 * 2. TV 2: TCL Android 14 (MediaTek MT5896 / G08 Google TV platform)
 */
public final class LaunchHelper {
    public static final String PKG_YOUTUBE = "com.google.android.youtube.tv";
    public static final String PKG_TV = "com.tcl.tv";

    public static final long DEBOUNCE_THRESHOLD_MS = 1000L;
    private static long lastInputLaunchTime = 0L;

    public static final String[] YOUTUBE_PACKAGES = {
            PKG_YOUTUBE,
            "com.liskovsoft.videomanager",
            "com.liskovsoft.smarttubetv.beta",
            "com.liskovsoft.smarttubetv"
    };

    /**
     * Inspectable specification for a target Intent candidate.
     */
    public static final class IntentTarget {
        public final String action;
        public final String packageName;
        public final String className;
        public final boolean isService;

        public static IntentTarget action(String action) {
            return new IntentTarget(action, null, null, false);
        }

        public static IntentTarget component(String packageName, String className) {
            return new IntentTarget(null, packageName, className, false);
        }

        public static IntentTarget service(String action, String packageName, String className) {
            return new IntentTarget(action, packageName, className, true);
        }

        public IntentTarget(String action, String packageName, String className, boolean isService) {
            this.action = action;
            this.packageName = packageName;
            this.className = className;
            this.isService = isService;
        }

        public Intent toIntent() {
            Intent intent = (action != null) ? new Intent(action) : new Intent();
            if (packageName != null && className != null) {
                intent.setClassName(packageName, className);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }
    }

    // Screensaver candidates (TV2 Google TV -> TV1 AOSP / Somnambulator -> fallback)
    public static final IntentTarget[] SCREENSAVER_TARGETS = {
            IntentTarget.action("com.google.android.pano.action.SLEEP"),
            IntentTarget.component("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamVoiceAction"),
            IntentTarget.action("com.google.android.tv.settings.ambient"),
            IntentTarget.component("com.android.tv.settings", "com.google.android.tv.settings.AmbientActivity"),
            IntentTarget.component("com.android.systemui", "com.android.systemui.Somnambulator"),
            IntentTarget.action(Settings.ACTION_DREAM_SETTINGS)
    };

    // Inputs overlay service candidates (TV2 player service -> TV1 sourcemenu service)
    public static final IntentTarget[] INPUTS_SERVICES = {
            IntentTarget.service("com.tcl.insrc.SHOW_WINDOW", "com.tcl.tv", "com.tcl.player.service.ShowWindowService"),
            IntentTarget.service("com.tcl.insrc.SHOW_WINDOW", "com.tcl.tv", "com.tcl.sourcemenu.insrc.ShowWindowService")
    };

    // Inputs activity candidates (TV2 suspension / Quick_Panel -> TV1 tvinput)
    public static final IntentTarget[] INPUTS_ACTIVITIES = {
            IntentTarget.action("com.android.tv.action.VIEW_INPUTS"),
            IntentTarget.action("com.tcl.suspension.VIEW_INPUTS"),
            IntentTarget.component("com.tcl.suspension", "com.tcl.suspension.ui.main.InputActivity"),
            IntentTarget.component("com.tcl.tvinput", "com.tcl.tvinput.MainActivity")
    };

    // Accounts candidates (TV2 Google TV AccountActivity -> TV1 / AOSP AccountSync)
    public static final IntentTarget[] ACCOUNTS_TARGETS = {
            IntentTarget.component("com.android.tv.settings", "com.google.android.tv.settings.AccountActivity"),
            IntentTarget.action("android.settings.ACCOUNT_SYNC_SETTINGS"),
            IntentTarget.component("com.android.tv.settings", "com.android.tv.settings.accounts.AccountSyncActivity"),
            IntentTarget.action("android.settings.SYNC_SETTINGS"),
            IntentTarget.action(Settings.ACTION_ADD_ACCOUNT),
            IntentTarget.action(Settings.ACTION_SETTINGS)
    };

    // Network candidates (TV2 NetworkActivity / OEM link -> TV1 TCL NetworkSettings -> AOSP)
    public static final IntentTarget[] NETWORK_TARGETS = {
            IntentTarget.component("com.android.tv.settings", "com.android.tv.settings.connectivity.NetworkActivity"),
            IntentTarget.action("android.settings.NETWORK_SETTINGS_TV_OEM_LINK"),
            IntentTarget.component("com.android.tv.settings", "com.android.tv.settings.oemlink.NetworkSettingsActivity"),
            IntentTarget.action("com.tcl.settings.action.NETWORKSETTINGS"),
            IntentTarget.action(Settings.ACTION_WIRELESS_SETTINGS),
            IntentTarget.action(Settings.ACTION_SETTINGS)
    };

    // Remotes & Bluetooth candidates (TV2 AccessoriesActivity -> TV1 Accessories -> AOSP)
    public static final IntentTarget[] REMOTES_TARGETS = {
            IntentTarget.action("com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES"),
            IntentTarget.component("com.android.tv.settings", "com.tcl.settings.accessories.AccessoriesActivity"),
            IntentTarget.component("com.android.tv.settings", "com.tcl.android.tv.settings.accessories.AccessoriesActivity"),
            IntentTarget.component("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"),
            IntentTarget.action("android.settings.BLUETOOTH_SETTINGS"),
            IntentTarget.action(Settings.ACTION_SETTINGS)
    };

    // Picture & Display candidates (TV2 TCL PictureActivity -> AOSP Display -> Settings)
    public static final IntentTarget[] PICTURE_TARGETS = {
            IntentTarget.component("com.android.tv.settings", "com.tcl.settings.PictureActivity"),
            IntentTarget.action(Settings.ACTION_DISPLAY_SETTINGS),
            IntentTarget.action(Settings.ACTION_SETTINGS)
    };

    // Accessibility candidates (TV2/1 OEM link -> AOSP Accessibility)
    public static final IntentTarget[] ACCESSIBILITY_TARGETS = {
            IntentTarget.action("android.settings.ACCESSIBILITY_TV_OEM_LINK"),
            IntentTarget.component("com.android.tv.settings", "com.android.tv.settings.oemlink.AccessibilitySettingsActivity"),
            IntentTarget.action(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            IntentTarget.action(Settings.ACTION_SETTINGS)
    };

    // App Management candidates (TV2 AllApps / AppsActivity -> AOSP Manage Apps)
    public static final IntentTarget[] APPS_TARGETS = {
            IntentTarget.component("com.android.tv.settings", "com.android.tv.settings.device.apps.AllAppsActivity"),
            IntentTarget.component("com.android.tv.settings", "com.android.tv.settings.device.apps.AppsActivity"),
            IntentTarget.action(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            IntentTarget.action(Settings.ACTION_SETTINGS)
    };

    // Main Settings fallback candidates
    public static final IntentTarget[] SETTINGS_TARGETS = {
            IntentTarget.action(Settings.ACTION_SETTINGS)
    };

    // Live TV component fallback candidates
    public static final IntentTarget[] LIVE_TV_TARGETS = {
            IntentTarget.component(PKG_TV, "com.tcl.player.TVActivity"),
            IntentTarget.component(PKG_TV, "com.tcl.tv.TVActivity")
    };

    private LaunchHelper() {}

    /**
     * Pure debounce checker for key events and UI launches.
     */
    public static boolean shouldDebounce(long currentTimeMs, long lastTimeMs, long thresholdMs) {
        return (currentTimeMs - lastTimeMs) < thresholdMs;
    }

    /**
     * Launches the first resolvable IntentTarget from the given array.
     */
    public static boolean launchFirst(Context context, IntentTarget[] targets) {
        if (context == null || targets == null) return false;
        for (IntentTarget target : targets) {
            if (target == null) continue;
            try {
                Intent intent = target.toIntent();
                context.startActivity(intent);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean startScreensaver(Context context) {
        return launchFirst(context, SCREENSAVER_TARGETS);
    }

    public static boolean openAccounts(Context context) {
        return launchFirst(context, ACCOUNTS_TARGETS);
    }

    public static boolean openNetworkSettings(Context context) {
        return launchFirst(context, NETWORK_TARGETS);
    }

    public static boolean openRemotesSettings(Context context) {
        return launchFirst(context, REMOTES_TARGETS);
    }

    public static boolean openPictureSettings(Context context) {
        return launchFirst(context, PICTURE_TARGETS);
    }

    public static boolean openAccessibilitySettings(Context context) {
        return launchFirst(context, ACCESSIBILITY_TARGETS);
    }

    public static boolean openAppsSettings(Context context) {
        return launchFirst(context, APPS_TARGETS);
    }

    public static boolean openSettings(Context context) {
        return launchFirst(context, SETTINGS_TARGETS);
    }

    /**
     * Opens the TV inputs selector overlay with debounce protection against remote bounces.
     */
    public static synchronized boolean openInputs(Context context) {
        if (context == null) return false;
        long now = SystemClock.uptimeMillis();
        if (shouldDebounce(now, lastInputLaunchTime, DEBOUNCE_THRESHOLD_MS)) {
            return true;
        }
        lastInputLaunchTime = now;

        // 1. Try native OEM overlay services
        for (IntentTarget target : INPUTS_SERVICES) {
            try {
                Intent serviceIntent = target.toIntent();
                ComponentName cn = context.startService(serviceIntent);
                if (cn != null) return true;
            } catch (Throwable ignored) {}
        }

        // 2. Try Activity fallbacks
        return launchFirst(context, INPUTS_ACTIVITIES);
    }

    /**
     * Launches Live TV using package launcher intent with component fallbacks.
     */
    public static boolean openLiveTv(Context context) {
        if (context == null) return false;
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(PKG_TV);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(PKG_TV);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return true;
            } catch (Throwable ignored) {}
        }
        return launchFirst(context, LIVE_TV_TARGETS);
    }

    /**
     * Attempts to launch YouTube or alternative leanback YouTube clients (e.g. SmartTube).
     * If disabled, tries enabling the official YouTube package before launching.
     */
    public static boolean openYouTube(Context context) {
        if (context == null) return false;
        PackageManager pm = context.getPackageManager();
        for (String pkg : YOUTUBE_PACKAGES) {
            if (launchPackage(context, pkg)) return true;
        }
        try {
            pm.setApplicationEnabledSetting(
                    PKG_YOUTUBE,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    0);
            if (launchPackage(context, PKG_YOUTUBE)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Resolves and launches the main intent for a package using leanback or standard launcher intent.
     */
    public static boolean launchPackage(Context context, String packageName) {
        if (context == null || packageName == null) return false;
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(packageName);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                context.startActivity(intent);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }
}
