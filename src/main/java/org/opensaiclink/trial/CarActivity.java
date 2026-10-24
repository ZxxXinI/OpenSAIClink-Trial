package org.opensaiclink.trial;

import android.app.Activity;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Landscape pages belonging to this app, without replacing the phone launcher. */
public abstract class CarActivity extends Activity {
    protected static final int BACKGROUND=0xff0c1422, CARD=0xff1a293e, TEXT=0xfff2f6fc, MUTED=0xffaabbd1;
    protected final Handler ui=new Handler(Looper.getMainLooper());
    protected LinearLayout page,header;
    private boolean registered;
    private final Runnable checkSession=new Runnable(){
        @Override public void run(){if(!closeIfStopped())ui.postDelayed(this,500);}
    };
    private final BroadcastReceiver stopped=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){closeIfStopped();}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!Status.capturing){
            startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();return;
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        registerReceiver(stopped,new IntentFilter(LinkService.SESSION_STOPPED),Context.RECEIVER_NOT_EXPORTED);registered=true;
    }
    protected boolean closeIfStopped(){
        if(Status.capturing)return false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);finish();return true;
    }
    protected int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    protected TextView text(CharSequence value,float size,int color){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;
    }
    protected Drawable cardBackground(int color){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(18));
        return new RippleDrawable(ColorStateList.valueOf(0x335cb8ff),shape,null);
    }
    protected void createPage(int title,boolean back){
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(BACKGROUND);
        page.setPadding(dp(20),dp(10),dp(20),dp(12));setContentView(page);
        page.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            view.setPadding(bars.left+dp(20),bars.top+dp(8),bars.right+dp(20),bars.bottom+dp(10));return insets;
        });
        WindowInsetsController controller=getWindow().getInsetsController();
        if(controller!=null)controller.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));
        if(back){
            Button button=new Button(this);button.setText(R.string.car_back);button.setTextColor(TEXT);
            button.setBackground(cardBackground(CARD));button.setOnClickListener(v->finish());
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(126),dp(44));params.setMargins(0,0,dp(16),0);header.addView(button,params);
        }
        TextView heading=text(getString(title),26,TEXT);heading.setSingleLine(true);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
    }
    @Override protected void onResume(){super.onResume();if(!isFinishing())ui.post(checkSession);}
    @Override protected void onPause(){ui.removeCallbacks(checkSession);super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);if(registered)unregisterReceiver(stopped);super.onDestroy();}
}
