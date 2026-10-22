package com.gathin.tvhome;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** No credentials. Reads a strictly bounded LAN feed; failures mean cozy/offline wallpaper. */
final class WeatherFeed {
    private WeatherFeed() {}

    static String fetchCondition(String address) {
        if (address == null || address.trim().isEmpty()) return null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(address.trim());
            if (!"http".equals(url.getProtocol()) ||
                    url.getPort() != 5056 || !"/weather".equals(url.getPath()) ||
                    (url.getQuery() != null && !url.getQuery().isEmpty())) return null;
            // Pinned to an RFC1918 LAN address; never fetch URLs from a public server.
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
                String condition = new JSONObject(out.toString("UTF-8")).optString("condition", "");
                return WeatherWallpaperSelector.validCondition(condition) ? condition : null;
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
