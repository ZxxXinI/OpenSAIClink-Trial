package org.opensaiclink.app;

import android.app.Activity;
import android.content.*;
import android.widget.Toast;

public final class AppLauncher {
    private AppLauncher(){}
    public static void open(Activity activity,String packageName){
        if(!Status.capturing){Toast.makeText(activity,R.string.capture_first,Toast.LENGTH_LONG).show();return;}
        Intent launch=activity.getPackageManager().getLaunchIntentForPackage(packageName);
        if(launch==null){Toast.makeText(activity,R.string.car_app_missing,Toast.LENGTH_LONG).show();return;}
        try{
            if(Status.usbOpen)activity.startService(new Intent(activity,LinkService.class).setAction(LinkService.MAP).putExtra("package",packageName));
            activity.startActivity(launch);Trace.log("CAR_APP_LAUNCHED",packageName);
        }catch(ActivityNotFoundException|SecurityException e){
            Trace.log("CAR_APP_ERROR",e.toString());Toast.makeText(activity,R.string.car_app_missing,Toast.LENGTH_LONG).show();
        }
    }
}
