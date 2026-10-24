package org.opensaiclink.trial;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.TextView;
/** A visible return control independent of the system notification shade. */
public final class ReturnOverlay {
    private final AccessibilityService service;private TextView view;
    public ReturnOverlay(AccessibilityService service){this.service=service;}
    private int dp(int n){return Math.round(n*service.getResources().getDisplayMetrics().density);}
    public void setEnabled(boolean enabled){
        enabled=enabled && !Status.carHomeVisible;
        if(enabled==(view!=null))return;
        WindowManager manager=service.getSystemService(WindowManager.class);
        if(!enabled){
            try{manager.removeViewImmediate(view);}catch(RuntimeException e){Trace.log("RETURN_OVERLAY_REMOVE",e.toString());}
            view=null;return;
        }
        TextView candidate=new TextView(service);candidate.setText(R.string.return_overlay_button);candidate.setContentDescription(service.getString(R.string.car_return));
        candidate.setTextSize(15);candidate.setTextColor(0xffffffff);candidate.setGravity(Gravity.CENTER);
        GradientDrawable background=new GradientDrawable();background.setColor(0xe6226e74);background.setCornerRadius(dp(18));candidate.setBackground(background);
        candidate.setOnClickListener(v->{
            if(!Status.capturing)return;RemoteTouchService.releaseCurrent();
            try{service.startActivity(CarHomeActivity.intent(service).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Trace.log("RETURN_OVERLAY_CLICK","Opened car home");}
            catch(RuntimeException e){Trace.log("RETURN_OVERLAY_ERROR",e.toString());}
        });
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(dp(68),dp(36),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.RIGHT|Gravity.BOTTOM;params.x=dp(10);params.y=dp(12);params.setTitle("OpenSAICLink return home");
        try{manager.addView(candidate,params);view=candidate;Trace.log("RETURN_OVERLAY_ON","Visible home control");}
        catch(RuntimeException e){Trace.log("RETURN_OVERLAY_ERROR",e.toString());}
    }
}
