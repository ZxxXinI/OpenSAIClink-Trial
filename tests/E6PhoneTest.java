package org.opensaiclink.trial;
import android.app.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
/** Phone-only test APK. It grants no accessibility or capture permission by itself. */
public final class E6PhoneTest extends Instrumentation {
 private Context c;private UiAutomation ui;private int checks;private Bundle result=new Bundle();
 private Activity main;private boolean forceBefore,returnBefore,skipSlots;private final Map<String,String> slots=new HashMap<>();
 private void sleep(long ms){SystemClock.sleep(ms);}
 private void check(boolean ok,String label){checks++;Trace.log("E6_CHECK",label+"="+ok);if(!ok)throw new AssertionError(label);}
 private interface Ready {boolean get();}
 private void waitFor(Ready ready,String label,long ms){long end=SystemClock.uptimeMillis()+ms;while(!ready.get() && SystemClock.uptimeMillis()<end)sleep(80);check(ready.get(),label);}
 private AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text){
  if(n==null)return null;CharSequence t=n.getText(),d=n.getContentDescription();
  if(text.contentEquals(t==null?"":t) || text.contentEquals(d==null?"":d) || (d!=null && d.toString().startsWith(text+". ")))return n;
  for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo f=find(n.getChild(i),text);if(f!=null)return f;}return null;
 }
 private AccessibilityNodeInfo find(String text){
  for(AccessibilityWindowInfo w:ui.getWindows()){AccessibilityNodeInfo f=find(w.getRoot(),text);if(f!=null)return f;}
  return find(ui.getRootInActiveWindow(),text);
 }
 private void screenshot(String name)throws Exception{
  Bitmap b=ui.takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}
 }
 private void click(String text)throws Exception{
  long end=SystemClock.uptimeMillis()+6000;AccessibilityNodeInfo n;
  while((n=find(text))==null && SystemClock.uptimeMillis()<end)sleep(100);
  if(n==null)throw new AssertionError("Missing control "+text);
  Rect r=new Rect();n.getBoundsInScreen(r);check(!r.isEmpty(),"visible control "+text);
  float scale=Math.min((float)Status.width/Status.contentWidth,(float)Status.height/Status.contentHeight);
  int x,y;
  if(Status.fillVideo){x=Math.round((float)r.centerX()*Status.width/Status.contentWidth);y=Math.round((float)r.centerY()*Status.height/Status.contentHeight);}
  else{x=Math.round((Status.width-Status.contentWidth*scale)/2+r.centerX()*scale);y=Math.round((Status.height-Status.contentHeight*scale)/2+r.centerY()*scale);}
  RemoteTouchService.deliver(new TouchInput.Event(0,x,y));RemoteTouchService.deliverCommit();sleep(90);
  RemoteTouchService.deliver(new TouchInput.Event(1,x,y));RemoteTouchService.deliverCommit();sleep(500);
 }
 private void consent(){
  long end=SystemClock.uptimeMillis()+25000;
  while(!Status.capturing && SystemClock.uptimeMillis()<end){
   for(AccessibilityWindowInfo w:ui.getWindows()){
    AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;String pkg=String.valueOf(root.getPackageName());
    if(!pkg.contains("systemui") && !pkg.contains("securitycenter") && !pkg.equals("android"))continue;
    for(String label:new String[]{"\u7acb\u5373\u5f00\u59cb","\u5f00\u59cb\u5f55\u5236","\u5f00\u59cb\u5171\u4eab","\u5141\u8bb8","\u5171\u4eab\u5c4f\u5e55","Start now","Start recording","Share screen"}){
     AccessibilityNodeInfo n=find(root,label);if(n!=null && n.isEnabled()){
      Trace.log("E6_CONSENT_BUTTON",label);if(!n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
       Rect r=new Rect();n.getBoundsInScreen(r);long now=SystemClock.uptimeMillis();
       MotionEvent d=MotionEvent.obtain(now,now,0,r.centerX(),r.centerY(),0);d.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);ui.injectInputEvent(d,true);d.recycle();
       MotionEvent u=MotionEvent.obtain(now,now+70,1,r.centerX(),r.centerY(),0);u.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);ui.injectInputEvent(u,true);u.recycle();
      }sleep(900);break;
     }
    }
   }sleep(150);
  }
 }
 private void resetPattern(){PatternActivity.touchDown=PatternActivity.touchUp=PatternActivity.touchMoves=PatternActivity.maxPointers=0;PatternActivity.pinchScale=1;}
 private float replay(String[] commands,int[] delays,boolean expansion)throws Exception{
  resetPattern();boolean lifting=false;float before=1;
  for(int i=0;i<commands.length;i++){
   TouchInput.Event e=TouchInput.parse(commands[i]);sleep(Math.min(150,delays[i]));
   if(e.action==TouchInput.UP && !lifting){sleep(120);before=PatternActivity.pinchScale;lifting=true;}
   RemoteTouchService.deliver(e);RemoteTouchService.deliverCommit();
  }
  waitFor(()->PatternActivity.touchUp==1,"recorded gesture final UP",4000);
  check(PatternActivity.maxPointers==2,"recorded gesture contains actual two-pointer motion");
  float after=PatternActivity.pinchScale;Trace.log("E6_SCALE","beforeUp="+before+" afterUp="+after+" moves="+PatternActivity.touchMoves);check(Math.abs(after-before)<.15,"bad UP does not collapse scale");
  check(expansion?after>1.05:after<.95,expansion?"real car expand retained":"real car shrink retained");return after;
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);skipSlots="gestures".equals(args.getString("phase","all"));start();}
 @Override public void onStart(){
  c=getTargetContext();SharedPreferences prefs=c.getSharedPreferences("trial",0);forceBefore=prefs.getBoolean("force_landscape",false);returnBefore=prefs.getBoolean("return_overlay",true);
  for(int i=1;i<=3;i++)slots.put(CarHomeActivity.shortcutKey(i),prefs.getString(CarHomeActivity.shortcutKey(i),null));
  try{
   Trace.init(c);waitFor(()->Status.touchReady,"user-enabled accessibility connected",15000);
   ui=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;ui.setServiceInfo(info);
   ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
   try(ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("am start -f 0x10008000 -n org.opensaiclink.trial/.MainActivity"))){byte[] buf=new byte[512];while(input.read(buf)!=-1){}}
   main=monitor.waitForActivityWithTimeout(6000);check(main!=null,"main activity starts");removeMonitor(monitor);
   Status.touchReady=false;sleep(1300);Field capture=MainActivity.class.getDeclaredField("captureButton");capture.setAccessible(true);Field local=MainActivity.class.getDeclaredField("localButton");local.setAccessible(true);
   check(!((Button)capture.get(main)).isEnabled() && !((Button)local.get(main)).isEnabled(),"both capture buttons gated when service is disconnected");
   Method request=MainActivity.class.getDeclaredMethod("requestCapture",boolean.class);request.setAccessible(true);
   runOnMainSync(()->{try{request.invoke(main,true);}catch(Exception e){throw new RuntimeException(e);}});sleep(200);check(!Status.capturing,"direct request also blocked by permission gate");
   Status.touchReady=true;prefs.edit().putBoolean("return_overlay",true).putBoolean("fill_display",true).commit();
   Trace.log("E6_NEEDS_CONSENT","Opening actual Android screen share prompt");
   runOnMainSync(()->{try{request.invoke(main,true);}catch(Exception e){throw new RuntimeException(e);}});consent();
   waitFor(()->Status.capturing && Status.encoded.get()>10,"real K80 screen capture running",12000);sleep(1500);
   waitFor(()->Status.carHomeVisible,"car home opened",5000);check(Status.fillVideo,"GPU fill is actually active");
   for(int i=1;i<=3;i++)check(find(c.getString(R.string.car_slot_add,i))!=null,"third row slot "+i+" visible");screenshot("e6-nine-tile-home");
   Status.usbOpen=true;Status.configured=true;Trace.log("E6_TEST_INPUT","Local replay only; no physical car connection");
   if(!skipSlots)for(int i=1;i<=3;i++){
    click(c.getString(R.string.car_slot_add,i));sleep(800);
    check(find(c.getString(R.string.car_pick_favorite))!=null,"slot picker opens");
    AccessibilityNodeInfo choice=null;long loadEnd=SystemClock.uptimeMillis()+8000;
    while(choice==null && SystemClock.uptimeMillis()<loadEnd){for(AccessibilityWindowInfo w:ui.getWindows()){choice=firstListItem(w.getRoot());if(choice!=null)break;}if(choice==null)sleep(100);}
    if(choice==null)throw new AssertionError("Loaded app picker has no selectable items");
    check(choice.performAction(AccessibilityNodeInfo.ACTION_CLICK),"picker item receives click");sleep(500);
    check(!prefs.getString(CarHomeActivity.shortcutKey(i),"").isEmpty(),"independent slot "+i+" saved");
   }
   ActivityMonitor patternMonitor=addMonitor(PatternActivity.class.getName(),null,false);
   c.startActivity(new Intent(c,PatternActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Activity pattern=patternMonitor.waitForActivityWithTimeout(4000);removeMonitor(patternMonitor);check(pattern!=null,"pattern activity starts");sleep(1200);
   runOnMainSync(()->((android.view.ViewGroup)pattern.findViewById(android.R.id.content)).getChildAt(0).setOnTouchListener((view,event)->{Trace.log("E6_POINTER",event.toString());return false;}));
   result.putFloat("recordedExpand",replay(E6RecordedTouch.EXPAND,E6RecordedTouch.EXPAND_DELAY,true));
   result.putFloat("recordedShrink",replay(E6RecordedTouch.SHRINK,E6RecordedTouch.SHRINK_DELAY,false));
   screenshot("e6-pattern-with-home");sleep(2000);
   check(find(c.getString(R.string.car_return))!=null,"return overlay visible on phone");click(c.getString(R.string.car_return));waitFor(()->Status.carHomeVisible,"overlay tap returns to car home",4000);
   c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));sleep(1600);screenshot("e6-phone-launcher-capture");
   check(Status.contentHeight>Status.contentWidth,"phone launcher keeps its normal portrait layout");
   AccessibilityNodeInfo icon=null;for(AccessibilityWindowInfo w:ui.getWindows()){AccessibilityNodeInfo r=w.getRoot();if(r==null)continue;for(AccessibilityNodeInfo n:r.findAccessibilityNodeInfosByText("SAICLink")){icon=n;break;}if(icon!=null)break;}
   if(icon!=null){Rect r=new Rect();icon.getBoundsInScreen(r);result.putString("launcherIconBounds",r.toString());Trace.log("E6_LAUNCHER_BOUNDS",r.toString());}
   click(c.getString(R.string.car_return));waitFor(()->Status.carHomeVisible,"return from system launcher",4000);
   StatusBarNotification[] notes=c.getSystemService(NotificationManager.class).getActiveNotifications();boolean publicNote=false;
   for(StatusBarNotification n:notes)if(n.getId()==10)publicNote=n.getNotification().visibility==Notification.VISIBILITY_PUBLIC;
   check(publicNote,"own projection notification marked public");
   Status.usbOpen=false;Status.configured=false;c.stopService(new Intent(c,LinkService.class));waitFor(()->!Status.capturing,"stop capture",6000);sleep(1000);
   check(!Status.rotationOverlay && find(c.getString(R.string.car_return))==null,"stop removes orientation and return overlays");
   result.putString("result","PASS "+checks+" K80 E6 checks");
  }catch(Throwable e){Trace.log("E6_TEST_FAILURE",e.toString());result.putString("failure",e.toString());}
  finally{
   Status.usbOpen=false;Status.configured=false;Status.touchReady=true;c.stopService(new Intent(c,LinkService.class));
   SharedPreferences.Editor edit=prefs.edit().putBoolean("force_landscape",forceBefore).putBoolean("return_overlay",returnBefore);
   for(Map.Entry<String,String> e:slots.entrySet()){if(e.getValue()==null)edit.remove(e.getKey());else edit.putString(e.getKey(),e.getValue());}edit.commit();
   RemoteTouchService.releaseCurrent();sleep(800);
  }
  finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
 }
 private AccessibilityNodeInfo firstListItem(AccessibilityNodeInfo n){
  if(n==null)return null;if("android.widget.ListView".contentEquals(n.getClassName()) && n.getChildCount()>0)return n.getChild(0);
  for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo item=firstListItem(n.getChild(i));if(item!=null)return item;}return null;
 }
 private boolean scroll(AccessibilityNodeInfo n){
  if(n==null)return false;if(n.isScrollable())return n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
  for(int i=0;i<n.getChildCount();i++)if(scroll(n.getChild(i)))return true;return false;
 }
}
