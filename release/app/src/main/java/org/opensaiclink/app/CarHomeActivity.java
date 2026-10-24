package org.opensaiclink.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Landscape dashboard: real navigation launch cards and a four-by-two app grid. */
public final class CarHomeActivity extends CarActivity {
    private TextView clock;
    private LinearLayout navArea,appGrid;
    private final Runnable updateClock=new Runnable(){
        @Override public void run(){
            if(clock!=null)clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
            ui.postDelayed(this,10000);
        }
    };

    public static Intent intent(Context context){
        return new Intent(context,CarHomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(isFinishing())return;
        createPage(false);
        buildHeader();
        LinearLayout body=AppUi.row(this);
        content.addView(body,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        navArea=AppUi.column(this);
        navArea.setPadding(dp(12),dp(12),dp(12),dp(12));
        navArea.setBackground(card(SURFACE,12));
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,38);
        navParams.setMargins(0,dp(8),dp(7),dp(7));
        body.addView(navArea,navParams);
        appGrid=AppUi.column(this);
        LinearLayout.LayoutParams gridParams=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,62);
        gridParams.setMargins(dp(7),dp(8),0,dp(7));
        body.addView(appGrid,gridParams);
        buildNavigation();
        buildApps();
        buildFooter();
        Trace.log("CAR_HOME_OPENED",Status.localTest?"Local preview":"Projection dashboard");
    }

