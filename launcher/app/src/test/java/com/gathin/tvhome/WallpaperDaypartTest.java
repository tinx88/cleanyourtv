package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class WallpaperDaypartTest {
    @Test public void sunriseAndSunsetAreCalculatedOfflineForMelbourne() {
        int[] summer = WallpaperDaypart.sunTimes(LocalDate.of(2026, 12, 1),
                -37.8136, 144.9631, ZoneId.of("Australia/Melbourne"));
        int[] winter = WallpaperDaypart.sunTimes(LocalDate.of(2026, 6, 1),
                -37.8136, 144.9631, ZoneId.of("Australia/Melbourne"));
        assertTrue(summer[0] < winter[0]);
        assertTrue(summer[1] > winter[1]);
    }

    @Test public void photosFollowActualSunriseAndSunset() {
        int sunrise = 450, sunset = 1050;
        assertEquals("night", WallpaperDaypart.phase(200, sunrise, sunset));
        assertEquals("morning", WallpaperDaypart.phase(410, sunrise, sunset));
        assertEquals("morning", WallpaperDaypart.phase(500, sunrise, sunset));
        assertEquals("day", WallpaperDaypart.phase(750, sunrise, sunset));
        assertEquals("sunset", WallpaperDaypart.phase(1020, sunrise, sunset));
        assertEquals("night", WallpaperDaypart.phase(1170, sunrise, sunset));
    }

    @Test public void filtersFilenamesAndFallsBackWhenGroupIsEmpty() {
        List<File> files = Arrays.asList(new File("morning-01.jpg"),
                new File("day_01.webp"), new File("night-01.png"));
        assertEquals(1, WallpaperDaypart.candidates(files, "morning").size());
        assertEquals("day_01.webp", WallpaperDaypart.candidates(files, "day").get(0).getName());
        assertEquals(files.size(), WallpaperDaypart.candidates(files, "sunset").size());
    }

    @Test public void invalidCoordinatesUseSafeFallback() {
        int[] fallback = WallpaperDaypart.sunTimes(LocalDate.of(2026, 10, 22),
                Double.NaN, 144.9, ZoneId.of("Australia/Melbourne"));
        assertEquals(WallpaperDaypart.DEFAULT_SUNRISE, fallback[0]);
        assertEquals(WallpaperDaypart.DEFAULT_SUNSET, fallback[1]);
    }
}
