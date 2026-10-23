package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS 8 only. Amazon's protected launcher remains the system HOME.
 *
 * When that launcher appears after HOME, app exit or boot, cover it with
 * Family Edition. The user must explicitly enable this separate accessibility
 * service: the existing HomeKeyService used by Sony/TCL is unaffected.
 *
 * The service observes window metadata only, never window contents.
 * No Home redirect happens just because this service is connected.
 */
public final class FireTvHomeService extends AccessibilityService {
    private static final String TAG = "FireTvHomeService";
    private long lastLaunchUptime = -1L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        long now = SystemClock.uptimeMillis();
        if (FireTvHomeRedirectPolicy.shouldCover(
                event.getEventType(), event.getPackageName(), now, lastLaunchUptime)) {
            showFamilyHome(now);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Fire OS generally does not deliver the physical HOME key to accessibility;
        // the Amazon-launcher window event above is the tested fallback.
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            showFamilyHome(SystemClock.uptimeMillis());
        }
        return true;
    }

    private void showFamilyHome(long now) {
        if (!FireTvHomeRedirectPolicy.elapsed(now, lastLaunchUptime)) return;
        lastLaunchUptime = now;
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (ActivityNotFoundException | SecurityException error) {
            // Leave Amazon HOME alone if Fire OS rejects a background start.
            Log.w(TAG, "Could not cover Amazon HOME", error);
        }
    }

    @Override
    public void onInterrupt() {
        // No ongoing polling or work to stop.
    }
}
