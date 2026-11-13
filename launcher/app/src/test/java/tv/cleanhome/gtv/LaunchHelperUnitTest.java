package tv.cleanhome.gtv;

import android.content.Intent;
import android.provider.Settings;
import org.junit.Test;
import java.util.Arrays;
import tv.cleanhome.gtv.LaunchHelper.IntentTarget;

import static org.junit.Assert.*;

/**
 * Cross-platform verification test suite guaranteeing that all launcher actions,
 * system settings shortcuts, screensaver triggers, and remote key integrations
 * have validated targets across both supported TV hardware platforms:
 *
 * 1. TV 1: TCL Android 11 (Realtek RTD2851 platform, RC902V remote)
 * 2. TV 2: TCL Android 14 (MediaTek MT5896 / G08 Google TV platform, RC833 remote)
 */
public class LaunchHelperUnitTest {

    @Test
    public void testYouTubePackagesOrder() {
        assertNotNull(LaunchHelper.YOUTUBE_PACKAGES);
        assertTrue("At least one YouTube package", LaunchHelper.YOUTUBE_PACKAGES.length >= 4);
        assertEquals("com.google.android.youtube.tv", LaunchHelper.PKG_YOUTUBE);
        assertEquals("First priority should be official YouTube TV", "com.google.android.youtube.tv", LaunchHelper.YOUTUBE_PACKAGES[0]);
        assertEquals("Second priority should be SmartTube video manager", "com.liskovsoft.videomanager", LaunchHelper.YOUTUBE_PACKAGES[1]);
        assertEquals("Third priority should be SmartTube beta", "com.liskovsoft.smarttubetv.beta", LaunchHelper.YOUTUBE_PACKAGES[2]);
        assertEquals("Fourth priority should be SmartTube stable", "com.liskovsoft.smarttubetv", LaunchHelper.YOUTUBE_PACKAGES[3]);
    }

    @Test
    public void testScreensaverTargetsCoverage() {
        IntentTarget[] targets = LaunchHelper.SCREENSAVER_TARGETS;
        assertNotNull(targets);
        assertTrue("Must have multiple screensaver fallback targets", targets.length >= 4);

        // TV2 (Android 14 Google TV) requires pano SLEEP or DaydreamVoiceAction
        assertEquals("Priority 1 must be Google TV SLEEP action", "com.google.android.pano.action.SLEEP", targets[0].action);
        assertTrue("Must contain DaydreamVoiceAction",
                containsComponent(targets, "com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamVoiceAction"));
        assertTrue("Must contain Google TV Ambient mode",
                containsAction(targets, "com.google.android.tv.settings.ambient"));

        // TV1 (Android 11) uses Somnambulator
        assertTrue("Must contain Android 11 Somnambulator fallback",
                containsComponent(targets, "com.android.systemui", "com.android.systemui.Somnambulator"));

        // Final fallback
        assertTrue("Must contain standard Dream Settings fallback",
                containsAction(targets, Settings.ACTION_DREAM_SETTINGS));
    }

    @Test
    public void testInputsTargetsCoverage() {
        IntentTarget[] targets = LaunchHelper.INPUTS_TARGETS;
        assertNotNull(targets);
        assertTrue("Must contain Android TV standard VIEW_INPUTS",
                containsAction(targets, "com.android.tv.action.VIEW_INPUTS"));
        assertTrue("Must contain TV2 Quick_Panel InputActivity",
                containsComponent(targets, "com.tcl.suspension", "com.tcl.suspension.ui.main.InputActivity"));
        assertTrue("Must contain TV1 tvinput MainActivity",
                containsComponent(targets, "com.tcl.tvinput", "com.tcl.tvinput.MainActivity"));
    }

