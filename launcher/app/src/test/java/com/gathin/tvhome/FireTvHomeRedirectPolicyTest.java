package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FireTvHomeRedirectPolicyTest {
    private static final int WINDOW_CHANGED = FireTvHomeRedirectPolicy.WINDOW_STATE_CHANGED;

    @Test public void onlyAmazonWindowTransitionsAreCovered() {
        assertTrue(FireTvHomeRedirectPolicy.shouldCover(
                WINDOW_CHANGED, "com.amazon.tv.launcher", 1000L, -1L));
        assertFalse(FireTvHomeRedirectPolicy.shouldCover(
                WINDOW_CHANGED, "com.amazon.firebat", 1000L, -1L));
        assertFalse(FireTvHomeRedirectPolicy.shouldCover(
                WINDOW_CHANGED, "com.android.settings", 1000L, -1L));
        assertFalse(FireTvHomeRedirectPolicy.shouldCover(
                WINDOW_CHANGED, null, 1000L, -1L));
        assertFalse(FireTvHomeRedirectPolicy.shouldCover(
                1, "com.amazon.tv.launcher", 1000L, -1L));
    }

    @Test public void repeatedAmazonEventsAreThrottled() {
        assertFalse(FireTvHomeRedirectPolicy.shouldCover(
                WINDOW_CHANGED, "com.amazon.tv.launcher", 1749L, 1000L));
        assertTrue(FireTvHomeRedirectPolicy.shouldCover(
                WINDOW_CHANGED, "com.amazon.tv.launcher", 1750L, 1000L));
    }

    @Test public void theFirstLaunchAndClockResetAreAllowed() {
        assertTrue(FireTvHomeRedirectPolicy.elapsed(0L, -1L));
        assertTrue(FireTvHomeRedirectPolicy.elapsed(10L, 1000L));
        assertFalse(FireTvHomeRedirectPolicy.elapsed(1100L, 1000L));
    }
}
