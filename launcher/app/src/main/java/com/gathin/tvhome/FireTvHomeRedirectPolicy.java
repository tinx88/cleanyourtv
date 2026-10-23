package com.gathin.tvhome;

/**
 * Fire OS 8 keeps Amazon's launcher as HOME. An opt-in accessibility service
 * can cover its window with Family Edition without changing the HOME role.
 *
 * Pure Java so the event filter and throttling can be unit tested.
 */
final class FireTvHomeRedirectPolicy {
    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    static final int WINDOW_STATE_CHANGED = 32; // AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    static final long COOLDOWN_MS = 750L;

    private FireTvHomeRedirectPolicy() {}

    static boolean elapsed(long now, long lastLaunch) {
        // A negative lastLaunch means that this service has never redirected.
        // Be tolerant of a clock reset rather than trapping Home forever.
        return lastLaunch < 0 || now < lastLaunch || now - lastLaunch >= COOLDOWN_MS;
    }

    static boolean shouldCover(int eventType, CharSequence packageName, long now, long lastLaunch) {
        return eventType == WINDOW_STATE_CHANGED
                && packageName != null
                && AMAZON_LAUNCHER.contentEquals(packageName)
                && elapsed(now, lastLaunch);
    }
}
