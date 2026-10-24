package org.opensaiclink.trial;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.io.FileOutputStream;
import android.view.accessibility.AccessibilityNodeInfo;
/** Temporary diagnostic APK. Never shipped inside the main APK. */
public final class E6Probe extends Instrumentation {
 private volatile int clicks;private View button;private RemoteTouchService service;
 private void sleep(long ms){SystemClock.sleep(ms);}
 private void info(String stage){
  Rect bounds=service.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
  Display display=service.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);Point p=new Point();display.getRealSize(p);
  Trace.log("E6_PROBE",stage+" wm="+bounds+" real="+p+" rotation="+display.getRotation()+" resource="+service.getResources().getConfiguration());
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){
  Bundle result=new Bundle();Context c=getTargetContext();boolean original=c.getSharedPreferences("trial",0).getBoolean("force_landscape",true);
  try{
   Trace.init(c);long end=SystemClock.uptimeMillis()+15000;while(!Status.touchReady && SystemClock.uptimeMillis()<end)sleep(100);
   Field active=RemoteTouchService.class.getDeclaredField("active");active.setAccessible(true);service=(RemoteTouchService)active.get(null);if(service==null)throw new AssertionError("service not connected");
   Status.capturing=true;Status.usbOpen=true;Status.configured=true;Status.huPaused=false;Status.fillVideo=true;Status.width=1280;Status.height=480;
   c.getSharedPreferences("trial",0).edit().putBoolean("force_landscape",true).commit();RemoteTouchService.refreshOrientation();
   c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));sleep(1800);info("launcher");
   Display display=service.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);Point p=new Point();display.getRealSize(p);
   Status.contentWidth=p.x;Status.contentHeight=p.y;
   runOnMainSync(()->{
    Button b=new Button(service);b.setText("OpenSAICLink test");b.setBackgroundColor(0xffff44cc);b.setOnClickListener(v->clicks++);button=b;
    WindowManager.LayoutParams lp=new WindowManager.LayoutParams(600,160,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
    lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=p.x/2;lp.y=p.y/2;lp.setTitle("OpenSAICLink E6 test target");service.getSystemService(WindowManager.class).addView(button,lp);
   });sleep(700);info("with-test-target");int[] at=new int[2];runOnMainSync(()->button.getLocationOnScreen(at));
   int px=at[0]+button.getWidth()/2,py=at[1]+button.getHeight()/2;
   Trace.log("E6_PROBE","target="+px+","+py+" physical="+p+" capture="+Status.contentWidth+"x"+Status.contentHeight);
   RemoteTouchService.deliver(new TouchInput.Event(0,px*1280/p.x,py*480/p.y));RemoteTouchService.deliverCommit();sleep(180);
   RemoteTouchService.deliver(new TouchInput.Event(1,px*1280/p.x,py*480/p.y));RemoteTouchService.deliverCommit();sleep(800);
   result.putInt("launcherOverlayClicks",clicks);result.putString("display",p.toString());
   runOnMainSync(()->service.getSystemService(WindowManager.class).removeViewImmediate(button));button=null;
   UiAutomation automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
   android.graphics.Bitmap screenshot=automation.takeScreenshot();
   try(FileOutputStream file=new FileOutputStream(new java.io.File(c.getFilesDir(),"e6-launcher-probe.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}
   AccessibilityNodeInfo root=automation.getRootInActiveWindow();java.util.List<AccessibilityNodeInfo> nodes=root==null?java.util.Collections.emptyList():root.findAccessibilityNodeInfosByText("SAICLink");
   for(AccessibilityNodeInfo node:nodes){
    Rect r=new Rect();node.getBoundsInScreen(r);if(r.isEmpty() || r.centerX()<0 || r.centerY()<0)continue;
    Trace.log("E6_PROBE","launcher-node="+r+" text="+node.getText()+" desc="+node.getContentDescription());
    RemoteTouchService.deliver(new TouchInput.Event(0,r.centerX()*1280/p.x,r.centerY()*480/p.y));RemoteTouchService.deliverCommit();sleep(120);
    RemoteTouchService.deliver(new TouchInput.Event(1,r.centerX()*1280/p.x,r.centerY()*480/p.y));RemoteTouchService.deliverCommit();sleep(900);
    AccessibilityNodeInfo after=automation.getRootInActiveWindow();result.putString("launcherTapPackage",after==null?"null":String.valueOf(after.getPackageName()));break;
   }

   c.getSharedPreferences("trial",0).edit().putBoolean("force_landscape",false).commit();RemoteTouchService.refreshOrientation();sleep(1000);info("orientation-removed");
  }catch(Throwable e){result.putString("failure",e.toString());}
  finally{
   if(button!=null)runOnMainSync(()->service.getSystemService(WindowManager.class).removeViewImmediate(button));
   Status.capturing=false;Status.usbOpen=false;Status.configured=false;Status.fillVideo=false;
   c.getSharedPreferences("trial",0).edit().putBoolean("force_landscape",original).commit();RemoteTouchService.releaseCurrent();
  }
  finish(Activity.RESULT_OK,result);
 }
}