    @Test
    public void testAccountsTargetsCoverage() {
        IntentTarget[] targets = LaunchHelper.ACCOUNTS_TARGETS;
        assertNotNull(targets);

        // TV2 (Android 14 Google TV) AccountActivity
        assertEquals("Priority 1 must be Google TV AccountActivity", "com.google.android.tv.settings.AccountActivity", targets[0].className);
        assertEquals("Priority 1 package must be TvSettings", "com.android.tv.settings", targets[0].packageName);

        // Android TV Sync
        assertTrue("Must contain ACCOUNT_SYNC_SETTINGS",
                containsAction(targets, "android.settings.ACCOUNT_SYNC_SETTINGS"));

        // TV1 (Android 11 TvSettings)
        assertTrue("Must contain TV1 AccountSyncActivity",
                containsComponent(targets, "com.android.tv.settings", "com.android.tv.settings.accounts.AccountSyncActivity"));

        // Fallback
        assertTrue("Must contain standard Settings fallback",
                containsAction(targets, Settings.ACTION_SETTINGS));
    }

    @Test
    public void testNetworkTargetsCoverage() {
        IntentTarget[] targets = LaunchHelper.NETWORK_TARGETS;
        assertNotNull(targets);

        // TV2 (Android 14 Google TV)
        assertTrue("Must contain Google TV NetworkActivity",
                containsComponent(targets, "com.android.tv.settings", "com.android.tv.settings.connectivity.NetworkActivity"));
        assertTrue("Must contain Google TV NETWORK_SETTINGS_TV_OEM_LINK",
                containsAction(targets, "android.settings.NETWORK_SETTINGS_TV_OEM_LINK"));

        // TV1 (Android 11 TCL)
        assertTrue("Must contain TV1 TCL NETWORKSETTINGS action",
                containsAction(targets, "com.tcl.settings.action.NETWORKSETTINGS"));

        // Fallbacks
        assertTrue("Must contain WIRELESS_SETTINGS",
                containsAction(targets, Settings.ACTION_WIRELESS_SETTINGS));
        assertTrue("Must contain SETTINGS fallback",
                containsAction(targets, Settings.ACTION_SETTINGS));
    }

    @Test
    public void testRemotesTargetsCoverage() {
        IntentTarget[] targets = LaunchHelper.REMOTES_TARGETS;
        assertNotNull(targets);

        // TV2 (Android 14 Google TV)
        assertTrue("Must contain ACCESSORIES_CONNECTED_DEVICES action",
                containsAction(targets, "com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES"));
        assertTrue("Must contain TV2 AccessoriesActivity",
                containsComponent(targets, "com.android.tv.settings", "com.tcl.settings.accessories.AccessoriesActivity"));

        // TV1 (Android 11 TCL)
        assertTrue("Must contain TV1 AccessoriesActivity",
                containsComponent(targets, "com.android.tv.settings", "com.tcl.android.tv.settings.accessories.AccessoriesActivity"));

        // Fallbacks
        assertTrue("Must contain BLUETOOTH_SETTINGS",
                containsAction(targets, "android.settings.BLUETOOTH_SETTINGS"));
        assertTrue("Must contain SETTINGS fallback",
                containsAction(targets, Settings.ACTION_SETTINGS));
    }

    @Test
    public void testPictureTargetsCoverage() {
        IntentTarget[] targets = LaunchHelper.PICTURE_TARGETS;
        assertNotNull(targets);

        // TV2 (Android 14 TCL Picture)
        assertTrue("Must contain TV2 PictureActivity",
                containsComponent(targets, "com.android.tv.settings", "com.tcl.settings.PictureActivity"));

        // Fallback
        assertTrue("Must contain DISPLAY_SETTINGS",
                containsAction(targets, Settings.ACTION_DISPLAY_SETTINGS));
        assertTrue("Must contain SETTINGS fallback",
                containsAction(targets, Settings.ACTION_SETTINGS));
    }

