package org.opensaiclink.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared landscape shell used only by the app's vehicle surfaces. */
public abstract class CarActivity extends Activity {
    protected static final int BACKGROUND=0xff202528;
    protected static final int SURFACE=0xff292F32;
    protected static final int SURFACE_ALT=0xff343A3D;
    protected static final int TEXT=0xffF4F6F4;
    protected static final int MUTED=0xffADB4B7;
    protected final Handler ui=new Handler(Looper.getMainLooper());
    protected LinearLayout page,content,header;
    private boolean registered;
    private final Runnable checkSession=new Runnable(){
        @Override public void run(){
            if(!closeIfStopped())ui.postDelayed(this,500);
        }
    };
    private final BroadcastReceiver stopped=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){closeIfStopped();}
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!Status.capturing){
            startActivity(new Intent(this,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return;
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        registerReceiver(stopped,new IntentFilter(LinkService.SESSION_STOPPED),Context.RECEIVER_NOT_EXPORTED);
        registered=true;
    }

    protected boolean closeIfStopped(){
        if(Status.capturing){
            hideSystemBars();
            return false;
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        finish();
        return true;
    }
    protected int dp(float value){return AppUi.dp(this,value);}
    protected TextView text(CharSequence value,float size,int tint){return AppUi.text(this,value,size,tint);}
    protected TextView heading(CharSequence value,float size,int tint){return AppUi.heading(this,value,size,tint);}
    protected GradientDrawable card(int fill,int radius){return AppUi.rounded(this,fill,radius);}

    protected void createPage(boolean backButton){
        page=AppUi.column(this);
        page.setPadding(dp(20),dp(12),dp(20),dp(12));
        page.setBackgroundColor(BACKGROUND);
        setContentView(page);
        hideSystemBars();
        page.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            page.setPadding(bars.left+dp(20),bars.top+dp(10),bars.right+dp(20),bars.bottom+dp(10));
            return insets;
        });
        header=AppUi.row(this);
        page.addView(header,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));
        if(backButton){
            TextView back=AppUi.heading(this,"\u2190 "+getString(R.string.back),13,TEXT);
            back.setGravity(Gravity.CENTER);
            back.setMinWidth(dp(112));
            back.setMinHeight(dp(44));
            back.setBackground(AppUi.interactive(this,SURFACE,0xff4A5154,10,0x2AFFFFFF));
            back.setContentDescription(getString(R.string.back_home));
            AppUi.setClickableText(back,v->finish());
            LinearLayout.LayoutParams p=AppUi.fixed(dp(116),dp(44));
            p.setMargins(0,0,dp(14),0);
            header.addView(back,p);
        }
        content=AppUi.column(this);
        page.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
    }

    protected void hideSystemBars(){
        View decor=getWindow().getDecorView();
        WindowInsetsController controller=decor.getWindowInsetsController();
        if(controller!=null){
            controller.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    @Override protected void onResume(){
        super.onResume();
        hideSystemBars();
        if(!isFinishing())ui.post(checkSession);
    }
    @Override protected void onPause(){
        ui.removeCallbacks(checkSession);
        super.onPause();
    }
    @Override protected void onDestroy(){
        ui.removeCallbacksAndMessages(null);
        if(registered)unregisterReceiver(stopped);
        super.onDestroy();
    }
}
