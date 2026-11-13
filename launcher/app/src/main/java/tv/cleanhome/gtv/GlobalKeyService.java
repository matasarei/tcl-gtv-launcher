package tv.cleanhome.gtv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class GlobalKeyService extends AccessibilityService {
    private static final String TAG = "GlobalKeyService";

    // Known OEM and Android keycodes
    private static final int KEYCODE_TCL_SET = 4077;
    private static final int KEYCODE_TCL_QUICK_MENU = 4020;
    private static final int KEYCODE_TCL_PROFILE = 4016;
    private static final int KEYCODE_PROFILE_SWITCH = 288;
    private static final int KEYCODE_PROFILE_SWITCH_LEGACY = 303;
    private static final int KEYCODE_TCL_YOUTUBE = 4065;
    private static final int KEYCODE_TCL_NETFLIX = 4062;
    private static final int KEYCODE_TCL_PRIMEVIDEO = 4099;
    private static final int KEYCODE_TCL_BROWSEHERE = 4159;
    private static final int KEYCODE_TCL_LIST = 4090;
    private static final int KEYCODE_TCL_RECORD_LIST = 4126;
    private static final int KEYCODE_BUTTON_3 = 190;
    private static final int KEYCODE_BOOKMARK = 174;

    private static final String PKG_YOUTUBE = LaunchHelper.PKG_YOUTUBE;
    private static final String PKG_NETFLIX = "com.netflix.ninja";
    private static final String PKG_PRIMEVIDEO = "com.amazon.amazonvideo.livingroom";
    private static final String PKG_TV = "com.tcl.tv";
    private static final String PKG_TV_INPUT = "com.tcl.tvinput";

    public static volatile long lastInputOpenedTime = 0;

    private volatile String foregroundPackage = null;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                foregroundPackage = pkg.toString();
            }
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        Log.i(TAG, "GlobalKeyService connected: flags=" + info.flags);
    }

    static boolean isHandledKey(int code, boolean isTvForeground) {
        if (isListKey(code) && isTvForeground) {
            return false;
        }
        return isSettingsKey(code) ||
                isProfileKey(code) ||
                isTvKey(code) ||
                isAppsKey(code) ||
                isInputKey(code) ||
                isListKey(code) ||
                isYouTubeKey(code) ||
                code == KEYCODE_TCL_NETFLIX ||
                code == KEYCODE_TCL_PRIMEVIDEO ||
                code == KEYCODE_TCL_BROWSEHERE ||
                code == KeyEvent.KEYCODE_EXPLORER;
    }

    private boolean isHandledKey(int code) {
        return isHandledKey(code, isTvForeground());
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int action = event.getAction();

        if (!isHandledKey(code)) {
            return super.onKeyEvent(event);
        }

        // Always consume ACTION_UP and repeated ACTION_DOWN for handled keys so they don't leak
        // to newly-focused overlay dialogs (such as TCL's Input QuickPanelDialog which dismisses on ACTION_UP)
        if (action != KeyEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getRepeatCount() > 0) {
            return true;
        }

        if (isSettingsKey(code)) {
            Log.i(TAG, "Intercepted settings key " + code + ", opening QuickSettingsActivity");
            openQuickSettings();
            return true;
        }

        if (isProfileKey(code)) {
            Log.i(TAG, "Intercepted profile key " + code + ", opening settings/accounts");
            openAccounts();
            return true;
        }

        if (isTvKey(code)) {
            Log.i(TAG, "Intercepted TV key " + code + ", opening Live TV app");
            openLiveTv();
            return true;
        }

        if (isAppsKey(code)) {
            Log.i(TAG, "Intercepted apps key " + code + ", opening Apps tab");
            openApps();
            return true;
        }

        if (isInputKey(code)) {
            Log.i(TAG, "Intercepted input key " + code + ", opening TV inputs");
            openTvInputs();
            return true;
        }

        if (isListKey(code)) {
            Log.i(TAG, "Intercepted list key " + code + " outside TV, opening Live TV");
            openLiveTv();
            return true;
        }

        if (isYouTubeKey(code)) {
            Log.i(TAG, "Intercepted YouTube key " + code);
            openYouTube();
            return true;
        }

        if (code == KEYCODE_TCL_NETFLIX) {
            Log.i(TAG, "Intercepted Netflix key " + code);
            if (launchPackage(PKG_NETFLIX)) return true;
        }

        if (code == KEYCODE_TCL_PRIMEVIDEO) {
            Log.i(TAG, "Intercepted Prime Video key " + code);
            if (launchPackage(PKG_PRIMEVIDEO)) return true;
        }

        if (code == KEYCODE_TCL_BROWSEHERE || code == KeyEvent.KEYCODE_EXPLORER) {
            Log.i(TAG, "Intercepted browser key " + code);
            openBrowser();
            return true;
        }

        return true;
    }

    static boolean isSettingsKey(int code) {
        return code == KEYCODE_TCL_SET ||
                code == 176 ||
                code == KeyEvent.KEYCODE_SETTINGS ||
                code == KeyEvent.KEYCODE_MENU ||
                code == 83 ||
                code == KeyEvent.KEYCODE_NOTIFICATION;
    }

    static boolean isProfileKey(int code) {
        return code == KEYCODE_TCL_PROFILE ||
                code == KEYCODE_PROFILE_SWITCH ||
                code == KEYCODE_PROFILE_SWITCH_LEGACY;
    }

    static boolean isTvKey(int code) {
        return code == 4001 ||
                code == 170 ||
                code == KeyEvent.KEYCODE_TV ||
                code == 172 ||
                code == KeyEvent.KEYCODE_GUIDE ||
                code == 4121 || code == 4164 ||
                (code >= 4503 && code <= 4509);
    }

    static boolean isAppsKey(int code) {
        return code == KEYCODE_BOOKMARK ||
                code == KeyEvent.KEYCODE_BOOKMARK ||
                code == 284 ||
                code == KeyEvent.KEYCODE_ALL_APPS;
    }

    static boolean isInputKey(int code) {
        return code == 178 || code == KeyEvent.KEYCODE_TV_INPUT;
    }

    static boolean isYouTubeKey(int code) {
        return code == KEYCODE_TCL_YOUTUBE ||
                code == KEYCODE_BUTTON_3 ||
                code == KeyEvent.KEYCODE_BUTTON_3;
    }

    static boolean isListKey(int code) {
        return code == KEYCODE_TCL_LIST ||
                code == KEYCODE_TCL_RECORD_LIST;
    }

    private void openQuickSettings() {
        Intent intent = new Intent(this, QuickSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void openAccounts() {
        Intent intent = new Intent(this, QuickSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(QuickSettingsActivity.EXTRA_FOCUS_TILE, "Accounts");
        try {
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void openApps() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_APPS);
        try {
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void openTvInputs() {
        long now = SystemClock.uptimeMillis();
        if (now - lastInputOpenedTime < 1000) {
            Log.i(TAG, "Debouncing TV inputs launch");
            return;
        }
        lastInputOpenedTime = now;

        // 1. Native TCL input sources overlay service (proven to show input selector)
        try {
            Intent serviceIntent = new Intent("com.tcl.insrc.SHOW_WINDOW");
            serviceIntent.setClassName("com.tcl.tv", "com.tcl.sourcemenu.insrc.ShowWindowService");
            ComponentName cn = startService(serviceIntent);
            if (cn != null) return;
        } catch (Exception ignored) {}

        // 2. Standard Android TV input picker
        Intent[] intents = new Intent[]{
                new Intent("com.android.tv.action.VIEW_INPUTS"),
                new Intent("com.tcl.suspension.VIEW_INPUTS"),
                new Intent().setClassName("com.tcl.suspension", "com.tcl.suspension.ui.main.InputActivity"),
                new Intent().setClassName("com.tcl.tvinput", "com.tcl.tvinput.MainActivity")
        };
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }
    }

    private void openYouTube() {
        if (!LaunchHelper.openYouTube(this)) {
            Toast.makeText(this, PKG_YOUTUBE + " is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isTvForeground() {
        return PKG_TV.equals(foregroundPackage) || PKG_TV_INPUT.equals(foregroundPackage);
    }

    private void openLiveTv() {
        if (!launchPackage(PKG_TV)) {
            String[] tvActivities = {
                    "com.tcl.player.TVActivity",
                    "com.tcl.tv.TVActivity"
            };
            for (String act : tvActivities) {
                try {
                    Intent intent = new Intent().setClassName(PKG_TV, act);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return;
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean launchPackage(String packageName) {
        if (LaunchHelper.launchPackage(this, packageName)) {
            return true;
        }
        Toast.makeText(this, packageName + " is not installed", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void openBrowser() {
        String[] browsers = new String[]{
                "com.phlox.tvwebbrowser",
                "com.tcl.browser",
                "org.mozilla.tv.firefox",
                "com.google.android.apps.chrome",
                "com.android.chrome"
        };
        for (String pkg : browsers) {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                return;
            }
        }
        try {
            Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.phlox.tvwebbrowser"));
            store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(store);
            Toast.makeText(this, "Recommended TV browser: TV Bro", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "No browser installed", Toast.LENGTH_SHORT).show();
        }
    }
}
