package tv.cleanhome.gtv;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SunTest {

    private static final ZoneId VILNIUS = ZoneId.of("Europe/Vilnius");
    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Test
    public void vilniusWinterDayIsShortAndLate() {
        int[] t = Sun.riseAndSet(LocalDate.of(2026, 12, 21), 54.69, 25.28, VILNIUS);
        assertNotNull(t);
        assertTrue("sunrise " + t[0], t[0] > 8 * 60 + 30 && t[0] < 9 * 60);      // about 08:40
        assertTrue("sunset " + t[1], t[1] > 15 * 60 + 40 && t[1] < 16 * 60 + 5);  // about 15:55
    }

    @Test
    public void vilniusSummerDayIsLongInLocalSummerTime() {
        int[] t = Sun.riseAndSet(LocalDate.of(2026, 6, 21), 54.69, 25.28, VILNIUS);
        assertNotNull(t);
        assertTrue("sunrise " + t[0], t[0] > 4 * 60 + 30 && t[0] < 4 * 60 + 55);  // about 04:42
        assertTrue("sunset " + t[1], t[1] > 21 * 60 + 50 && t[1] < 22 * 60 + 10); // about 22:00
    }

    @Test
    public void southernHemisphereSeasonsAreInverted() {
        // Sydney in December is summer (early sunrise, late sunset)
        int[] summer = Sun.riseAndSet(LocalDate.of(2026, 12, 21), -33.87, 151.21, SYDNEY);
        assertNotNull(summer);
        assertTrue("summer sunrise " + summer[0], summer[0] < 6 * 60);
        assertTrue("summer sunset " + summer[1], summer[1] > 19 * 60 + 30);

        // Sydney in June is winter (later sunrise, early sunset)
        int[] winter = Sun.riseAndSet(LocalDate.of(2026, 6, 21), -33.87, 151.21, SYDNEY);
        assertNotNull(winter);
        assertTrue("winter sunrise " + winter[0], winter[0] > 6 * 60 + 30);
        assertTrue("winter sunset " + winter[1], winter[1] < 17 * 60 + 30);
    }

    @Test
    public void noSunriseOrSunsetAtThePoles() {
        assertNull(Sun.riseAndSet(LocalDate.of(2026, 12, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen")));
        assertNull(Sun.riseAndSet(LocalDate.of(2026, 6, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen")));
    }
}
