package tv.cleanhome.gtv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static final String PKG_DREAMX = "com.google.android.apps.tv.dreamx";
    public static final String PKG_SYSTEMUI = "com.android.systemui";

    private static final Set<String> EXEMPT_WAKE_PACKAGES = new HashSet<>(Arrays.asList(
            PKG_DREAMX,
            PKG_SYSTEMUI,
            "com.spotify.tv.android",
            "com.spotify.music",
            "com.google.android.apps.youtube.music"
    ));

    public static volatile boolean isConnected = false;

    private volatile String foregroundPackage = null;
    private View keepScreenOnOverlay = null;
    private AudioManager audioManager = null;
    private AudioManager.AudioPlaybackCallback audioPlaybackCallback = null;

    public static boolean isWakeExemptPackage(String pkg, String launcherPkg) {
        if (pkg == null) return true;
        if (launcherPkg != null && launcherPkg.equals(pkg)) return true;
        return EXEMPT_WAKE_PACKAGES.contains(pkg);
    }

    public boolean isMediaPlaying() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
        if (audioManager == null) return false;

        boolean isMusicActive = audioManager.isMusicActive();
        if (isMusicActive) {
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                List<AudioPlaybackConfiguration> configs = audioManager.getActivePlaybackConfigurations();
                if (configs != null) {
                    for (AudioPlaybackConfiguration config : configs) {
                        AudioAttributes attrs = config.getAudioAttributes();
                        if (attrs != null) {
                            int usage = attrs.getUsage();
                            if (usage == AudioAttributes.USAGE_MEDIA
                                    || usage == AudioAttributes.USAGE_GAME
                                    || usage == AudioAttributes.USAGE_UNKNOWN) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public boolean shouldKeepScreenOn(String pkg) {
        if (isWakeExemptPackage(pkg, getPackageName())) {
            return false;
        }
        return isMediaPlaying();
    }

    private String getOrResolveForegroundPackage() {
        if (foregroundPackage != null) {
            return foregroundPackage;
        }
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                root.recycle();
                if (pkg != null) {
                    foregroundPackage = pkg.toString();
                    return foregroundPackage;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void updateScreenWakeState() {
        updateScreenWakeState(getOrResolveForegroundPackage());
    }

    private void updateScreenWakeState(String pkg) {
        if (pkg == null) {
            pkg = getOrResolveForegroundPackage();
        }
        boolean needed = shouldKeepScreenOn(pkg);
        if (needed && keepScreenOnOverlay == null) {
            try {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (wm != null) {
                    keepScreenOnOverlay = new View(this);
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            1, 1,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            PixelFormat.TRANSLUCENT
                    );
                    params.gravity = Gravity.TOP | Gravity.START;
                    wm.addView(keepScreenOnOverlay, params);
                    Log.i(TAG, "Attached FLAG_KEEP_SCREEN_ON overlay for active media in " + pkg);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to attach keep-screen-on overlay", t);
                keepScreenOnOverlay = null;
            }
        } else if (!needed && keepScreenOnOverlay != null) {
            detachKeepScreenOnOverlay();
            Log.i(TAG, "Removed keep-screen-on overlay (pkg=" + pkg + ")");
        }
    }

    private void detachKeepScreenOnOverlay() {
        if (keepScreenOnOverlay != null) {
            try {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (wm != null) {
                    wm.removeView(keepScreenOnOverlay);
                }
            } catch (Throwable ignored) {}
            keepScreenOnOverlay = null;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                String pkgStr = pkg.toString();
                if (getPackageName().equals(pkgStr)) {
                    CharSequence cls = event.getClassName();
                    if (cls == null || !MainActivity.class.getName().contentEquals(cls)) {
                        // Ignore events from our own non-MainActivity components (such as our keep-screen-on overlay)
                        return;
                    }
                }
                Log.i(TAG, "onAccessibilityEvent: foregroundPackage=" + pkgStr);
                foregroundPackage = pkgStr;
                updateScreenWakeState(foregroundPackage);
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

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            audioPlaybackCallback = new AudioManager.AudioPlaybackCallback() {
                @Override
                public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                    super.onPlaybackConfigChanged(configs);
                    updateScreenWakeState();
                }
            };
            try {
                audioManager.registerAudioPlaybackCallback(audioPlaybackCallback, null);
            } catch (Throwable t) {
                Log.w(TAG, "Failed to register audio playback callback", t);
            }
        }

        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                root.recycle();
                if (pkg != null) {
                    foregroundPackage = pkg.toString();
                    updateScreenWakeState(foregroundPackage);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean onUnbind(Intent intent) {
        isConnected = false;
        detachKeepScreenOnOverlay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null && audioPlaybackCallback != null) {
            try {
                audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback);
            } catch (Throwable ignored) {}
            audioPlaybackCallback = null;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        isConnected = false;
        detachKeepScreenOnOverlay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null && audioPlaybackCallback != null) {
            try {
                audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback);
            } catch (Throwable ignored) {}
            audioPlaybackCallback = null;
        }
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
            Log.i(TAG, "Intercepted input key " + code + ", opening TV inputs");
            openTvInputs();
            return true;
        }

        if (isListKey(code)) {
            if (isTvForeground()) {
                Log.i(TAG, "TV app is foreground; delegating list key " + code + " to native TV channel list");
                return super.onKeyEvent(event);
            }
            if (isLauncherForeground()) {
                Log.i(TAG, "Launcher is foreground; routing list key " + code + " to Apps tab");
                openApps();
                return true;
            }
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
        return code == 284 ||
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
        return code == KEYCODE_BOOKMARK ||
                code == KeyEvent.KEYCODE_BOOKMARK ||
                code == KEYCODE_TCL_LIST ||
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

    public static boolean isTvPackage(String pkg) {
        if (pkg == null) return false;
        return LaunchHelper.PKG_TV.equals(pkg) ||
                "com.tcl.tvinput".equals(pkg) ||
                "com.android.tv".equals(pkg) ||
                "com.google.android.tv".equals(pkg) ||
                "com.tcl.channelist".equals(pkg) ||
                pkg.startsWith("com.tcl.tv") ||
                pkg.contains("channellist");
    }

    private boolean isTvForeground() {
        if (isTvPackage(foregroundPackage)) {
            return true;
        }
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                root.recycle();
                if (pkg != null) {
                    foregroundPackage = pkg.toString();
                    updateScreenWakeState(foregroundPackage);
                    return isTvPackage(foregroundPackage);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean isLauncherForeground() {
        if (getPackageName().equals(foregroundPackage)) {
            return true;
        }
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                root.recycle();
                if (pkg != null) {
                    foregroundPackage = pkg.toString();
                    updateScreenWakeState(foregroundPackage);
                    return getPackageName().equals(foregroundPackage);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void openLiveTv() {
        LaunchHelper.openLiveTv(this);
    }

    private void openTvInputs() {
        LaunchHelper.openInputs(this);
    }
}
