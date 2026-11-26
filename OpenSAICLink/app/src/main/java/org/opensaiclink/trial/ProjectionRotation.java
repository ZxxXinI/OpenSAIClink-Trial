package org.opensaiclink.trial;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;
import android.view.Surface;

/** Temporarily locks rotation and saves the original settings for recovery. */
public final class ProjectionRotation {
    private static final int AUTO = 0;
    private static final String APPLIED_ROTATION = "applied_rotation";
    private static final String PENDING_ROTATION = "pending_rotation";
    private static volatile int manualOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    private ProjectionRotation() {}

    public static synchronized void recover(Context context) {
        if (!Status.capturing && !Status.usbOpen) restore(context);
    }

    public static synchronized void apply(Context context) {
        if (state(context).getBoolean("landscape", true) && Settings.System.canWrite(context)) {
            lock(context, Surface.ROTATION_90);
        }
    }

    static synchronized boolean toggle(Context context) {
        if (!Status.capturing || !Settings.System.canWrite(context)) return false;

        Display display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) return false;

        int currentRotation = display.getRotation();
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        boolean quarterTurn = currentRotation == Surface.ROTATION_90 || currentRotation == Surface.ROTATION_270;
        boolean naturalLandscape = landscape != quarterTurn;
        int target = landscape == naturalLandscape ? Surface.ROTATION_90 : Surface.ROTATION_0;
        if (!lock(context, target)) return false;
        manualOrientation = landscape ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        SessionController.publish();
        return true;
    }

    static int activityOrientation(int defaultOrientation) {
        int requested = manualOrientation;
        return Status.capturing && requested != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ? requested : defaultOrientation;
    }

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences("trial", Context.MODE_PRIVATE);
    }

    private static boolean lock(Context context, int rotation) {
        SharedPreferences saved = state(context);
        ContentResolver settings = context.getContentResolver();
        try {
            SharedPreferences.Editor record = saved.edit().putInt(PENDING_ROTATION, rotation);
            if (!saved.getBoolean("restore_pending", false)) {
                record.putInt("restore_auto", Settings.System.getInt(settings, Settings.System.ACCELEROMETER_ROTATION, 0))
                        .putInt("restore_rotation", Settings.System.getInt(settings, Settings.System.USER_ROTATION, 0))
                        .putBoolean("restore_pending", true);
            }
            if (!record.commit()) throw new IllegalStateException("Cannot save the original rotation");
            write(settings, Settings.System.USER_ROTATION, rotation);
            write(settings, Settings.System.ACCELEROMETER_ROTATION, AUTO);
            saved.edit().putInt(APPLIED_ROTATION, rotation).remove(PENDING_ROTATION).apply();
            Trace.log("ROTATION_APPLIED", "Temporary rotation " + rotation);
            return true;
        } catch (RuntimeException e) {
            Trace.log("ROTATION_ERROR", e.toString());
            restore(context);
            return false;
        }
    }

    public static synchronized void restore(Context context) {
        if (manualOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            manualOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
            SessionController.publish();
        }
        SharedPreferences saved = state(context);
        if (!saved.getBoolean("restore_pending", false)) return;
        if (!Settings.System.canWrite(context)) {
            Trace.log("ROTATION_RESTORE_PENDING", "Permission unavailable; original settings retained for recovery");
            return;
        }
        try {
            ContentResolver settings = context.getContentResolver();
            int rotation = Settings.System.getInt(settings, Settings.System.USER_ROTATION, -1);
            // Old versions saved the original settings and always applied ROTATION_90.
            if (rotation >= 0 && (rotation == saved.getInt(APPLIED_ROTATION, Surface.ROTATION_90)
                    || rotation == saved.getInt(PENDING_ROTATION, -1))) {
                write(settings, Settings.System.USER_ROTATION, saved.getInt("restore_rotation", 0));
            }
            if (Settings.System.getInt(settings, Settings.System.ACCELEROMETER_ROTATION, -1) == AUTO) {
                write(settings, Settings.System.ACCELEROMETER_ROTATION, saved.getInt("restore_auto", 0));
            }
            saved.edit().remove(APPLIED_ROTATION).remove(PENDING_ROTATION).putBoolean("restore_pending", false).commit();
            Trace.log("ROTATION_RESTORED", "Temporary rotation released");
        } catch (RuntimeException e) {
            Trace.log("ROTATION_RESTORE_ERROR", e.toString());
        }
    }

    private static void write(ContentResolver settings, String key, int value) {
        if (!Settings.System.putInt(settings, key, value)) {
            throw new IllegalStateException("Cannot write " + key);
        }
    }
}
