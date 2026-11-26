package org.opensaiclink.trial;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/** Temporarily changes the phone's brightness while sharing the display. */
final class ProjectionBrightness {
    private static final String TARGET = "target";
    private static final String PREVIOUS_LEVEL = "previous_level";
    private static final String PREVIOUS_MODE = "previous_mode";
    private static final int MANUAL = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;

    private ProjectionBrightness() {}

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences("projection_brightness", Context.MODE_PRIVATE);
    }

    static boolean isPending(Context context) {
        return state(context).contains(TARGET);
    }

    static synchronized void recover(Context context) {
        if (!Status.usbOpen && !Status.capturing) restore(context);
    }

    static synchronized boolean apply(Context context, int percent) {
        if (!Settings.System.canWrite(context) || !restore(context)) return false;
        SharedPreferences saved = state(context);
        ContentResolver settings = context.getContentResolver();
        int target = Math.round(percent * 255 / 100f);
        try {
            int level = Settings.System.getInt(settings, Settings.System.SCREEN_BRIGHTNESS);
            int mode = Settings.System.getInt(settings, Settings.System.SCREEN_BRIGHTNESS_MODE, MANUAL);
            if (!saved.edit().putInt(PREVIOUS_LEVEL, level).putInt(PREVIOUS_MODE, mode)
                    .putInt(TARGET, target).commit()) {
                throw new IllegalStateException("Cannot save the original brightness");
            }
            write(settings, Settings.System.SCREEN_BRIGHTNESS_MODE, MANUAL);
            write(settings, Settings.System.SCREEN_BRIGHTNESS, target);
            Trace.log("BRIGHTNESS_APPLIED", "Projection brightness set to " + percent + "%");
            return true;
        } catch (Exception e) {
            Trace.log("BRIGHTNESS_APPLY_ERROR", e.toString());
            restore(context);
            return false;
        }
    }

    static synchronized boolean restore(Context context) {
        SharedPreferences saved = state(context);
        if (!saved.contains(TARGET)) return true;
        if (!Settings.System.canWrite(context)) return false;
        ContentResolver settings = context.getContentResolver();
        try {
            if (Settings.System.getInt(settings, Settings.System.SCREEN_BRIGHTNESS, -1)
                    == saved.getInt(TARGET, -2)) {
                write(settings, Settings.System.SCREEN_BRIGHTNESS, saved.getInt(PREVIOUS_LEVEL, 0));
            }
            if (Settings.System.getInt(settings, Settings.System.SCREEN_BRIGHTNESS_MODE, -1) == MANUAL) {
                write(settings, Settings.System.SCREEN_BRIGHTNESS_MODE, saved.getInt(PREVIOUS_MODE, MANUAL));
            }
            return saved.edit().clear().commit();
        } catch (RuntimeException e) {
            Trace.log("BRIGHTNESS_RESTORE_ERROR", e.toString());
            return false;
        }
    }

    private static void write(ContentResolver settings, String key, int value) {
        if (!Settings.System.putInt(settings, key, value)) {
            throw new IllegalStateException("Cannot write " + key);
        }
    }
}
