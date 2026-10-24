package org.opensaiclink.trial;

import android.content.*;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class CarHomeActivity extends CarActivity {
    private LinearLayout grid;
    private TextView clock;
    private final Runnable updateClock=new Runnable(){
        @Override public void run(){if(clock!=null)clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));ui.postDelayed(this,10000);}
    };
    public static Intent intent(Context context){return new Intent(context,CarHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(isFinishing())return;
        createPage(R.string.car_home,false);
        TextView mode=text(getString(Status.localTest?R.string.car_preview:R.string.car_projecting),14,0xff84d4c6);
        header.addView(mode);clock=text("",24,TEXT);clock.setPadding(dp(20),0,0,0);header.addView(clock);
        grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);
        page.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=text(getString(R.string.car_home_hint),13,MUTED);footer.setPadding(0,dp(6),0,0);page.addView(footer);
        Trace.log("CAR_HOME_OPENED",Status.localTest?"Local preview":"Projection dashboard");
    }
    public static String shortcutKey(int slot){if(slot<0 || slot>3)throw new IllegalArgumentException("slot");return slot==0?"car_shortcut_package":"car_shortcut_package_"+slot;}
    private void showApps(boolean shortcut){showApps(shortcut,0);}
    private void showApps(boolean shortcut,int slot){startActivity(new Intent(this,AppPickerActivity.class).putExtra("shortcut",shortcut).putExtra("shortcutSlot",slot));}
    private void editSlot(int slot){
        new AlertDialog.Builder(this).setItems(new CharSequence[]{getString(R.string.car_slot_change),getString(R.string.car_slot_clear)},(dialog,which)->{
            if(which==0)showApps(true,slot);else {getSharedPreferences("trial",MODE_PRIVATE).edit().remove(shortcutKey(slot)).apply();cards();}
        }).show();
    }
    private Drawable icon(String packageName,int fallback){
        try{if(packageName!=null)return getPackageManager().getApplicationIcon(packageName);}catch(PackageManager.NameNotFoundException ignored){}
        return getDrawable(fallback);
    }
    private void tile(LinearLayout row,CharSequence title,CharSequence description,Drawable drawable,View.OnClickListener click,View.OnLongClickListener hold){
        LinearLayout tile=new LinearLayout(this);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(18),dp(8),dp(12),dp(8));
        tile.setBackground(cardBackground(CARD));tile.setOnClickListener(click);tile.setFocusable(true);
        if(hold!=null)tile.setOnLongClickListener(hold);
        tile.setContentDescription(title+". "+description);
        ImageView image=new ImageView(this);image.setImageDrawable(drawable);
        tile.addView(image,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(14),0,0,0);
        TextView main=text(title,20,TEXT);main.setMaxLines(1);main.setEllipsize(TextUtils.TruncateAt.END);labels.addView(main);
        TextView sub=text(description,12,MUTED);sub.setMaxLines(1);sub.setPadding(0,dp(3),0,0);labels.addView(sub);
        tile.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1);params.setMargins(dp(5),dp(5),dp(5),dp(5));row.addView(tile,params);
    }
    private void cards(){
        grid.removeAllViews();
        LinearLayout top=new LinearLayout(this),bottom=new LinearLayout(this),extra=new LinearLayout(this);
        grid.addView(top,new LinearLayout.LayoutParams(-1,0,1));grid.addView(bottom,new LinearLayout.LayoutParams(-1,0,1));grid.addView(extra,new LinearLayout.LayoutParams(-1,0,1));
        tile(top,getString(R.string.car_gaode),getString(R.string.car_navigation),icon("com.autonavi.minimap",android.R.drawable.ic_menu_mylocation),v->AppLauncher.open(this,"com.autonavi.minimap"),null);
        tile(top,getString(R.string.car_baidu),getString(R.string.car_navigation),icon("com.baidu.BaiduMap",android.R.drawable.ic_menu_mapmode),v->AppLauncher.open(this,"com.baidu.BaiduMap"),null);
        String favorite=getSharedPreferences("trial",MODE_PRIVATE).getString("car_shortcut_package","");
        CharSequence favoriteName=getString(R.string.car_favorite);
        try{if(!favorite.isEmpty())favoriteName=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(favorite,0));}
        catch(PackageManager.NameNotFoundException ignored){}
        tile(top,favoriteName,getString(favorite.isEmpty()?R.string.car_choose_favorite:R.string.car_change_favorite),icon(favorite,android.R.drawable.ic_media_play),
                v->{if(favorite.isEmpty())showApps(true);else AppLauncher.open(this,favorite);},v->{showApps(true);return true;});
        tile(bottom,getString(R.string.car_other_apps),getString(R.string.car_apps_hint),icon(null,android.R.drawable.ic_menu_more),v->showApps(false),null);
        tile(bottom,getString(R.string.car_pattern),getString(R.string.car_pattern_hint),icon(null,android.R.drawable.ic_menu_view),v->startActivity(new Intent(this,PatternActivity.class)),null);
        tile(bottom,getString(R.string.car_settings),getString(R.string.car_settings_hint),icon(null,android.R.drawable.ic_menu_preferences),
                v->startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)),null);
        for(int i=1;i<=3;i++){
            final int slot=i;String chosen=getSharedPreferences("trial",MODE_PRIVATE).getString(shortcutKey(slot),"");
            CharSequence title=getString(R.string.car_slot_add,slot);
            try{if(!chosen.isEmpty())title=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(chosen,0));}catch(PackageManager.NameNotFoundException ignored){}
            tile(extra,title,getString(chosen.isEmpty()?R.string.car_slot_empty:R.string.car_change_favorite),icon(chosen,android.R.drawable.ic_input_add),
                    v->{if(chosen.isEmpty())showApps(true,slot);else AppLauncher.open(this,chosen);},v->{if(chosen.isEmpty())showApps(true,slot);else editSlot(slot);return true;});
        }
    }
    @Override protected void onResume(){super.onResume();Status.carHomeVisible=true;RemoteTouchService.refreshOrientation();if(grid!=null && !isFinishing()){cards();ui.post(updateClock);}}
    @Override protected void onPause(){Status.carHomeVisible=false;RemoteTouchService.refreshOrientation();ui.removeCallbacks(updateClock);super.onPause();}
}
