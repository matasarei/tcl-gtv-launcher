package tv.cleanhome.gtv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Shared helper for resolving and launching apps and third-party clients.
 */
public final class LaunchHelper {
    public static final String PKG_YOUTUBE = "com.google.android.youtube.tv";

    public static final String[] YOUTUBE_PACKAGES = {
            PKG_YOUTUBE,
            "com.liskovsoft.videomanager",
            "com.liskovsoft.smarttubetv.beta",
            "com.liskovsoft.smarttubetv"
    };

    private LaunchHelper() {}

    /**
     * Attempts to launch YouTube or alternative leanback YouTube clients (e.g. SmartTube).
     * If disabled, tries enabling the official YouTube package before launching.
     *
     * @return true if an app was successfully launched.
     */
    public static boolean openYouTube(Context context) {
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
     *
     * @return true if the intent was resolved and started.
     */
    public static boolean launchPackage(Context context, String packageName) {
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
