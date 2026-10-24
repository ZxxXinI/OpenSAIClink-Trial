package org.opensaiclink.trial;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.*;
import android.view.WindowManager;
/** Test APK only. No test-only exported entry point in the delivered app. */
public final class E5DeviceTest extends Instrumentation {
    private int checks;private Activity activity;
    private void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private void sleep(long ms){SystemClock.sleep(ms);}
    private Rect bounds(){return activity.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();}
    private void event(int action,int id,int x,int y){RemoteTouchService.deliver(new TouchInput.Event(action,x,y,id));RemoteTouchService.deliverCommit();sleep(70);}
    private void awaitUp(int n){long end=SystemClock.uptimeMillis()+4000;while(PatternActivity.touchUp<n && SystemClock.uptimeMillis()<end)sleep(50);}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){
        Context c=getTargetContext();Bundle result=new Bundle();
        try{
            Trace.init(c);c.getSharedPreferences("trial",0).edit().putBoolean("force_landscape",false).putBoolean("touch",true).putBoolean("multitouch",true).commit();
            activity=startActivitySync(new Intent(c,PatternActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long end=SystemClock.uptimeMillis()+10000;while(!Status.touchReady && SystemClock.uptimeMillis()<end)sleep(100);
            check(Status.touchReady,"actual accessibility service connected");
            runOnMainSync(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));sleep(1000);
            check(bounds().height()>bounds().width(),"fixture really requests and displays portrait");
            Status.capturing=true;c.getSharedPreferences("trial",0).edit().putBoolean("force_landscape",true).commit();RemoteTouchService.refreshOrientation();sleep(1800);
            boolean rotated=bounds().width()>bounds().height();result.putBoolean("portraitOverride",rotated);
            result.putBoolean("overlayCreated",Status.rotationOverlay);
            c.getSharedPreferences("trial",0).edit().putBoolean("force_landscape",false).commit();RemoteTouchService.refreshOrientation();sleep(1000);
            check(!Status.rotationOverlay && bounds().height()>bounds().width(),"overlay removed and portrait restored");
            // A launcher-style portrait fixture is tested independently of touch coordinates.
            runOnMainSync(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));sleep(1000);
            Rect b=bounds();check(b.width()>b.height(),"landscape touch fixture");
            Status.width=1280;Status.height=480;Status.contentWidth=b.width();Status.contentHeight=b.height();Status.fillVideo=true;
            Status.usbOpen=true;Status.configured=true;Status.huPaused=false;
            PatternActivity.touchDown=PatternActivity.touchMoves=PatternActivity.touchUp=0;
            PatternActivity.pointerDown=PatternActivity.pointerUp=PatternActivity.maxPointers=0;PatternActivity.pinchScale=1;
            event(0,0,500,240);event(0,1,780,240);
            for(int step=1;step<=8;step++){event(2,0,500-step*20,240);event(2,1,780+step*20,240);}
            event(1,0,340,240);event(2,1,970,240);event(1,1,970,240);awaitUp(1);
            check(PatternActivity.maxPointers==2 && PatternActivity.pointerDown==1 && PatternActivity.pointerUp==1,"actual two-pointer Android stream");
            check(PatternActivity.touchDown==1 && PatternActivity.touchUp==1,"primary lifts first without cancelling secondary");
            check(PatternActivity.pinchScale>1.5,"actual ScaleGestureDetector pinch zoom");
            result.putFloat("pinchScale",PatternActivity.pinchScale);
            PatternActivity.pinchScale=1;
            event(0,0,360,240);event(0,1,920,240);
            for(int step=1;step<=14;step++){
                RemoteTouchService.deliver(new TouchInput.Event(2,360+step*10,240,0));
                RemoteTouchService.deliver(new TouchInput.Event(2,920-step*10,240,1));
                RemoteTouchService.deliverCommit();sleep(15);
            }
            sleep(300);event(1,1,780,240);event(2,0,510,240);event(1,0,510,240);awaitUp(2);
            check(PatternActivity.touchUp==2 && PatternActivity.pinchScale<.8,"fast paired moves zoom in, secondary lifts first");
            result.putFloat("pinchScaleIn",PatternActivity.pinchScale);
            event(0,0,100,300);event(1,0,100,300);awaitUp(3);
            check(PatternActivity.touchUp==3 && Math.abs(PatternActivity.lastTouchX-Math.round(100f*b.width()/1280))<3,"filled former black bar maps to actual phone coordinate");
            int ups=PatternActivity.touchUp;event(0,0,450,240);event(0,1,830,240);Status.huPaused=true;RemoteTouchService.releaseCurrent();awaitUp(ups+1);
            check(PatternActivity.touchUp==ups+1,"pause releases both held pointers");
            int downs=PatternActivity.touchDown;event(0,0,640,240);check(PatternActivity.touchDown==downs,"paused input blocked");
            Status.huPaused=false;event(0,0,640,240);event(1,0,640,240);awaitUp(ups+2);
            check(PatternActivity.touchUp==ups+2,"single tap recovers after two-pointer release");
            Status.capturing=false;RemoteTouchService.releaseCurrent();sleep(200);downs=PatternActivity.touchDown;event(0,0,640,240);
            check(PatternActivity.touchDown==downs,"stopped capture blocks input");
            result.putString("result","PASS "+checks+" E5 runtime checks");
        }catch(Throwable e){result.putString("failure",e.toString());}
        finally{
            Status.capturing=false;Status.usbOpen=false;Status.configured=false;Status.huPaused=false;Status.fillVideo=false;
            RemoteTouchService.releaseCurrent();c.getSharedPreferences("trial",0).edit().putBoolean("force_landscape",true).commit();
            if(activity!=null)runOnMainSync(activity::finish);
        }
        finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
