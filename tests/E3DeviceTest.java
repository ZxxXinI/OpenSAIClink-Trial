package org.opensaiclink.trial;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.provider.Settings;
import android.view.WindowManager;

/** Test APK only: runs in the target app process on the isolated emulator. */
public final class E3DeviceTest extends Instrumentation {
    private int checks;
    private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private void sleep(long ms){SystemClock.sleep(ms);}
    private void applyRotation(Context c){ProjectionRotation.apply(c);sleep(500);}
    private void restoreRotation(Context c){ProjectionRotation.restore(c);sleep(500);}
    private void awaitUp(int count){
        long deadline=SystemClock.uptimeMillis()+5000;
        while(PatternActivity.touchUp<count && SystemClock.uptimeMillis()<deadline)sleep(50);
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();
        try{
            Trace.init(c);
            check(Settings.System.canWrite(c),"isolated emulator settings grant");
            Settings.System.putInt(c.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1);
            Settings.System.putInt(c.getContentResolver(),Settings.System.USER_ROTATION,0);
            sleep(500);
            c.getSharedPreferences("trial",0).edit().putBoolean("landscape",true).putBoolean("touch",true).commit();
            applyRotation(c);
            check(Settings.System.getInt(c.getContentResolver(),Settings.System.USER_ROTATION)==1,"landscape applied");
            check(Settings.System.getInt(c.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION)==0,"auto rotation temporarily locked");
            restoreRotation(c);
            check(Settings.System.getInt(c.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION)==1,"original auto restored");
            check(Settings.System.getInt(c.getContentResolver(),Settings.System.USER_ROTATION)==0,"original rotation restored");
            applyRotation(c);
            Settings.System.putInt(c.getContentResolver(),Settings.System.USER_ROTATION,3);
            sleep(500);restoreRotation(c);
            check(Settings.System.getInt(c.getContentResolver(),Settings.System.USER_ROTATION)==3,"user change retained");
            Settings.System.putInt(c.getContentResolver(),Settings.System.USER_ROTATION,0);
            sleep(500);applyRotation(c);ProjectionRotation.recover(c);sleep(500);
            check(Settings.System.getInt(c.getContentResolver(),Settings.System.USER_ROTATION)==0,"crash recovery marker restores original");
            applyRotation(c);
            Intent launch=new Intent(c,PatternActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(launch);
            long deadline=SystemClock.uptimeMillis()+8000;
            while(!Status.touchReady && SystemClock.uptimeMillis()<deadline)sleep(100);
            check(Status.touchReady,"actual accessibility service is connected");
            sleep(500);
            Rect b=activity.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            Status.width=1280;Status.height=480;Status.contentWidth=b.width();Status.contentHeight=b.height();
            Status.capturing=true;Status.usbOpen=true;Status.configured=true;Status.huPaused=false;
            PatternActivity.touchDown=0;PatternActivity.touchMoves=0;PatternActivity.touchUp=0;
            RemoteTouchService.deliver(TouchInput.parse("motionevent3 640 240 0 50 down"));sleep(120);
            RemoteTouchService.deliver(TouchInput.parse("motionevent3 640 240 0 50 up"));awaitUp(1);
            check(PatternActivity.touchDown==1 && PatternActivity.touchUp==1,"real injected tap reached activity");
            RemoteTouchService.deliver(TouchInput.parse("motionevent3 640 240 0 50 down"));sleep(100);
            for(int x=650;x<=800;x+=30){RemoteTouchService.deliver(TouchInput.parse("motionevent3 "+x+" 240 0 50 move"));sleep(60);}
            RemoteTouchService.deliver(TouchInput.parse("motionevent3 800 240 0 50 up"));awaitUp(2);
            check(PatternActivity.touchDown==2 && PatternActivity.touchUp==2 && PatternActivity.touchMoves>0,"continued stroke produces real drag");
            int before=PatternActivity.touchDown;
            RemoteTouchService.deliver(new TouchInput.Event(0,0,240));sleep(150);
            check(PatternActivity.touchDown==before,"letterbox tap does not reach activity");
            Status.huPaused=true;
            RemoteTouchService.deliver(new TouchInput.Event(0,640,240));sleep(150);
            check(PatternActivity.touchDown==before,"paused car source cannot inject");
            Status.huPaused=false;Status.capturing=false;
            RemoteTouchService.deliver(new TouchInput.Event(0,640,240));sleep(150);
            check(PatternActivity.touchDown==before,"stopped projection cannot inject");
            Status.capturing=true;
            c.getSharedPreferences("trial",0).edit().putBoolean("touch",false).commit();
            RemoteTouchService.deliver(new TouchInput.Event(0,640,240));sleep(150);
            check(PatternActivity.touchDown==before,"disabled user toggle cannot inject");
            result.putString("result","PASS "+checks+" Android runtime checks; tap and drag injected through actual service");
            result.putInt("down",PatternActivity.touchDown);result.putInt("move",PatternActivity.touchMoves);result.putInt("up",PatternActivity.touchUp);
            runOnMainSync(activity::finish);
        }catch(Throwable e){result.putString("failure",e.toString());}
        finally{
            Status.capturing=false;Status.usbOpen=false;Status.configured=false;Status.huPaused=false;
            RemoteTouchService.releaseCurrent();ProjectionRotation.restore(c);
            c.getSharedPreferences("trial",0).edit().putBoolean("touch",true).commit();
        }
        finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
