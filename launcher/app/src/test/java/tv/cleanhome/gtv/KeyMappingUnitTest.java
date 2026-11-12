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
        int keyInputs = 178;
        int keyList = 4090;
        int keyRecordList = 4126;

        // Settings
        assertTrue("TV1 settings (4077)", GlobalKeyService.isSettingsKey(keySettingsTcl));
        assertTrue("TV1 settings (176)", GlobalKeyService.isSettingsKey(keySettingsStandard));

        // Profile / Quick Menu
        assertTrue("TV1 profile (4016)", GlobalKeyService.isProfileKey(keyProfileTcl));
        assertTrue("TV1 profile legacy (303)", GlobalKeyService.isProfileKey(keyProfileLegacy));

        // TV / Guide
        assertTrue("TV1 TV button (4001)", GlobalKeyService.isTvKey(keyTv));
        assertTrue("TV1 TV button (170)", GlobalKeyService.isTvKey(keyTvFallback1));
        assertTrue("TV1 TV button (172)", GlobalKeyService.isTvKey(keyTvFallback2));

        // YouTube
        assertTrue("TV1 YouTube button (4065)", GlobalKeyService.isYouTubeKey(keyYouTube));

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
        assertTrue(GlobalKeyService.isHandledKey(keyInputs, false));
        assertTrue(GlobalKeyService.isHandledKey(keyList, false));
    }

    @Test
    public void testTv2RemoteKeyMappings() {
        // TV 2 (Android 14 / G08 / RC833 remote)
        int keySettingsNotification = 83; // KEYCODE_NOTIFICATION
        int keyPeopleProfileSwitch = 288; // KEYCODE_PROFILE_SWITCH
        int keyTvButton = 4001;           // 0x000c005b
        int keyAppsBookmark = 174;        // KEYCODE_BOOKMARK
        int keyAppsAllApps = 284;         // KEYCODE_ALL_APPS
        int keyYouTubeButton3 = 190;      // KEYCODE_BUTTON_3
        int keyInputs = 178;              // KEYCODE_TV_INPUT
        int keyList = 4090;

        // Settings
        assertTrue("TV2 settings (83)", GlobalKeyService.isSettingsKey(keySettingsNotification));

        // Profile / People
        assertTrue("TV2 profile (288)", GlobalKeyService.isProfileKey(keyPeopleProfileSwitch));

        // TV button
        assertTrue("TV2 TV button (4001)", GlobalKeyService.isTvKey(keyTvButton));

        // Apps button
        assertTrue("TV2 apps bookmark (174)", GlobalKeyService.isAppsKey(keyAppsBookmark));
        assertTrue("TV2 apps all_apps (284)", GlobalKeyService.isAppsKey(keyAppsAllApps));

        // YouTube button (crucial: 190 was previously misclassified as List)
        assertTrue("TV2 YouTube button (190)", GlobalKeyService.isYouTubeKey(keyYouTubeButton3));

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
        assertTrue(GlobalKeyService.isHandledKey(keyInputs, false));
    }

    @Test
    public void testNoCrossContaminationBetweenKeys() {
        // YouTube button 190 MUST NOT trigger Live TV / List
        assertFalse("190 must not be a List key", GlobalKeyService.isListKey(190));
        assertFalse("190 must not be a TV key", GlobalKeyService.isTvKey(190));
        assertFalse("190 must not be a Settings key", GlobalKeyService.isSettingsKey(190));
        assertFalse("190 must not be an Input key", GlobalKeyService.isInputKey(190));

        // Settings button 83 MUST NOT trigger Apps or Profile
        assertFalse("83 must not be an Apps key", GlobalKeyService.isAppsKey(83));
        assertFalse("83 must not be a Profile key", GlobalKeyService.isProfileKey(83));
        assertFalse("83 must not be a TV key", GlobalKeyService.isTvKey(83));

        // Profile button 288 MUST NOT trigger Settings or TV
        assertFalse("288 must not be a Settings key", GlobalKeyService.isSettingsKey(288));
        assertFalse("288 must not be a TV key", GlobalKeyService.isTvKey(288));

        // TV button 4001 MUST NOT trigger Input
        assertFalse("4001 must not be an Input key", GlobalKeyService.isInputKey(4001));

        // Input button 178 MUST NOT trigger TV
        assertFalse("178 must not be a TV key", GlobalKeyService.isTvKey(178));
        assertFalse("178 must not be a YouTube key", GlobalKeyService.isYouTubeKey(178));
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

        for (int code : passThroughKeys) {
            assertFalse("Key " + code + " must pass through", GlobalKeyService.isHandledKey(code, false));
            assertFalse("Key " + code + " must not match settings", GlobalKeyService.isSettingsKey(code));
            assertFalse("Key " + code + " must not match profile", GlobalKeyService.isProfileKey(code));
            assertFalse("Key " + code + " must not match tv", GlobalKeyService.isTvKey(code));
            assertFalse("Key " + code + " must not match apps", GlobalKeyService.isAppsKey(code));
            assertFalse("Key " + code + " must not match input", GlobalKeyService.isInputKey(code));
            assertFalse("Key " + code + " must not match youtube", GlobalKeyService.isYouTubeKey(code));
            assertFalse("Key " + code + " must not match list", GlobalKeyService.isListKey(code));
        }
    }

    @Test
    public void testTvForegroundDelegation() {
        // When TV app is in foreground, List key (4090 / 4126) must delegate to native TV channel list
        assertFalse("List key must delegate when TV is foreground", GlobalKeyService.isHandledKey(4090, true));
        assertFalse("Record list key must delegate when TV is foreground", GlobalKeyService.isHandledKey(4126, true));

        // When TV is NOT foreground, List key must be handled (to launch TV)
        assertTrue("List key must be handled when TV is background", GlobalKeyService.isHandledKey(4090, false));
        assertTrue("Record list key must be handled when TV is background", GlobalKeyService.isHandledKey(4126, false));
    }
}
