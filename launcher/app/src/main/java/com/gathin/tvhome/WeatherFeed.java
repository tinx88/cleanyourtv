package com.gathin.tvhome;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Reads a small credential-free LAN weather feed. Null means the offline Cosy fallback. */
final class WeatherFeed {
    private WeatherFeed() {}

    static final class WeatherData {
        final String condition;
        final Double temperature;

        WeatherData(String condition, Double temperature) {
            this.condition = condition;
            this.temperature = temperature;
        }
    }

    /** Compatibility with callers that only need the animated weather category. */
    static String fetchCondition(String address) {
        WeatherData data = fetch(address);
        return data == null ? null : data.condition;
    }

    static WeatherData fetch(String address) {
        if (address == null || address.trim().isEmpty()) return null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(address.trim());
            if (!"http".equals(url.getProtocol())
                    || url.getPort() != 5056 || !"/weather".equals(url.getPath())
                    || (url.getQuery() != null && !url.getQuery().isEmpty())
                    || url.getUserInfo() != null) return null;
            // Explicit RFC1918 IPv4 host only, not a public endpoint or hostname.
            String host = url.getHost();
            if (!host.matches("^(10\\.\\d+\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+|172\\.(1[6-9]|2[0-9]|3[01])\\.\\d+\\.\\d+)$")) return null;
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[2048];
                int count;
                while ((count = in.read(bytes)) != -1) {
                    if (out.size() + count > 8192) return null;
                    out.write(bytes, 0, count);
                }
                JSONObject weather = new JSONObject(out.toString("UTF-8"));
                String condition = weather.optString("condition", "");
                if (!WeatherWallpaperSelector.validCondition(condition)) return null;
                double temp = weather.optDouble("temperature", Double.NaN);
                // A stale observation is null in FamilyOS. Never show an invented temperature.
                Double temperature = Double.isFinite(temp) && temp > -90 && temp < 65 ? temp : null;
                return new WeatherData(condition, temperature);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
