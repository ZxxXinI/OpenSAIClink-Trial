package org.opensaiclink.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Secondary page for permissions, display preferences, app slots and diagnostics. */
public final class SettingsActivity extends Activity {
    private static final int REQUEST_EXPORT=201;
    private static final int REQUEST_NOTIFICATIONS=202;
    private static final int REQUEST_LOCAL_CAPTURE=203;
    private final List<CheckBox> optionViews=new ArrayList<>();
    private TextView summary,permissionLine,bluetoothLine;
    private boolean chooseLocalCapture;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Trace.init(this);
        build();
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIFICATIONS);
    }

    private void build(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(color(R.color.osc_background));
        scroll.setFillViewport(true);
        LinearLayout root=AppUi.column(this);
        root.setPadding(dp(16),dp(12),dp(16),dp(24));
        scroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        View decor=getWindow().getDecorView();
        WindowInsetsController controller=decor.getWindowInsetsController();
        if(controller!=null)controller.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            root.setPadding(bars.left+dp(16),bars.top+dp(12),bars.right+dp(16),bars.bottom+dp(24));
            return insets;
        });
        LinearLayout top=AppUi.row(this);
        root.addView(top,AppUi.matchWrap());
        TextView back=AppUi.text(this,"\u2039",28,color(R.color.osc_text));
        back.setGravity(Gravity.CENTER);
        back.setMinWidth(dp(48));
        back.setMinHeight(dp(48));
        back.setContentDescription(getString(R.string.back));
        AppUi.setClickableText(back,v->finish());
        top.addView(back,AppUi.fixed(dp(48),dp(48)));
        TextView title=AppUi.heading(this,getString(R.string.settings_title),23,color(R.color.osc_text));
        title.setPadding(dp(8),0,0,0);
        top.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        summary=AppUi.text(this,"",12,color(R.color.osc_muted));
        summary.setPadding(0,dp(12),0,dp(4));
        summary.setMaxLines(3);
        root.addView(summary,AppUi.matchWrap());

        addGroup(root,getString(R.string.connection_display),false);
        addOption(root,getString(R.string.option_touch),AppSettings.TOUCH,getString(R.string.option_touch_detail));
        addOption(root,getString(R.string.option_multitouch),AppSettings.MULTITOUCH,getString(R.string.option_multitouch_detail));
        addOption(root,getString(R.string.option_landscape),AppSettings.LANDSCAPE,getString(R.string.option_landscape_detail));
        addOption(root,getString(R.string.option_fill),AppSettings.FILL_DISPLAY,getString(R.string.option_fill_detail));
        addOption(root,getString(R.string.option_responsive),AppSettings.RESPONSIVE,getString(R.string.option_responsive_detail));
        addOption(root,getString(R.string.option_overlay),AppSettings.RETURN_OVERLAY,getString(R.string.option_overlay_detail));

        addGroup(root,getString(R.string.permissions),true);
        permissionLine=AppUi.text(this,"",12,color(R.color.osc_muted));
        permissionLine.setPadding(dp(2),dp(3),dp(2),dp(8));
        permissionLine.setMaxLines(3);
        root.addView(permissionLine,AppUi.matchWrap());
        addAction(root,getString(R.string.open_accessibility),v->openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addAction(root,getString(R.string.allow_rotation),v->openSettings(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:"+getPackageName()))));
        addAction(root,getString(R.string.notification_permission),v->requestPermission());

        addGroup(root,getString(R.string.app_slots),true);
        addSlot(root,0,getString(R.string.slot_music));
        addSlot(root,1,getString(R.string.slot_car_1));
        addSlot(root,2,getString(R.string.slot_car_2));
        addSlot(root,3,getString(R.string.slot_car_3));

        addGroup(root,getString(R.string.help_diagnostics),true);
        bluetoothLine=AppUi.text(this,getString(R.string.bluetooth_explanation),12,color(R.color.osc_muted));
        bluetoothLine.setPadding(dp(2),dp(3),dp(2),dp(8));
        bluetoothLine.setMaxLines(3);
        root.addView(bluetoothLine,AppUi.matchWrap());
        addAction(root,getString(R.string.show_bluetooth_tip),v->{
            settings().edit().putBoolean(AppSettings.BLUETOOTH_TIP,false).apply();refresh();Toast.makeText(this,getString(R.string.bluetooth_tip_restored),Toast.LENGTH_SHORT).show();
        });
        addAction(root,getString(R.string.local_preview),v->requestLocalCapture());
        addAction(root,getString(R.string.test_pattern),v->{
            if(Status.capturing)startActivity(new Intent(this,PatternActivity.class));
            else Toast.makeText(this,getString(R.string.start_preview_first),Toast.LENGTH_SHORT).show();
        });
        addAction(root,getString(R.string.ui_preview_debug),v->{
            if(!Status.capturing){
                Status.capturing=true;
                Status.localTest=true;
                Status.usbOpen=false;
                Status.configured=false;
            }
            startActivity(CarHomeActivity.intent(this));
        });
        addAction(root,getString(R.string.stop_projection),v->{
            stopService(new Intent(this,LinkService.class));
            handlerPostRefresh();
        });
        addAction(root,getString(R.string.export_diagnostics),v->requestExport());
        TextView version=AppUi.text(this,getString(R.string.version_label),11,color(R.color.osc_muted));
        version.setGravity(Gravity.CENTER);
        version.setPadding(0,dp(18),0,dp(4));
        root.addView(version,AppUi.matchWrap());
    }

    private void addGroup(LinearLayout root,String label,boolean withGap){
        LinearLayout groupHeader=AppUi.row(this);
        if(withGap)groupHeader.setPadding(0,dp(16),0,0);
        root.addView(groupHeader,AppUi.matchWrap());
        groupHeader.addView(AppUi.heading(this,label,15,color(R.color.osc_text)),
                new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
    }

    private void addOption(LinearLayout root,String label,String key,String detail){
        LinearLayout row=AppUi.row(this);
        row.setPadding(dp(12),dp(5),dp(8),dp(5));
        row.setBackground(AppUi.interactive(this,color(R.color.osc_card),color(R.color.osc_silver),10,0x12515B60));
        LinearLayout labels=AppUi.column(this);
        labels.addView(AppUi.heading(this,label,13,color(R.color.osc_text)),AppUi.matchWrap());
        TextView description=AppUi.text(this,detail,10,color(R.color.osc_muted));
        description.setMaxLines(2);
        description.setPadding(0,dp(2),0,0);
        labels.addView(description,AppUi.matchWrap());
        row.addView(labels,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        CheckBox check=new CheckBox(this);
        check.setText(label+"\n"+detail);
        check.setChecked(settings().getBoolean(key,true));
        check.setText("");
        check.setContentDescription(label);
        check.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked},{}},
                new int[]{color(R.color.osc_red),color(R.color.osc_muted)}));
        row.setOnClickListener(v->check.setChecked(!check.isChecked()));
        row.addView(check,AppUi.fixed(dp(48),dp(48)));
        check.setOnCheckedChangeListener((button,checked)->{
            settings().edit().putBoolean(key,checked).apply();
            if(AppSettings.TOUCH.equals(key) || AppSettings.MULTITOUCH.equals(key))RemoteTouchService.releaseCurrent();
            if(AppSettings.RETURN_OVERLAY.equals(key))RemoteTouchService.refreshOrientation();
            if(AppSettings.LANDSCAPE.equals(key) && !checked)ProjectionRotation.restore(this);
            refresh();
        });
        LinearLayout.LayoutParams p=AppUi.matchWrap();
        p.setMargins(0,dp(4),0,0);
        root.addView(row,p);
        optionViews.add(check);
    }

    private void addAction(LinearLayout root,String label,View.OnClickListener click){
        TextView action=AppUi.compactButton(this,label,color(R.color.osc_card),color(R.color.osc_text));
        action.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        action.setPadding(dp(14),dp(10),dp(14),dp(10));
        action.setContentDescription(label);
        action.setOnClickListener(click);
        LinearLayout.LayoutParams p=AppUi.matchWrap();
        p.setMargins(0,dp(5),0,0);
        root.addView(action,p);
    }

    private void addSlot(LinearLayout root,int slot,String fallback){
        String selected=settings().getString(AppSettings.shortcutKey(slot),"");
        CharSequence label=fallback;
        try{
            if(!selected.isEmpty())
                label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(selected,0));
        }catch(PackageManager.NameNotFoundException ignored){}
        addAction(root,label.toString(),v->startActivity(new Intent(this,AppPickerActivity.class)
                .putExtra("shortcut",true).putExtra("shortcutSlot",slot)));
    }

    private void startLocalPreview(){
        if(!Status.touchReady){Toast.makeText(this,getString(R.string.toast_accessibility_missing),Toast.LENGTH_LONG).show();return;}
        if(Status.usbOpen){
            Toast.makeText(this,getString(R.string.toast_usb_stop_first),Toast.LENGTH_LONG).show();return;
        }
        startActivity(new Intent(this,MainActivity.class));
    }

    private void requestExport(){
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE,"OpenSAICLink-0.1.0-"+System.currentTimeMillis()+".zip");
        startActivityForResult(save,REQUEST_EXPORT);
    }

    private void requestLocalCapture(){
        if(!Status.touchReady){
            Toast.makeText(this,getString(R.string.toast_accessibility_missing),Toast.LENGTH_LONG).show();
            return;
        }
        if(Status.usbOpen){
            Toast.makeText(this,getString(R.string.toast_usb_stop_first),Toast.LENGTH_LONG).show();
            return;
        }
        if(Status.capturing){
            startActivity(CarHomeActivity.intent(this));
            return;
        }
        chooseLocalCapture=true;
        android.media.projection.MediaProjectionManager manager=
                getSystemService(android.media.projection.MediaProjectionManager.class);
        Intent request=android.os.Build.VERSION.SDK_INT>=34
                ?manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
                :manager.createScreenCaptureIntent();
        startActivityForResult(request,REQUEST_LOCAL_CAPTURE);
    }

    private void requestPermission(){
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIFICATIONS);
        else Toast.makeText(this,getString(R.string.notification_granted),Toast.LENGTH_SHORT).show();
    }

    private void openSettings(Intent intent){
        try{startActivity(intent);}
        catch(Exception e){Toast.makeText(this,getString(R.string.toast_settings_unavailable),Toast.LENGTH_LONG).show();}
    }

    private void handlerPostRefresh(){
        summary.postDelayed(this::refresh,250);
    }

    private void refresh(){
        if(summary==null)return;
        String connection=Status.usbOpen
                ?(Status.configured?getString(R.string.connection_connected):getString(R.string.connection_configuring))
                :getString(R.string.connection_waiting);
        summary.setText(connection+"  |  "+(Status.capturing?getString(R.string.capture_running):getString(R.string.capture_stopped)));
        String accessibility=getString(Status.touchReady?R.string.permission_accessibility_on:R.string.permission_accessibility_off);
        String notify=android.os.Build.VERSION.SDK_INT<33
                ||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
                ?getString(R.string.permission_notification_on):getString(R.string.permission_notification_off);
        String rotation=getString(Settings.System.canWrite(this)?
                R.string.permission_rotation_on:R.string.permission_rotation_off);
        permissionLine.setText(getString(R.string.permission_summary_format,accessibility,notify,rotation));
        boolean tipHidden=settings().getBoolean(AppSettings.BLUETOOTH_TIP,false);
        bluetoothLine.setText(getString(R.string.bluetooth_explanation)
                +(tipHidden?"\n"+getString(R.string.bluetooth_tip_hidden):""));
    }

    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQUEST_LOCAL_CAPTURE){
            if(result!=RESULT_OK || data==null){
                Trace.log("LOCAL_CAPTURE_DENIED","Screen sharing not granted");
                return;
            }
            settings().edit().putBoolean(AppSettings.TOUCH,true).apply();
            Status.showCarHome=true;
            startForegroundService(new Intent(this,LinkService.class)
                    .setAction(LinkService.CAPTURE).putExtra(LinkService.GRANT,data)
                    .putExtra("result",result).putExtra("local",true));
        }else if(request==REQUEST_EXPORT && result==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();
            new Thread(()->{
                try(OutputStream output=getContentResolver().openOutputStream(uri)){
                    if(output==null)throw new java.io.IOException("Cannot open export destination");
                    Trace.export(output);
                    runOnUiThread(()->Toast.makeText(this,getString(R.string.diagnostic_saved),Toast.LENGTH_SHORT).show());
                }catch(Exception e){
                    Trace.log("SETTINGS_EXPORT_ERROR",e.toString());
                    runOnUiThread(()->Toast.makeText(this,getString(R.string.diagnostic_failed),Toast.LENGTH_SHORT).show());
                }
            },"osc-settings-export").start();
        }
    }

    private android.content.SharedPreferences settings(){return getSharedPreferences(AppSettings.FILE,MODE_PRIVATE);}
    private int dp(float value){return AppUi.dp(this,value);}
    private int color(int res){return getColor(res);}
}
