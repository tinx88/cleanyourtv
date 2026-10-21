package com.gathin.tvhome;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Chooses local wallpapers by sunrise and sunset without network or Android dependencies. */
final class WallpaperDaypart {
    static final String MORNING = "morning";
    static final String DAY = "day";
    static final String SUNSET = "sunset";
    static final String NIGHT = "night";
    static final int DEFAULT_SUNRISE = 7 * 60;
    static final int DEFAULT_SUNSET = 18 * 60;

    private WallpaperDaypart() {}

    static int[] sunTimes(LocalDate today, double latitude, double longitude, ZoneId zone) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return new int[] {DEFAULT_SUNRISE, DEFAULT_SUNSET};
        }
        int[] result = Sun.riseAndSet(today, latitude, longitude, zone);
        if (result == null || result.length != 2 || result[0] <= 0
                || result[1] >= 1440 || result[1] <= result[0]) {
            return new int[] {DEFAULT_SUNRISE, DEFAULT_SUNSET};
        }
        return result;
    }

    static String phase(int minuteOfDay, int sunrise, int sunset) {
        int minute = modDay(minuteOfDay);
        if (sunrise < 0 || sunset > 1440 || sunset <= sunrise) {
            sunrise = DEFAULT_SUNRISE;
            sunset = DEFAULT_SUNSET;
        }
        if (inWindow(minute, sunrise - 45, sunrise + 90)) return MORNING;
        if (inWindow(minute, sunset - 90, sunset + 45)) return SUNSET;
        if (inWindow(minute, sunrise + 90, sunset - 90)) return DAY;
        return NIGHT;
    }

    static List<File> candidates(List<File> images, String phase) {
        List<File> matching = new ArrayList<>();
        for (File image : images) {
            String filename = image.getName().toLowerCase(Locale.ROOT);
            if (filename.startsWith(phase + "-") || filename.startsWith(phase + "_")) {
                matching.add(image);
            }
        }
        return matching.isEmpty() ? new ArrayList<>(images) : matching;
    }

    private static boolean inWindow(int minute, int start, int end) {
        if (end - start >= 1440) return true;
        int from = modDay(start), to = modDay(end);
        return from <= to ? minute >= from && minute < to : minute >= from || minute < to;
    }

    private static int modDay(int minute) {
        int m = minute % 1440;
        return m < 0 ? m + 1440 : m;
    }
}
