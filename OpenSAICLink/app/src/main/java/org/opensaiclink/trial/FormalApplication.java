package org.opensaiclink.trial;
import android.app.Application;
public final class FormalApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        Trace.init(this);
        ProjectionRotation.recover(this);
        ProjectionBrightness.recover(this);
        try{
            new SettingsStore(this);
        }
        catch(RuntimeException e){
            Trace.log("MIGRATION_ERROR",e.toString());
        }
    }
}
