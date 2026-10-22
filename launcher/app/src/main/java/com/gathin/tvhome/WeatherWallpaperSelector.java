package com.gathin.tvhome;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure selection logic. No network or Android dependencies; Cozy Home is ALWAYS the fallback. */
final class WeatherWallpaperSelector {
    private WeatherWallpaperSelector() {}

    static boolean validCondition(String condition) {
        return "sunny".equals(condition) || "cloudy".equals(condition)
                || "rainy".equals(condition) || "stormy".equals(condition);
    }

    static List<File> matching(List<File> images, String prefix) {
        List<File> result = new ArrayList<>();
        for (File f : images) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix + "-") || name.startsWith(prefix + "_")) result.add(f);
        }
        return result;
    }

    /**
     * Condition + time of day takes priority. Any matching condition photo comes second.
     * No feed, stale feed, unknown condition or missing photos => offline Cozy Home only.
     */
    static List<File> candidates(List<File> images, String condition, String phase) {
        if (validCondition(condition)) {
            List<File> exact = matching(images, condition + "-" + phase);
            if (!exact.isEmpty()) return exact;
            List<File> weather = matching(images, condition);
            if (!weather.isEmpty()) return weather;
        }
        List<File> cozy = matching(images, "cozy");
        return cozy.isEmpty() ? new ArrayList<>(images) : cozy;
    }
}