    private void buildHeader(){
        LinearLayout brand=AppUi.row(this);
        TextView mark=heading("OS",10,0xff2E3437);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(AppUi.outlined(this,0xffEFF1F0,0xffC5CBCD,999));
        brand.addView(mark,AppUi.fixed(dp(26),dp(26)));
        TextView name=heading("OpenSAICLink",12,TEXT);
        name.setPadding(dp(8),0,0,0);
        brand.addView(name,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        header.addView(brand,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout live=AppUi.row(this);
        live.setGravity(Gravity.CENTER);
        View dot=new View(this);
        dot.setBackground(AppUi.rounded(this,0xffB43E47,99));
        live.addView(dot,AppUi.fixed(dp(6),dp(6)));
        TextView state=text(getString(Status.localTest?R.string.state_local_preview:R.string.state_projecting),10,TEXT);
        state.setPadding(dp(7),0,0,0);
        live.addView(state,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        header.addView(live,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        clock=heading("",18,TEXT);
        clock.setPadding(dp(20),0,0,0);
        header.addView(clock,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildNavigation(){
        navArea.removeAllViews();
        navArea.addView(text(getString(R.string.navigation),11,MUTED),AppUi.matchWrap());
        navArea.addView(heading(getString(R.string.open_phone_navigation),18,TEXT),AppUi.matchWrap());
        TextView hint=text(getString(R.string.open_phone_navigation_hint),10,MUTED);
        hint.setPadding(0,dp(3),0,dp(10));
        navArea.addView(hint,AppUi.matchWrap());
        navEntry(getString(R.string.quick_gaode),getString(R.string.phone_navigation),"com.autonavi.minimap",android.R.drawable.ic_menu_mylocation);
        navEntry(getString(R.string.quick_baidu),getString(R.string.phone_navigation),"com.baidu.BaiduMap",android.R.drawable.ic_menu_mapmode);
        TextView note=text(getString(R.string.navigation_real_time_aid),10,MUTED);
        note.setPadding(0,dp(8),0,0);
        navArea.addView(note,AppUi.matchWrap());
    }

    private void navEntry(String title,String detail,String packageName,int fallback){
        LinearLayout item=AppUi.row(this);
        item.setPadding(dp(10),dp(7),dp(9),dp(7));
        item.setBackground(AppUi.interactive(this,SURFACE_ALT,0xff4A5154,9,0x2AFFFFFF));
        ImageView image=new ImageView(this);
        image.setImageDrawable(icon(packageName,fallback));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        item.addView(image,AppUi.fixed(dp(25),dp(25)));
        LinearLayout labels=AppUi.column(this);
        labels.setPadding(dp(9),0,0,0);
        labels.addView(heading(title,12,TEXT),AppUi.matchWrap());
        labels.addView(text(detail,9,MUTED),AppUi.matchWrap());
        item.addView(labels,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        item.setContentDescription(title+". "+detail);
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v->AppLauncher.open(this,packageName));
        LinearLayout.LayoutParams p=AppUi.matchWrap();
        p.setMargins(0,dp(6),0,0);
        navArea.addView(item,p);
    }

    private void buildApps(){
        appGrid.removeAllViews();
        TextView title=heading(getString(R.string.navigation_and_apps),13,TEXT);
        appGrid.addView(title,AppUi.matchWrap());
        TextView hint=text(getString(R.string.tap_open_long_manage),9,MUTED);
        hint.setGravity(Gravity.END);
        appGrid.addView(hint,AppUi.matchWrap());
        LinearLayout top=AppUi.row(this),bottom=AppUi.row(this);
        appGrid.addView(top,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        appGrid.addView(bottom,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        appTile(top,getString(R.string.quick_gaode),android.R.drawable.ic_menu_mylocation,true,
                v->AppLauncher.open(this,"com.autonavi.minimap"),null);
        appTile(top,getString(R.string.quick_baidu),android.R.drawable.ic_menu_mapmode,false,
                v->AppLauncher.open(this,"com.baidu.BaiduMap"),null);
        appTile(top,getString(R.string.car_favorite),android.R.drawable.ic_media_play,false,
                v->openOrPickSlot(0),v->{showApps(0);return true;});
        appTile(top,getString(R.string.car_other_apps),android.R.drawable.ic_menu_more,false,
                v->showApps(-1),null);
        appTile(bottom,getString(R.string.car_settings),android.R.drawable.ic_menu_preferences,false,
                v->startActivity(new Intent(this,SettingsActivity.class)),null);
        for(int slot=1;slot<=3;slot++){
            final int chosenSlot=slot;
            String selected=settings().getString(AppSettings.shortcutKey(slot),"");
            CharSequence label=selected.isEmpty()?getString(R.string.car_slot_add,slot):labelFor(selected);
            appTile(bottom,label.toString(),android.R.drawable.ic_input_add,false,
                    v->openOrPickSlot(chosenSlot),v->{showApps(chosenSlot);return true;});
        }
    }

    private void openOrPickSlot(int slot){
        String selected=settings().getString(AppSettings.shortcutKey(slot),"");
        if(selected.isEmpty())showApps(slot);else AppLauncher.open(this,selected);
    }
    private void showApps(int slot){
        startActivity(new Intent(this,AppPickerActivity.class)
                .putExtra("shortcut",slot>=0).putExtra("shortcutSlot",slot>=0?slot:0));
    }
    private CharSequence labelFor(String packageName){
        try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName,0));}
        catch(PackageManager.NameNotFoundException e){return getString(R.string.app_unavailable);}
    }
    private Drawable icon(String packageName,int fallback){
        try{
            if(packageName!=null && !packageName.isEmpty())return getPackageManager().getApplicationIcon(packageName);
        }catch(PackageManager.NameNotFoundException ignored){}
        return getDrawable(fallback);
    }
    private void appTile(LinearLayout row,String title,int fallback,boolean primary,
                         View.OnClickListener click,View.OnLongClickListener hold){
        LinearLayout tile=AppUi.column(this);
        tile.setPadding(dp(8),dp(8),dp(8),dp(6));
        tile.setBackground(AppUi.interactive(this,primary?0xff34393C:SURFACE,
                primary?0xff7B4A4D:0xff42484B,9,0x2AFFFFFF));
        ImageView icon=new ImageView(this);
        icon.setImageDrawable(getDrawable(fallback));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        tile.addView(icon,AppUi.fixed(dp(25),dp(25)));
        TextView label=text(title,10,TEXT);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0,dp(5),0,0);
        tile.addView(label,AppUi.matchWrap());
        tile.setContentDescription(title);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setOnClickListener(click);
        if(hold!=null)tile.setOnLongClickListener(hold);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1);
        p.setMargins(dp(3),dp(3),dp(3),dp(3));
        row.addView(tile,p);
    }

    private void buildFooter(){
        LinearLayout footer=AppUi.row(this);
        footer.setPadding(0,dp(4),0,0);
        TextView info=text(getString(R.string.footer_status),9,MUTED);
        footer.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        addFooterAction(footer,getString(R.string.reset_frame),v->{
            if(Status.usbOpen)startService(new Intent(this,LinkService.class).setAction(LinkService.MAP).putExtra("package",""));
        });
        addFooterAction(footer,getString(R.string.stop_projection),v->stopService(new Intent(this,LinkService.class)));
        TextView back=text(getString(R.string.home),10,TEXT);
        back.setGravity(Gravity.CENTER);
        back.setMinWidth(dp(52));
        back.setMinHeight(dp(30));
        back.setBackground(AppUi.interactive(this,SURFACE,0xff4A5154,999,0x2AFFFFFF));
        back.setContentDescription(getString(R.string.return_car_home));
        AppUi.setClickableText(back,v->startActivity(intent(this)));
        LinearLayout.LayoutParams backP=AppUi.fixed(dp(52),dp(30));
        backP.setMargins(dp(6),0,0,0);
        footer.addView(back,backP);
        page.addView(footer,AppUi.matchWrap());
    }
    private void addFooterAction(LinearLayout footer,String label,View.OnClickListener click){
        TextView action=text(label,9,TEXT);
        action.setGravity(Gravity.CENTER);
        action.setMinHeight(dp(30));
        action.setPadding(dp(8),0,dp(8),0);
        action.setBackground(AppUi.interactive(this,SURFACE,0xff4A5154,6,0x2AFFFFFF));
        action.setContentDescription(label);
        AppUi.setClickableText(action,click);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(30));
        p.setMargins(dp(6),0,0,0);
        footer.addView(action,p);
    }

    private android.content.SharedPreferences settings(){return getSharedPreferences(AppSettings.FILE,MODE_PRIVATE);}
    @Override protected void onResume(){
        super.onResume();
        Status.carHomeVisible=true;
        RemoteTouchService.refreshOrientation();
        ui.post(updateClock);
    }
    @Override protected void onPause(){
        Status.carHomeVisible=false;
        RemoteTouchService.refreshOrientation();
        ui.removeCallbacks(updateClock);
        super.onPause();
    }
}
