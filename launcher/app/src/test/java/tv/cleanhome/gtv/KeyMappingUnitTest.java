package tv.cleanhome.gtv;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests verifying that remote key mapping logic is guaranteed to work
 * consistently across both target TV platforms:
 *
 * 1. TV 1: TCL Android 11 (RTD2851 platform, RC902V remote)
 * 2. TV 2: TCL Android 14 (MT5896 / G08 platform, RC833 remote)
 */
public class KeyMappingUnitTest {

    @Test
    public void testTv1RemoteKeyMappings() {
        // TV 1 (Android 11 / RTD2851)
        int keySettingsTcl = 4077;
        int keySettingsStandard = 176;
        int keyProfileTcl = 4016;
        int keyProfileLegacy = 303;
        int keyTv = 4001;
        int keyTvFallback1 = 170;
        int keyTvFallback2 = 172;
        int keyYouTube = 4065;
        int keyNetflix = 4062;
        int keyPrimeVideo = 4099;
        int keyBrowser = 4159;
        int keyAppsGrid = 4082;
        int keyExitBookmark = 4095;
        int keyInputs = 178;
        int keyList = 4090;
        int keyRecordList = 4126;

        // Settings
        assertTrue("TV1 settings (4077)", GlobalKeyService.isSettingsKey(keySettingsTcl));
        assertTrue("TV1 settings (176)", GlobalKeyService.isSettingsKey(keySettingsStandard));

        // Profile / Quick Menu
        assertTrue("TV1 profile (4016)", GlobalKeyService.isProfileKey(keyProfileTcl));
        assertTrue("TV1 profile legacy (303)", GlobalKeyService.isProfileKey(keyProfileLegacy));

        // TV / Guide / T-Key
        assertTrue("TV1 TV button (4001)", GlobalKeyService.isTvKey(keyTv));
        assertTrue("TV1 TV button (170)", GlobalKeyService.isTvKey(keyTvFallback1));
        assertTrue("TV1 TV button (172)", GlobalKeyService.isTvKey(keyTvFallback2));
        assertTrue("TV1 TV TKey (4020)", GlobalKeyService.isTvKey(4020));

        // App Buttons: YouTube, Netflix, Prime Video, Browser
        assertTrue("TV1 YouTube button (4065)", GlobalKeyService.isYouTubeKey(keyYouTube));
        assertTrue("TV1 Netflix button (4062)", GlobalKeyService.isNetflixKey(keyNetflix));
        assertTrue("TV1 Prime Video button (4099)", GlobalKeyService.isPrimeVideoKey(keyPrimeVideo));
        assertTrue("TV1 Browser button (4159)", GlobalKeyService.isBrowserKey(keyBrowser));

        // Apps tab / grid button
        assertTrue("TV1 Apps key (4082)", GlobalKeyService.isAppsKey(keyAppsGrid));
        assertTrue("TV1 Exit/Bookmark key (4095)", GlobalKeyService.isAppsKey(keyExitBookmark));

        // Inputs
        assertTrue("TV1 Inputs button (178)", GlobalKeyService.isInputKey(keyInputs));

        // Channel list
        assertTrue("TV1 List key (4090)", GlobalKeyService.isListKey(keyList));
        assertTrue("TV1 Record List key (4126)", GlobalKeyService.isListKey(keyRecordList));

        // All should be marked handled by GlobalKeyService outside TV
        assertTrue(GlobalKeyService.isHandledKey(keySettingsTcl, false));
        assertTrue(GlobalKeyService.isHandledKey(keyProfileTcl, false));
        assertTrue(GlobalKeyService.isHandledKey(keyTv, false));
        assertTrue(GlobalKeyService.isHandledKey(keyYouTube, false));
        assertTrue(GlobalKeyService.isHandledKey(keyNetflix, false));
        assertTrue(GlobalKeyService.isHandledKey(keyPrimeVideo, false));
        assertTrue(GlobalKeyService.isHandledKey(keyBrowser, false));
        assertTrue(GlobalKeyService.isHandledKey(keyAppsGrid, false));
        assertTrue(GlobalKeyService.isHandledKey(keyInputs, false));
        assertTrue(GlobalKeyService.isHandledKey(keyList, false));
    }

