package com.gathin.tvhome;

import org.junit.Test;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class WeatherWallpaperSelectorTest {
    @Test public void onlySupportedConditions() {
        for (String c : new String[]{"sunny", "cloudy", "rainy", "stormy"}) {
            assertTrue(WeatherWallpaperSelector.validCondition(c));
        }
        for (String c : new String[]{"", "snow", "hurricane", "rainy-sun", "RAINY"}) {
            assertFalse(WeatherWallpaperSelector.validCondition(c));
        }
        assertFalse(WeatherWallpaperSelector.validCondition(null));
    }

    @Test public void outagesStayWithLocallyStoredCozyPictures() {
        List<File> items = Arrays.asList(new File("rainy-day.jpg"),
                new File("cozy-01.jpg"), new File("cozy-02.jpg"));
        List<File> fallback = WeatherWallpaperSelector.candidates(items, null, "day");
        assertEquals(2, fallback.size());
        assertEquals("cozy-01.jpg", fallback.get(0).getName());
    }

    @Test public void validWeatherCanUseMatchingPhotoIfEverAdded() {
        List<File> items = Arrays.asList(new File("cozy-01.jpg"),
                new File("rainy-day-01.jpg"), new File("rainy-night-01.jpg"));
        List<File> chosen = WeatherWallpaperSelector.candidates(items, "rainy", "night");
        assertEquals(1, chosen.size());
        assertEquals("rainy-night-01.jpg", chosen.get(0).getName());
    }
}
