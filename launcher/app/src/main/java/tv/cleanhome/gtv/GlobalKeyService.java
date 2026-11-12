package tv.cleanhome.gtv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
    private static final int KEYCODE_TCL_YOUTUBE = 4065;
    private static final int KEYCODE_TCL_NETFLIX = 4062;
    private static final int KEYCODE_TCL_PRIMEVIDEO = 4099;
    private static final int KEYCODE_TCL_BROWSEHERE = 4159;
    private static final int KEYCODE_TCL_LIST = 4090;
    private static final int KEYCODE_TCL_RECORD_LIST = 4126;

    private static final String PKG_YOUTUBE = "com.google.android.youtube.tv";
    private static final String PKG_NETFLIX = "com.netflix.ninja";
    private static final String PKG_PRIMEVIDEO = "com.amazon.amazonvideo.livingroom";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        Log.i(TAG, "GlobalKeyService connected: flags=" + info.flags);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int action = event.getAction();
        Log.i(TAG, "onKeyEvent: code=" + code + ", action=" + action);

        if (action != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return super.onKeyEvent(event);
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
            Log.i(TAG, "Intercepted TV key " + code + ", opening TV inputs");
            openTvInputs();
            return true;
        }

        if (isListKey(code)) {
            Log.i(TAG, "Intercepted list key " + code + ", opening app list");
            openList();
            return true;
        }

        if (code == KEYCODE_TCL_YOUTUBE) {
            Log.i(TAG, "Intercepted YouTube key " + code);
            if (launchPackage(PKG_YOUTUBE)) return true;
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

        return super.onKeyEvent(event);
    }

    private boolean isSettingsKey(int code) {
        return code == KEYCODE_TCL_SET ||
                code == 176 ||
                code == KeyEvent.KEYCODE_SETTINGS ||
                code == KeyEvent.KEYCODE_MENU;
    }

    private boolean isProfileKey(int code) {
        return code == KEYCODE_TCL_PROFILE ||
                code == 288 ||
                code == KeyEvent.KEYCODE_PROFILE_SWITCH;
    }

    private boolean isTvKey(int code) {
        return code == 4001 ||
                code == 170 ||
                code == 172 ||
                code == 178 ||
                code == KeyEvent.KEYCODE_TV ||
                code == KeyEvent.KEYCODE_GUIDE ||
                code == KeyEvent.KEYCODE_TV_INPUT ||
                code == 4121 || code == 4164 ||
                (code >= 4503 && code <= 4509);
    }

    private boolean isListKey(int code) {
        return code == KEYCODE_TCL_LIST || code == KEYCODE_TCL_RECORD_LIST;
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

    private void openTvInputs() {
        // 1. Native TCL input sources overlay service (proven to show input selector)
        try {
            Intent serviceIntent = new Intent("com.tcl.insrc.SHOW_WINDOW");
            serviceIntent.setClassName("com.tcl.tv", "com.tcl.sourcemenu.insrc.ShowWindowService");
            serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startService(serviceIntent);
            return;
        } catch (Exception ignored) {}

        // 2. Standard Android TV input picker
        Intent[] intents = new Intent[]{
                new Intent("com.android.tv.action.VIEW_INPUTS"),
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

    private void openList() {
        Intent[] intents = new Intent[]{
                new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.apps.AllAppsActivity"),
                new Intent(this, QuickSettingsActivity.class)
        };
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }
    }

    private boolean launchPackage(String packageName) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(packageName);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
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
