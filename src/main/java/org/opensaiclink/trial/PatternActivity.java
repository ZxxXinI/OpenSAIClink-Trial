package org.opensaiclink.trial;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;

public final class PatternActivity extends Activity {
    public static volatile int touchDown,touchMoves,touchUp,pointerDown,pointerUp,maxPointers;
    public static volatile float pinchScale=1;
    public static volatile float lastTouchX,lastTouchY;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(new Pattern(this));
    }
    private static final class Pattern extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long began=SystemClock.elapsedRealtime();
        private final ScaleGestureDetector scale;
        Pattern(Context c){super(c);setContentDescription("OpenSAICLink moving test pattern");
            scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector detector){pinchScale*=detector.getScaleFactor();return true;}
            });
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            lastTouchX=event.getX();lastTouchY=event.getY();
            maxPointers=Math.max(maxPointers,event.getPointerCount());scale.onTouchEvent(event);
            if(event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN)pointerDown++;
            if(event.getActionMasked()==MotionEvent.ACTION_POINTER_UP)pointerUp++;
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)touchDown++;
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE)touchMoves++;
            if(event.getActionMasked()==MotionEvent.ACTION_UP)touchUp++;
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN || event.getActionMasked()==MotionEvent.ACTION_UP)
                Trace.log("PATTERN_TOUCH","action="+event.getActionMasked()+" x="+lastTouchX+" y="+lastTouchY);
            invalidate();return true;
        }
        @Override protected void onDraw(Canvas canvas){
            int[] colors={Color.rgb(220,50,50),Color.rgb(40,180,90),Color.rgb(50,100,225),Color.rgb(240,185,30)};
            int w=getWidth(),h=getHeight();
            for(int i=0;i<4;i++){paint.setColor(colors[i]);canvas.drawRect(i*w/4f,0,(i+1)*w/4f,h,paint);}
            long elapsed=SystemClock.elapsedRealtime()-began;
            float x=(elapsed%4000)/4000f*w;
            paint.setColor(Color.WHITE);canvas.drawCircle(x,h*.65f,Math.max(10,w*.025f),paint);
            paint.setColor(Color.BLACK);paint.setTextSize(Math.max(22,w*.045f));
            canvas.drawText("OpenSAICLink E6",w*.05f,h*.25f,paint);
            canvas.drawText("time "+elapsed+" ms",w*.05f,h*.35f,paint);
            canvas.drawText("encoded "+Status.encoded.get()+" / USB "+Status.videoWritten.get(),w*.05f,h*.45f,paint);
            canvas.drawText("touch down/move/up "+touchDown+" / "+touchMoves+" / "+touchUp,w*.05f,h*.55f,paint);
            canvas.drawText("pointers "+maxPointers+" / scale "+String.format(java.util.Locale.US,"%.2f",pinchScale),w*.05f,h*.85f,paint);
            if(touchDown>0){paint.setColor(Color.MAGENTA);canvas.drawCircle(lastTouchX,lastTouchY,18,paint);}
            postInvalidateDelayed(50);
        }
    }
}
