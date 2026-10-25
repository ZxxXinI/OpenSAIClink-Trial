package org.opensaiclink.trial;
import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.graphics.*;
import android.os.*;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.*;
/** User-enabled gesture relay. No window content or accessibility events are read. */
public final class RemoteTouchService extends AccessibilityService {
    private static volatile RemoteTouchService active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final TouchFrames desired=new TouchFrames();
    private final ArrayDeque<TouchFrames.Frame> pending=new ArrayDeque<>();
    private final TreeMap<Integer,Pointer> strokes=new TreeMap<>();
    private boolean busy,releasePending,connected;
    private int generation;
    private ReturnOverlay returns;
    private String lastGeometry="";
    private static final class Pointer {
        final GestureDescription.StrokeDescription stroke;final float x,y;
        Pointer(GestureDescription.StrokeDescription stroke,float x,float y){this.stroke=stroke;this.x=x;this.y=y;}
    }
    private final Runnable expire=this::release,flushFallback=this::flush;
    private final Runnable maintain=new Runnable(){@Override public void run(){
        refreshRotation();if(!allowed())release();main.postDelayed(this,500);
    }};
    @Override protected void onServiceConnected(){
        connected=true;active=this;Status.touchReady=true;SessionController.touch(true);Trace.init(this);returns=new ReturnOverlay(this);
        main.removeCallbacks(maintain);main.post(maintain);Trace.log("TOUCH_SERVICE","User enabled two-pointer relay");
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent ignored){}
    @Override public void onInterrupt(){release();}
    @Override public void onDestroy(){
        connected=false;main.removeCallbacks(maintain);release();if(returns!=null)returns.setEnabled(false);
        active=null;Status.touchReady=false;SessionController.touch(false);super.onDestroy();
    }
    public static void deliver(TouchInput.Event event){RemoteTouchService s=active;if(s!=null)s.main.post(()->s.accept(event));}
    public static void deliverCommit(){RemoteTouchService s=active;if(s!=null)s.main.post(s::flush);}
    public static void releaseCurrent(){RemoteTouchService s=active;if(s!=null)s.main.post(()->{s.release();s.refreshRotation();});}
    public static void refreshOrientation(){RemoteTouchService s=active;if(s!=null)s.main.post(s::refreshRotation);}
    public static void back(){RemoteTouchService s=active;if(s!=null)s.main.post(()->{if(s.allowed()){s.release();s.performGlobalAction(GLOBAL_ACTION_BACK);}});}
    private boolean unlocked(){return !getSystemService(KeyguardManager.class).isKeyguardLocked();}
    private void refreshRotation(){
        boolean visible=connected && Status.capturing && !Status.huPaused && unlocked();
        if(returns!=null)returns.setEnabled(visible && getSharedPreferences("trial",MODE_PRIVATE).getBoolean("return_overlay",true));
    }
    private boolean allowed(){return connected && Status.capturing && Status.usbOpen && Status.configured && !Status.huPaused
            && getSharedPreferences("trial",MODE_PRIVATE).getBoolean("touch",true) && unlocked();}
    private float[] map(TouchFrames.Point point){
        Display display=getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);Point physical=new Point();display.getRealSize(physical);
        Rect bounds=new Rect(0,0,physical.x,physical.y);
        String geometry="video="+Status.width+"x"+Status.height+" content="+Status.contentWidth+"x"+Status.contentHeight+" physical="+physical+" rotation="+display.getRotation()+" fill="+Status.fillVideo;
        if(!geometry.equals(lastGeometry)){lastGeometry=geometry;Trace.log("TOUCH_GEOMETRY",geometry);}
        return TouchInput.map(new TouchInput.Event(0,point.x,point.y,point.id),Status.width,Status.height,
                Status.contentWidth,Status.contentHeight,bounds.width(),bounds.height(),Status.fillVideo);
    }
    private void accept(TouchInput.Event event){
        if(!allowed()){release();return;}
        if(event.id!=0 && !getSharedPreferences("trial",MODE_PRIVATE).getBoolean("multitouch",true))return;
        // Preserve even a very short down/up pair when comm is absent.
        if(event.action!=TouchInput.MOVE)flush();
        if(!desired.accept(event)){release();Trace.log("TOUCH_REJECT","Invalid transition or more than two pointers");return;}
        main.removeCallbacks(flushFallback);main.postDelayed(flushFallback,10);
        main.removeCallbacks(expire);main.postDelayed(expire,2000);
    }
    private void flush(){
        main.removeCallbacks(flushFallback);TouchFrames.Frame frame=desired.commit();if(frame==null)return;
        if(!frame.transition && !pending.isEmpty() && !pending.peekLast().transition)pending.removeLast();
        if(pending.size()>=32){release();Trace.log("TOUCH_REJECT","Input queue overflow; released all pointers");return;}
        pending.add(frame);drain();
    }
    private void drain(){
        if(busy)return;
        if(releasePending || !allowed()){release();return;}
        TouchFrames.Frame frame=pending.poll();if(frame==null)return;
        boolean changed=frame.points.size()!=strokes.size();
        for(TouchFrames.Point p:frame.points){
            float[] xy=map(p);Pointer prior=strokes.get(p.id);
            if(xy==null){release();Trace.log("TOUCH_REJECT","Geometry mismatch or outside content");return;}
            if(prior==null || !p.down || prior.x!=Math.round(xy[0]) || prior.y!=Math.round(xy[1]))changed=true;
        }
        if(!changed){drain();return;}
        GestureDescription.Builder gesture=new GestureDescription.Builder();
        TreeMap<Integer,Pointer> next=new TreeMap<>();Set<Integer> held=new HashSet<>();
        for(TouchFrames.Point p:frame.points){
            float[] xy=map(p);if(xy==null){release();Trace.log("TOUCH_REJECT","Outside captured display or resize in progress");return;}
            xy[0]=Math.round(xy[0]);xy[1]=Math.round(xy[1]);
            Pointer previous=strokes.get(p.id);if(previous==null && !p.down){release();return;}
            Path path=new Path();
            if(previous==null)path.moveTo(xy[0],xy[1]);else {path.moveTo(previous.x,previous.y);path.lineTo(xy[0],xy[1]);}
            // Android requires the first step to contain only continuing pointers.
            // Add a new finger one millisecond later, with the same end time.
            GestureDescription.StrokeDescription stroke=previous==null
                    ?new GestureDescription.StrokeDescription(path,strokes.isEmpty()?0:1,strokes.isEmpty()?20:19,p.down)
                    :previous.stroke.continueStroke(path,0,20,p.down);
            gesture.addStroke(stroke);next.put(p.id,new Pointer(stroke,xy[0],xy[1]));if(p.down)held.add(p.id);
        }
        if(!next.keySet().containsAll(strokes.keySet())){release();return;}
        Status.touchMaxPointers=Math.max(Status.touchMaxPointers,next.size());submit(gesture.build(),next,held);
    }
    private void submit(GestureDescription gesture,TreeMap<Integer,Pointer> next,Set<Integer> held){
        int token=generation;busy=true;strokes.clear();strokes.putAll(next);
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gesture){
                if(token!=generation)return;busy=false;strokes.keySet().retainAll(held);Status.touchDispatched.incrementAndGet();drain();
            }
            @Override public void onCancelled(GestureDescription gesture){
                if(token!=generation)return;reset();Trace.log("TOUCH_CANCELLED","Gesture interrupted by system or another input");
            }
        },main);
        if(!accepted){reset();Trace.log("TOUCH_ERROR","dispatchGesture rejected");}
    }
    private void reset(){
        generation++;busy=false;releasePending=false;strokes.clear();pending.clear();desired.clear();
        main.removeCallbacks(expire);main.removeCallbacks(flushFallback);
    }
    private void release(){
        pending.clear();desired.clear();main.removeCallbacks(expire);main.removeCallbacks(flushFallback);
        if(busy){releasePending=true;return;}releasePending=false;if(strokes.isEmpty())return;
        GestureDescription.Builder gesture=new GestureDescription.Builder();TreeMap<Integer,Pointer> next=new TreeMap<>();
        for(Map.Entry<Integer,Pointer> e:strokes.entrySet()){
            Pointer p=e.getValue();Path path=new Path();path.moveTo(p.x,p.y);
            GestureDescription.StrokeDescription stroke=p.stroke.continueStroke(path,0,1,false);
            gesture.addStroke(stroke);next.put(e.getKey(),new Pointer(stroke,p.x,p.y));
        }
        submit(gesture.build(),next,Collections.emptySet());
    }
}
