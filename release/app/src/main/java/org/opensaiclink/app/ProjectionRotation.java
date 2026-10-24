package org.opensaiclink.app;

import android.content.*;
import android.provider.Settings;

/** Temporary user-authorized landscape setting with a persisted crash recovery marker. */
public final class ProjectionRotation {
    private ProjectionRotation(){}
    public static synchronized void recover(Context context){if(!Status.capturing && !Status.usbOpen)restore(context);}
    public static synchronized void apply(Context context){
        SharedPreferences prefs=context.getSharedPreferences(AppSettings.FILE,Context.MODE_PRIVATE);
        if(!prefs.getBoolean(AppSettings.LANDSCAPE,true))return;
        if(!Settings.System.canWrite(context)){Trace.log("ROTATION_PERMISSION","User must allow modify-system-settings for maps to rotate automatically");return;}
        ContentResolver resolver=context.getContentResolver();
        int oldAuto=Settings.System.getInt(resolver,Settings.System.ACCELEROMETER_ROTATION,0);
        int oldRotation=Settings.System.getInt(resolver,Settings.System.USER_ROTATION,0);
        prefs.edit().putInt(AppSettings.RESTORE_AUTO,oldAuto)
                .putInt(AppSettings.RESTORE_ROTATION,oldRotation)
                .putBoolean(AppSettings.RESTORE_PENDING,true).commit();
        try{
            if(!Settings.System.putInt(resolver,Settings.System.USER_ROTATION,1)
                    || !Settings.System.putInt(resolver,Settings.System.ACCELEROMETER_ROTATION,0))throw new IllegalStateException("Rotation settings not accepted");
            Trace.log("ROTATION_APPLIED","Temporary landscape; previous auto="+oldAuto+" rotation="+oldRotation);
        }catch(Exception e){
            try{
                if(Settings.System.getInt(resolver,Settings.System.USER_ROTATION,-1)==1)Settings.System.putInt(resolver,Settings.System.USER_ROTATION,oldRotation);
                if(Settings.System.getInt(resolver,Settings.System.ACCELEROMETER_ROTATION,-1)==0)Settings.System.putInt(resolver,Settings.System.ACCELEROMETER_ROTATION,oldAuto);
                prefs.edit().putBoolean(AppSettings.RESTORE_PENDING,false).commit();
            }catch(Exception ignored){}
            Trace.log("ROTATION_ERROR",e.toString());
        }
    }
    public static synchronized void restore(Context context){
        SharedPreferences prefs=context.getSharedPreferences(AppSettings.FILE,Context.MODE_PRIVATE);
        if(!prefs.getBoolean(AppSettings.RESTORE_PENDING,false))return;
        if(!Settings.System.canWrite(context)){Trace.log("ROTATION_RESTORE_PENDING","Permission unavailable; original settings retained for recovery");return;}
        try{
            ContentResolver resolver=context.getContentResolver();
            if(Settings.System.getInt(resolver,Settings.System.ACCELEROMETER_ROTATION,-1)==0
                    && Settings.System.getInt(resolver,Settings.System.USER_ROTATION,-1)==1){
                boolean rotation=Settings.System.putInt(resolver,Settings.System.USER_ROTATION,
                        prefs.getInt(AppSettings.RESTORE_ROTATION,0));
                boolean auto=Settings.System.putInt(resolver,Settings.System.ACCELEROMETER_ROTATION,
                        prefs.getInt(AppSettings.RESTORE_AUTO,0));
                if(!rotation || !auto)throw new IllegalStateException("Cannot restore rotation");
                Trace.log("ROTATION_RESTORED","Original screen rotation restored");
            }else Trace.log("ROTATION_RESTORE_SKIPPED","Settings changed since trial; user choice retained");
            prefs.edit().putBoolean(AppSettings.RESTORE_PENDING,false).commit();
        }catch(Exception e){Trace.log("ROTATION_RESTORE_ERROR",e.toString());}
    }
}
