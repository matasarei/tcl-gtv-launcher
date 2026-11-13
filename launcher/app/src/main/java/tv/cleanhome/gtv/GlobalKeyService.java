package tv.cleanhome.gtv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class GlobalKeyService extends AccessibilityService {
    private static final String TAG = "GlobalKeyService";

    // Known OEM and Android keycodes across TV1 (RTD2851) and TV2 (MT5896 / G08)
    public static final int KEYCODE_TCL_SET = 4077;
    public static final int KEYCODE_TCL_QUICK_MENU = 4020;
    public static final int KEYCODE_TCL_PROFILE = 4016;
    public static final int KEYCODE_PROFILE_SWITCH = 288;
    public static final int KEYCODE_PROFILE_SWITCH_LEGACY = 303;
    public static final int KEYCODE_TCL_YOUTUBE = 4065;
    public static final int KEYCODE_TCL_NETFLIX = 4062;
    public static final int KEYCODE_TCL_PRIMEVIDEO = 4099;
    public static final int KEYCODE_TCL_BROWSEHERE = 4159;
    public static final int KEYCODE_TCL_DISNEY = 4141;
    public static final int KEYCODE_TCL_APPLETV = 4160;
    public static final int KEYCODE_TCL_LIST = 4090;
    public static final int KEYCODE_TCL_RECORD_LIST = 4126;
    public static final int KEYCODE_TCL_ALL_APP = 4082;
    public static final int KEYCODE_TCL_EXIT = 4095;
    public static final int KEYCODE_TCL_GUARD = 4140;
    public static final int KEYCODE_TCL_TVPLUS = 4158;
    public static final int KEYCODE_BUTTON_3 = 190;
    public static final int KEYCODE_BOOKMARK = 174;

    public static volatile boolean isConnected = false;
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
        isConnected = true;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        Log.i(TAG, "GlobalKeyService connected: flags=" + info.flags);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        isConnected = false;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        isConnected = false;
        super.onDestroy();
    }

    public static boolean isHandledKey(int code, boolean isTvForeground) {
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
                isNetflixKey(code) ||
                isPrimeVideoKey(code) ||
                isBrowserKey(code) ||
                isDisneyKey(code);
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
            Log.i(TAG, "Intercepted input key " + code + ", TV system handles QuickPanel inputs natively");
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

        if (isNetflixKey(code)) {
            Log.i(TAG, "Intercepted Netflix key " + code);
            openNetflix();
            return true;
        }

        if (isPrimeVideoKey(code)) {
            Log.i(TAG, "Intercepted Prime Video key " + code);
            openPrimeVideo();
            return true;
        }

        if (isBrowserKey(code)) {
            Log.i(TAG, "Intercepted browser key " + code);
            openBrowser();
            return true;
        }

        if (isDisneyKey(code)) {
            Log.i(TAG, "Intercepted Disney key " + code);
            LaunchHelper.launchPackage(this, LaunchHelper.PKG_DISNEY);
            return true;
        }

        return true;
    }

    public static boolean isSettingsKey(int code) {
        return code == KEYCODE_TCL_SET ||
                code == 176 ||
                code == KeyEvent.KEYCODE_SETTINGS ||
                code == KeyEvent.KEYCODE_MENU ||
                code == 83 ||
                code == KeyEvent.KEYCODE_NOTIFICATION;
    }

    public static boolean isProfileKey(int code) {
        return code == KEYCODE_TCL_PROFILE ||
                code == KEYCODE_PROFILE_SWITCH ||
                code == KEYCODE_PROFILE_SWITCH_LEGACY;
    }

    public static boolean isTvKey(int code) {
        return code == 4001 ||
                code == 170 ||
                code == KeyEvent.KEYCODE_TV ||
                code == 172 ||
                code == KeyEvent.KEYCODE_GUIDE ||
                code == KEYCODE_TCL_QUICK_MENU ||
                code == 4135 ||
                code == 4121 ||
                code == 4164 ||
                code == 213 ||
                code == 230 ||
                code == 232 ||
                code == 236 ||
                (code >= 4503 && code <= 4509);
    }

    public static boolean isAppsKey(int code) {
        return code == KEYCODE_BOOKMARK ||
                code == KeyEvent.KEYCODE_BOOKMARK ||
                code == 284 ||
                code == KeyEvent.KEYCODE_ALL_APPS ||
                code == KEYCODE_TCL_ALL_APP ||
                code == KEYCODE_TCL_EXIT ||
                code == KEYCODE_TCL_GUARD ||
                code == KEYCODE_TCL_TVPLUS;
    }

    public static boolean isInputKey(int code) {
        return code == 178 || code == KeyEvent.KEYCODE_TV_INPUT;
    }

    public static boolean isYouTubeKey(int code) {
        return code == KEYCODE_TCL_YOUTUBE ||
                code == KEYCODE_BUTTON_3 ||
                code == KeyEvent.KEYCODE_BUTTON_3;
    }

    public static boolean isNetflixKey(int code) {
        return code == KEYCODE_TCL_NETFLIX ||
                code == 199 ||
                code == 200 ||
                code == 191 ||
                code == KeyEvent.KEYCODE_BUTTON_12;
    }

    public static boolean isPrimeVideoKey(int code) {
        return code == KEYCODE_TCL_PRIMEVIDEO ||
                code == 189 ||
                code == 193 ||
                code == KeyEvent.KEYCODE_BUTTON_2;
    }

    public static boolean isBrowserKey(int code) {
        return code == KEYCODE_TCL_BROWSEHERE ||
                code == 64 ||
                code == KeyEvent.KEYCODE_EXPLORER;
    }

    public static boolean isDisneyKey(int code) {
        return code == KEYCODE_TCL_DISNEY;
    }

    public static boolean isListKey(int code) {
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
        LaunchHelper.openApps(this);
    }

    private void openYouTube() {
        if (!LaunchHelper.openYouTube(this)) {
            Toast.makeText(this, LaunchHelper.PKG_YOUTUBE + " is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openNetflix() {
        if (!LaunchHelper.openNetflix(this)) {
            Toast.makeText(this, LaunchHelper.PKG_NETFLIX + " is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPrimeVideo() {
        if (!LaunchHelper.openPrimeVideo(this)) {
            Toast.makeText(this, LaunchHelper.PKG_PRIMEVIDEO + " is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBrowser() {
        if (!LaunchHelper.openBrowser(this)) {
            Toast.makeText(this, "No browser installed", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isTvForeground() {
        return LaunchHelper.PKG_TV.equals(foregroundPackage) || "com.tcl.tvinput".equals(foregroundPackage);
    }

    private void openLiveTv() {
        LaunchHelper.openLiveTv(this);
    }
}
