package tv.cleanhome.gtv;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherUnitTest {

    @Test
    public void testIsFahrenheitCountry() {
        assertTrue(MainActivity.isFahrenheitCountry("US"));
        assertTrue(MainActivity.isFahrenheitCountry("USA"));
        assertTrue(MainActivity.isFahrenheitCountry("us"));
        assertTrue(MainActivity.isFahrenheitCountry("BS"));
        assertTrue(MainActivity.isFahrenheitCountry("BZ"));
        assertTrue(MainActivity.isFahrenheitCountry("PR"));

        assertFalse(MainActivity.isFahrenheitCountry("BG"));
        assertFalse(MainActivity.isFahrenheitCountry("GB"));
        assertFalse(MainActivity.isFahrenheitCountry("DE"));
        assertFalse(MainActivity.isFahrenheitCountry("FR"));
        assertFalse(MainActivity.isFahrenheitCountry("SE"));
        assertFalse(MainActivity.isFahrenheitCountry(""));
        assertFalse(MainActivity.isFahrenheitCountry(null));
    }

    @Test
    public void testResolveTemperatureUnitAndSymbol_AutoLocation() {
        // Bulgaria -> Celsius with °C
        String[] bg = MainActivity.resolveTemperatureUnitAndSymbol("auto", "BG");
        assertEquals("celsius", bg[0]);
        assertEquals("°C", bg[1]);

        // United States -> Fahrenheit with °F
        String[] us = MainActivity.resolveTemperatureUnitAndSymbol("auto", "US");
        assertEquals("fahrenheit", us[0]);
        assertEquals("°F", us[1]);

        // Empty / Null config -> defaults to auto location detection
        String[] de = MainActivity.resolveTemperatureUnitAndSymbol("", "DE");
        assertEquals("celsius", de[0]);
        assertEquals("°C", de[1]);
    }

    @Test
    public void testResolveTemperatureUnitAndSymbol_ExplicitOverride() {
        // Explicit celsius override even for US
        String[] forcedC = MainActivity.resolveTemperatureUnitAndSymbol("celsius", "US");
        assertEquals("celsius", forcedC[0]);
        assertEquals("°C", forcedC[1]);

        // Explicit fahrenheit override even for BG
        String[] forcedF = MainActivity.resolveTemperatureUnitAndSymbol("fahrenheit", "BG");
        assertEquals("fahrenheit", forcedF[0]);
        assertEquals("°F", forcedF[1]);
    }
}