    @Test
    public void testAccessibilityTargetsCoverage() {
        IntentTarget[] targets = LaunchHelper.ACCESSIBILITY_TARGETS;
        assertNotNull(targets);

        assertTrue("Must contain ACCESSIBILITY_TV_OEM_LINK",
                containsAction(targets, "android.settings.ACCESSIBILITY_TV_OEM_LINK"));
        assertTrue("Must contain AccessibilitySettingsActivity",
                containsComponent(targets, "com.android.tv.settings", "com.android.tv.settings.oemlink.AccessibilitySettingsActivity"));
        assertTrue("Must contain ACCESSIBILITY_SETTINGS fallback",
                containsAction(targets, Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @Test
    public void testAppsTargetsCoverage() {
        IntentTarget[] targets = LaunchHelper.APPS_TARGETS;
        assertNotNull(targets);

        assertTrue("Must contain AllAppsActivity",
                containsComponent(targets, "com.android.tv.settings", "com.android.tv.settings.device.apps.AllAppsActivity"));
        assertTrue("Must contain AppsActivity",
                containsComponent(targets, "com.android.tv.settings", "com.android.tv.settings.device.apps.AppsActivity"));
        assertTrue("Must contain MANAGE_APPLICATIONS_SETTINGS fallback",
                containsAction(targets, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
    }

    @Test
    public void testLiveTvTargetsCoverage() {
        assertEquals("Live TV package must be com.tcl.tv", "com.tcl.tv", LaunchHelper.PKG_TV);
        IntentTarget[] targets = LaunchHelper.LIVE_TV_TARGETS;
        assertNotNull(targets);
        assertTrue("Must contain player.TVActivity for TV2",
                containsComponent(targets, "com.tcl.tv", "com.tcl.player.TVActivity"));
        assertTrue("Must contain tv.TVActivity for TV1",
                containsComponent(targets, "com.tcl.tv", "com.tcl.tv.TVActivity"));
        assertTrue("Must contain tcl.sys.intent.action.TV fallback",
                containsAction(targets, "tcl.sys.intent.action.TV"));
    }

    @Test
    public void testNetflixTargetsCoverage() {
        assertEquals("Netflix package must be com.netflix.ninja", "com.netflix.ninja", LaunchHelper.PKG_NETFLIX);
        IntentTarget[] targets = LaunchHelper.NETFLIX_TARGETS;
        assertNotNull(targets);
        assertTrue("Must contain Netflix MainActivity component",
                containsComponent(targets, "com.netflix.ninja", "com.netflix.ninja.MainActivity"));
    }

    @Test
    public void testPrimeVideoTargetsCoverage() {
        assertEquals("Prime Video package must be com.amazon.amazonvideo.livingroom",
                "com.amazon.amazonvideo.livingroom", LaunchHelper.PKG_PRIMEVIDEO);
        IntentTarget[] targets = LaunchHelper.PRIMEVIDEO_TARGETS;
        assertNotNull(targets);
        assertTrue("Must contain IgnitionActivity component",
                containsComponent(targets, "com.amazon.amazonvideo.livingroom", "com.amazon.ignition.IgnitionActivity"));
        assertTrue("Must contain AMAZON_BUTTON action fallback",
                containsAction(targets, "com.amazon.amazonvideo.livingroom.AMAZON_BUTTON"));
    }

    @Test
    public void testBrowserPackagesCoverage() {
        String[] browsers = LaunchHelper.BROWSER_PACKAGES;
        assertNotNull(browsers);
        assertTrue("Must contain at least 4 browser options", browsers.length >= 4);
        assertEquals("First priority must be TV Bro", "com.phlox.tvwebbrowser", browsers[0]);
        assertEquals("Second priority must be TCL Browser", "com.tcl.browser", browsers[1]);
    }

    @Test
    public void testDebounceLogic() {
        long base = 5000L;
        // Same millisecond -> debounce
        assertTrue("Same millisecond must debounce", LaunchHelper.shouldDebounce(base, base, 1000L));
        // Within 999ms -> debounce
        assertTrue("Within 999ms must debounce", LaunchHelper.shouldDebounce(base + 999L, base, 1000L));
        // Exactly at 1000ms -> do NOT debounce
        assertFalse("At 1000ms must not debounce", LaunchHelper.shouldDebounce(base + 1000L, base, 1000L));
        // Above 1000ms -> do NOT debounce
        assertFalse("Above 1000ms must not debounce", LaunchHelper.shouldDebounce(base + 2500L, base, 1000L));
    }

    private static boolean containsAction(IntentTarget[] targets, String action) {
        if (targets == null || action == null) return false;
        return Arrays.stream(targets).anyMatch(t -> action.equals(t.action));
    }

    private static boolean containsComponent(IntentTarget[] targets, String pkg, String cls) {
        if (targets == null || pkg == null || cls == null) return false;
        return Arrays.stream(targets).anyMatch(t -> pkg.equals(t.packageName) && cls.equals(t.className));
    }
}
