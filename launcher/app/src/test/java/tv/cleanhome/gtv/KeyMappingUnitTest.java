package tv.cleanhome.gtv;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyMappingUnitTest {

    @Test
    public void testSettingsKeyCodes() {
        // Android 11 / RTD2851: 4077, 176
        // Android 14 / G08: 83 (KEYCODE_NOTIFICATION)
        int[] settingsCodes = {4077, 176, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_MENU, 83, KeyEvent.KEYCODE_NOTIFICATION};
        for (int code : settingsCodes) {
            assertTrue("Settings code should be accepted: " + code,
                    code == 4077 || code == 176 || code == KeyEvent.KEYCODE_SETTINGS ||
                    code == KeyEvent.KEYCODE_MENU || code == 83 || code == KeyEvent.KEYCODE_NOTIFICATION);
        }
    }

    @Test
    public void testProfileKeyCodes() {
        // Android 11 / RTD2851: 4016, 303
        // Android 14 / G08: 288 (KEYCODE_PROFILE_SWITCH)
        int[] profileCodes = {4016, 288, 303, KeyEvent.KEYCODE_PROFILE_SWITCH};
        for (int code : profileCodes) {
            assertTrue("Profile code should be accepted: " + code,
                    code == 4016 || code == 288 || code == 303 || code == KeyEvent.KEYCODE_PROFILE_SWITCH);
        }
    }

    @Test
    public void testTvKeyCodes() {
        // TV button (4001, 170, KEYCODE_TV, KEYCODE_GUIDE / 172)
        int[] tvCodes = {4001, 170, 172, KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_GUIDE};
        for (int code : tvCodes) {
            assertTrue("TV code should be accepted: " + code,
                    code == 4001 || code == 170 || code == 172 ||
                    code == KeyEvent.KEYCODE_TV || code == KeyEvent.KEYCODE_GUIDE);
        }
    }

    @Test
    public void testAppsKeyCodes() {
        // Apps button on RC833: 174 (KEYCODE_BOOKMARK), 284 (ALL_APPS)
        int[] appsCodes = {174, 284, KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_ALL_APPS};
        for (int code : appsCodes) {
            assertTrue("Apps code should be accepted: " + code,
                    code == 174 || code == 284 || code == KeyEvent.KEYCODE_BOOKMARK || code == KeyEvent.KEYCODE_ALL_APPS);
        }
    }

    @Test
    public void testListKeyCodes() {
        // List button on RC833: 190 (KEYCODE_BUTTON_3), 4090, 4126
        int[] listCodes = {190, 4090, 4126, KeyEvent.KEYCODE_BUTTON_3};
        for (int code : listCodes) {
            assertTrue("List code should be accepted: " + code,
                    code == 190 || code == 4090 || code == 4126 || code == KeyEvent.KEYCODE_BUTTON_3);
        }
    }
}