    @Test
    public void testTv2RemoteKeyMappings() {
        // TV 2 (Android 14 / G08 / RC833 remote)
        int keySettingsNotification = 83; // KEYCODE_NOTIFICATION
        int keyPeopleProfileSwitch = 288; // KEYCODE_PROFILE_SWITCH
        int keyTvButton = 4001;           // 0x000c005b / KEYCODE_TCL_EPG
        int keyAppsBookmark = 174;        // KEYCODE_BOOKMARK
        int keyAppsAllApps = 284;         // KEYCODE_ALL_APPS
        int keyAppsTcl = 4082;            // KEYCODE_TCL_ALL_APP
        int keyYouTubeButton3 = 190;      // KEYCODE_BUTTON_3
        int keyYouTubeOem = 4065;         // KEYCODE_TCL_YOUTUBE
        int keyNetflixOem = 4062;         // KEYCODE_TCL_NETFLIX
        int keyNetflixBtn12 = 199;        // KEYCODE_BUTTON_12
        int keyPrimeVideoOem = 4099;      // KEYCODE_TCL_PRIMEVIDEO
        int keyPrimeVideoBtn2 = 189;      // KEYCODE_BUTTON_2
        int keyBrowserBrowseHere = 4159;  // KEYCODE_TCL_BROWSEHERE
        int keyDisney = 4141;             // KEYCODE_TCL_DISNEY
        int keyInputs = 178;              // KEYCODE_TV_INPUT
        int keyList = 4090;

        // Settings
        assertTrue("TV2 settings (83)", GlobalKeyService.isSettingsKey(keySettingsNotification));

        // Profile / People
        assertTrue("TV2 profile (288)", GlobalKeyService.isProfileKey(keyPeopleProfileSwitch));

        // TV button
        assertTrue("TV2 TV button (4001)", GlobalKeyService.isTvKey(keyTvButton));
        assertTrue("TV2 TV fallback (170)", GlobalKeyService.isTvKey(170));

        // Apps button
        assertTrue("TV2 apps bookmark (174)", GlobalKeyService.isAppsKey(keyAppsBookmark));
        assertTrue("TV2 apps all_apps (284)", GlobalKeyService.isAppsKey(keyAppsAllApps));
        assertTrue("TV2 apps tcl_all_app (4082)", GlobalKeyService.isAppsKey(keyAppsTcl));

        // Dedicated streaming buttons
        assertTrue("TV2 YouTube button (190)", GlobalKeyService.isYouTubeKey(keyYouTubeButton3));
        assertTrue("TV2 YouTube button (4065)", GlobalKeyService.isYouTubeKey(keyYouTubeOem));
        assertTrue("TV2 Netflix button (4062)", GlobalKeyService.isNetflixKey(keyNetflixOem));
        assertTrue("TV2 Netflix button (199)", GlobalKeyService.isNetflixKey(keyNetflixBtn12));
        assertTrue("TV2 Prime Video button (4099)", GlobalKeyService.isPrimeVideoKey(keyPrimeVideoOem));
        assertTrue("TV2 Prime Video button (189)", GlobalKeyService.isPrimeVideoKey(keyPrimeVideoBtn2));
        assertTrue("TV2 Browser button (4159)", GlobalKeyService.isBrowserKey(keyBrowserBrowseHere));
        assertTrue("TV2 Disney button (4141)", GlobalKeyService.isDisneyKey(keyDisney));

        // Inputs button
        assertTrue("TV2 Inputs button (178)", GlobalKeyService.isInputKey(keyInputs));

        // Channel list
        assertTrue("TV2 List key (4090)", GlobalKeyService.isListKey(keyList));

        // All should be marked handled by GlobalKeyService outside TV
        assertTrue(GlobalKeyService.isHandledKey(keySettingsNotification, false));
        assertTrue(GlobalKeyService.isHandledKey(keyPeopleProfileSwitch, false));
        assertTrue(GlobalKeyService.isHandledKey(keyTvButton, false));
        assertTrue(GlobalKeyService.isHandledKey(keyAppsBookmark, false));
        assertTrue(GlobalKeyService.isHandledKey(keyYouTubeButton3, false));
        assertTrue(GlobalKeyService.isHandledKey(keyNetflixOem, false));
        assertTrue(GlobalKeyService.isHandledKey(keyPrimeVideoOem, false));
        assertTrue(GlobalKeyService.isHandledKey(keyBrowserBrowseHere, false));
        assertTrue(GlobalKeyService.isHandledKey(keyDisney, false));
        assertTrue(GlobalKeyService.isHandledKey(keyInputs, false));
    }

