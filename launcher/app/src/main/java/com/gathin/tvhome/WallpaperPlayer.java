package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final String weatherUrl;
    private final CosySceneView cosyScene;
    private String weatherCondition;
    private long weatherCheckedAt;
    private boolean weatherPolling;
    private static final long WEATHER_POLL_MS = 5 * 60_000L;
    private final double latitude;
    private final double longitude;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        order = config.wallpaperOrder;
        weatherUrl = config.weatherUrl;
        latitude = coordinate(context, R.string.latitude);
        longitude = coordinate(context, R.string.longitude);
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        if (WallpaperRotation.ORDER_WEATHER.equals(order) && config.wallpaperEnabled) {
            cosyScene = new CosySceneView(context, null);
            root.addView(cosyScene, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            cosyScene = null;
        }
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (config.wallpaperEnabled && (!files.isEmpty() || cosyScene != null)) {
            root.addView(image, cosyScene != null ? 1 : 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // No shade layer at all when the theme doesn't dim: a full-screen view is overdraw on 1 GB TVs.
            if (config.wallpaperDim > 0) {
                View shade = new View(context);
                shade.setBackgroundColor((config.wallpaperDim * 255 / 100) << 24);
                shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                root.addView(shade, 1, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (cosyScene != null) {
                image.setVisibility(View.GONE);
                updateCosyPhase();
            } else {
                showFirst();
            }
        }
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        List<File> candidates = eligibleFiles();
        File first = WallpaperRotation.find(candidates, previous);
        if (first == null) first = WallpaperRotation.pick(candidates, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                displayed = first.getName();
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            first = WallpaperRotation.pick(eligibleFiles(), previous, order, random);
        }
    }

    void start() {
        active = true;
        if (cosyScene != null) cosyScene.setRunning(weatherCondition == null);
        update();
    }

    void stop() {
        active = false;
        if (cosyScene != null) cosyScene.setRunning(false);
    }

    void close() {
        active = false;
        closed = true;
        decoder.shutdownNow();
        if (cosyScene != null) cosyScene.setRunning(false);
    }

    void update() {
        if (!active || closed) return;
        pollWeatherIfDue();
        if (cosyScene != null) {
            updateCosyPhase();
            String condition = weatherCondition;
            // No forecast or no condition-matched local photos: always use animated Cosy Home.
            if (condition == null || WeatherWallpaperSelector.matching(files, condition).isEmpty()) {
                image.setVisibility(View.GONE);
                cosyScene.setVisibility(View.VISIBLE);
                cosyScene.setRunning(true);
                return;
            }
            // Keep the cosy scene behind the photo until the first weather image is decoded.
        }
        if (loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        List<File> candidates = eligibleFiles();
        File selected = WallpaperRotation.find(candidates, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(candidates, previous, order, random);
        }
        if (selected == null) return;
        if (selected.getName().equals(displayed)) {
            if (cosyScene != null) {
                image.setVisibility(View.VISIBLE);
                cosyScene.setRunning(false);
                cosyScene.setVisibility(View.GONE);
            }
            return;
        }
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                image.setImageBitmap(bitmap);
                image.setVisibility(View.VISIBLE);
                if (cosyScene != null) {
                    cosyScene.setRunning(false);
                    cosyScene.setVisibility(View.GONE);
                }
                displayed = next.getName();
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    private String currentPhase() {
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int[] times = WallpaperDaypart.sunTimes(LocalDate.now(), latitude, longitude,
                ZoneId.systemDefault());
        return WallpaperDaypart.phase(minute, times[0], times[1]);
    }

    private void updateCosyPhase() {
        if (cosyScene == null) return;
        String phase = currentPhase();
        CosySceneView.Phase p = WallpaperDaypart.MORNING.equals(phase) ? CosySceneView.Phase.DAWN
                : WallpaperDaypart.DAY.equals(phase) ? CosySceneView.Phase.DAY
                : WallpaperDaypart.SUNSET.equals(phase) ? CosySceneView.Phase.DUSK
                : CosySceneView.Phase.NIGHT;
        cosyScene.setPhase(p);
    }

    /** Poll in the decoder thread only while Home is visible. On failure, go straight to Cosy Home. */
    private void pollWeatherIfDue() {
        if (cosyScene == null || weatherUrl == null || weatherUrl.isEmpty() || weatherPolling) return;
        long now = System.currentTimeMillis();
        if (weatherCheckedAt != 0 && now >= weatherCheckedAt
                && now - weatherCheckedAt < WEATHER_POLL_MS) return;
        weatherCheckedAt = now;
        weatherPolling = true;
        decoder.execute(() -> {
            String result = WeatherFeed.fetchCondition(weatherUrl);
            root.post(() -> {
                weatherPolling = false;
                if (closed) return;
                weatherCondition = result;
                if (active) update();
            });
        });
    }

    /** Select weather pictures by condition + daypart, or the original daypart images. */
    private List<File> eligibleFiles() {
        if (WallpaperRotation.ORDER_WEATHER.equals(order)) {
            return WeatherWallpaperSelector.candidates(files, weatherCondition, currentPhase());
        }
        if (WallpaperRotation.ORDER_DAYPART.equals(order)) {
            return WallpaperDaypart.candidates(files, currentPhase());
        }
        return files;
    }

    private static double coordinate(Context ctx, int resource) {
        try {
            return Double.parseDouble(ctx.getString(resource).trim());
        } catch (NumberFormatException error) {
            return Double.NaN;
        }
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