    @Test
    public void testNoCrossContaminationBetweenKeys() {
        // YouTube button 190 MUST NOT trigger Live TV / List
        assertFalse("190 must not be a List key", GlobalKeyService.isListKey(190));
        assertFalse("190 must not be a TV key", GlobalKeyService.isTvKey(190));
        assertFalse("190 must not be a Settings key", GlobalKeyService.isSettingsKey(190));
        assertFalse("190 must not be an Input key", GlobalKeyService.isInputKey(190));
        assertFalse("190 must not be a Netflix key", GlobalKeyService.isNetflixKey(190));

        // Settings button 83 MUST NOT trigger Apps or Profile
        assertFalse("83 must not be an Apps key", GlobalKeyService.isAppsKey(83));
        assertFalse("83 must not be a Profile key", GlobalKeyService.isProfileKey(83));
        assertFalse("83 must not be a TV key", GlobalKeyService.isTvKey(83));

        // Profile button 288 MUST NOT trigger Settings or TV
        assertFalse("288 must not be a Settings key", GlobalKeyService.isSettingsKey(288));
        assertFalse("288 must not be a TV key", GlobalKeyService.isTvKey(288));

        // TV button 4001 MUST NOT trigger Input or Apps
        assertFalse("4001 must not be an Input key", GlobalKeyService.isInputKey(4001));
        assertFalse("4001 must not be an Apps key", GlobalKeyService.isAppsKey(4001));

        // Input button 178 MUST NOT trigger TV
        assertFalse("178 must not be a TV key", GlobalKeyService.isTvKey(178));
        assertFalse("178 must not be a YouTube key", GlobalKeyService.isYouTubeKey(178));

        // Netflix button 4062 MUST NOT trigger YouTube, Prime Video, or TV
        assertFalse("4062 must not be YouTube", GlobalKeyService.isYouTubeKey(4062));
        assertFalse("4062 must not be Prime Video", GlobalKeyService.isPrimeVideoKey(4062));
        assertFalse("4062 must not be TV", GlobalKeyService.isTvKey(4062));

        // Prime Video button 4099 MUST NOT trigger Netflix or YouTube
        assertFalse("4099 must not be Netflix", GlobalKeyService.isNetflixKey(4099));
        assertFalse("4099 must not be YouTube", GlobalKeyService.isYouTubeKey(4099));
    }

    @Test
    public void testPassThroughKeysNotIntercepted() {
        // Standard TV navigation keys that should NEVER be swallowed
        int[] passThroughKeys = {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_0,
                KeyEvent.KEYCODE_1,
                KeyEvent.KEYCODE_9
        };

        for (int k : passThroughKeys) {
            assertFalse("Key " + k + " must not be handled by GlobalKeyService",
                    GlobalKeyService.isHandledKey(k, false));
        }
    }
}
