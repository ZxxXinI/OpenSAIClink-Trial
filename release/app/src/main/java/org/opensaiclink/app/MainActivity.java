package org.opensaiclink.app;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** One-page phone connection surface. Technical settings live in SettingsActivity. */
public final class MainActivity extends Activity {
    private static final String USB_PERMISSION="org.opensaiclink.app.USB_PERMISSION";
    private static final int REQUEST_CAPTURE=100;
    private static final int REQUEST_EXPORT=101;
    private static final int REQUEST_NOTIFICATIONS=5;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<TextView> stepViews=new ArrayList<>();
    private LinearLayout connectionCard,quickGrid,bluetoothTip;
    private TextView stateTitle,statePill,primaryButton,statusLine,edgeNote;
    private boolean captureForLocal;
    private boolean receiverRegistered;
    private boolean actionRunning;
    private int actionMode;
    private String lastRenderedState;
    private String lastRenderedShortcut;
    private final Runnable refresh=new Runnable(){
        @Override public void run(){
            if(!isFinishing())render();
            handler.postDelayed(this,500);
        }
    };
    private final BroadcastReceiver usbReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(!USB_PERMISSION.equals(intent.getAction()))return;
            UsbAccessory accessory=intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY,UsbAccessory.class);
            UsbManager manager=getSystemService(UsbManager.class);
            if(accessory!=null && manager.hasPermission(accessory)){
                connect(accessory);
            }else{
                toast(getString(R.string.toast_usb_denied));
                Trace.log("USB_PERMISSION_DENIED","User denied accessory access");
            }
        }
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Trace.init(this);
        if(state!=null)captureForLocal=state.getBoolean("captureForLocal",false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildPhone();
        registerReceiver(usbReceiver,new IntentFilter(USB_PERMISSION),Context.RECEIVER_NOT_EXPORTED);
        receiverRegistered=true;
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIFICATIONS);
        if(UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(getIntent().getAction()))
            handler.postDelayed(this::findAccessory,450);
    }

    private void buildPhone(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(color(R.color.osc_background));
        scroll.setFillViewport(true);
        LinearLayout root=AppUi.column(this);
        root.setPadding(dp(16),dp(10),dp(16),dp(16));
        scroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        View decor=getWindow().getDecorView();
        WindowInsetsController controller=decor.getWindowInsetsController();
        if(controller!=null)controller.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            root.setPadding(bars.left+dp(16),bars.top+dp(10),bars.right+dp(16),bars.bottom+dp(16));
            return insets;
        });

        LinearLayout brand=AppUi.row(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(brand,AppUi.matchWrap());
        TextView mark=AppUi.heading(this,"OS",13,color(R.color.osc_text));
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(AppUi.outlined(this,color(R.color.osc_card),color(R.color.osc_silver_dark),999));
        brand.addView(mark,AppUi.fixed(dp(40),dp(40)));
        LinearLayout brandText=AppUi.column(this);
        brandText.setPadding(dp(10),0,0,0);
        TextView title=AppUi.heading(this,getString(R.string.app_name),20,color(R.color.osc_text));
        brandText.addView(title,AppUi.matchWrap());
        brandText.addView(AppUi.text(this,getString(R.string.brand_subtitle),11,color(R.color.osc_muted)),AppUi.matchWrap());
        brand.addView(brandText,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView settingsButton=AppUi.text(this,"\u2699",21,color(R.color.osc_text));
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setMinWidth(dp(48));
        settingsButton.setMinHeight(dp(48));
        settingsButton.setBackground(AppUi.interactive(this,color(R.color.osc_card),color(R.color.osc_silver),999,0x20515C60));
        settingsButton.setContentDescription(getString(R.string.settings_content_description));
        AppUi.setClickableText(settingsButton,v->startActivity(new Intent(this,SettingsActivity.class)));
        brand.addView(settingsButton,AppUi.fixed(dp(48),dp(48)));

        TextView greeting=AppUi.heading(this,getString(R.string.phone_ready_title),25,color(R.color.osc_text));
        greeting.setPadding(0,dp(14),0,dp(1));
        root.addView(greeting,AppUi.matchWrap());
        root.addView(AppUi.text(this,getString(R.string.phone_ready_subtitle),11,color(R.color.osc_muted)),AppUi.matchWrap());

        connectionCard=AppUi.column(this);
        connectionCard.setPadding(dp(16),dp(11),dp(16),dp(11));
        connectionCard.setBackground(AppUi.outlined(this,color(R.color.osc_graphite),color(R.color.osc_graphite_soft),18));
        LinearLayout.LayoutParams cardParams=AppUi.matchWrap();
        cardParams.setMargins(0,dp(9),0,0);
        root.addView(connectionCard,cardParams);
        LinearLayout cardTop=AppUi.row(this);
        connectionCard.addView(cardTop,AppUi.matchWrap());
        LinearLayout cardText=AppUi.column(this);
        cardTop.addView(cardText,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        cardText.addView(AppUi.text(this,getString(R.string.current_connection),10,color(R.color.osc_dark_muted)),AppUi.matchWrap());
        stateTitle=AppUi.heading(this,"",18,color(R.color.osc_dark_text));
        stateTitle.setPadding(0,dp(3),0,0);
        cardText.addView(stateTitle,AppUi.matchWrap());
        statePill=AppUi.text(this,"",10,color(R.color.osc_dark_text));
        statePill.setGravity(Gravity.CENTER);
        statePill.setPadding(dp(9),dp(5),dp(9),dp(5));
        statePill.setBackground(AppUi.rounded(this,0x2FFFFFFF,999));
        cardTop.addView(statePill,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout progress=AppUi.row(this);
        progress.setPadding(0,dp(8),0,0);
        connectionCard.addView(progress,AppUi.matchWrap());
        for(int i=0;i<3;i++){
            TextView step=AppUi.text(this,"",10,color(R.color.osc_dark_text));
            step.setPadding(0,dp(8),0,0);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(34),1);
            p.setMargins(i==0?0:dp(3),0,0,0);
            progress.addView(step,p);
            stepViews.add(step);
        }
        primaryButton=AppUi.heading(this,"",13,color(R.color.osc_white));
        primaryButton.setGravity(Gravity.CENTER);
        primaryButton.setMinHeight(dp(48));
        primaryButton.setBackground(AppUi.interactive(this,color(R.color.osc_red),color(R.color.osc_red),12,0x22FFFFFF));
        AppUi.setClickableText(primaryButton,v->onPrimaryAction());
        LinearLayout.LayoutParams primaryParams=AppUi.matchWrap();
        primaryParams.height=dp(48);
        primaryParams.setMargins(0,dp(9),0,0);
        connectionCard.addView(primaryButton,primaryParams);

        LinearLayout shortcutsHeader=AppUi.row(this);
        shortcutsHeader.setPadding(0,dp(11),0,0);
        root.addView(shortcutsHeader,AppUi.matchWrap());
        shortcutsHeader.addView(AppUi.heading(this,getString(R.string.quick_apps),14,color(R.color.osc_text)),
                new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        shortcutsHeader.addView(AppUi.text(this,getString(R.string.quick_open),10,color(R.color.osc_muted)),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        quickGrid=AppUi.row(this);
        quickGrid.setPadding(0,dp(6),0,0);
        root.addView(quickGrid,AppUi.matchWrap());
        buildQuickButtons();

        statusLine=AppUi.text(this,"",11,color(R.color.osc_muted));
        statusLine.setPadding(dp(2),dp(8),dp(2),0);
        statusLine.setMaxLines(2);
        statusLine.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(statusLine,AppUi.matchWrap());

        if(!settings().getBoolean(AppSettings.BLUETOOTH_TIP,false))buildBluetoothTip(root);
        edgeNote=AppUi.text(this,getString(R.string.edge_note),10,color(R.color.osc_muted));
        edgeNote.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams=AppUi.matchWrap();
        noteParams.setMargins(0,dp(8),0,0);
        root.addView(edgeNote,noteParams);
    }

    private void buildQuickButtons(){
        quickGrid.removeAllViews();
        addQuickButton("\u2316",getString(R.string.quick_gaode),v->openMap("com.autonavi.minimap"));
        final String favorite=settings().getString(AppSettings.shortcutKey(0),"");
        CharSequence favoriteName=getString(R.string.quick_music);
        try{
            if(!favorite.isEmpty())
                favoriteName=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(favorite,0));
        }catch(PackageManager.NameNotFoundException ignored){}
        final String selected=favorite;
        final CharSequence selectedName=favoriteName;
        addQuickButton("\u266b",selectedName.toString(),v->{
            if(selected.isEmpty())startActivity(new Intent(this,AppPickerActivity.class)
                    .putExtra("shortcut",true).putExtra("shortcutSlot",0));
            else AppLauncher.open(this,selected);
        });
        addQuickButton("+",getString(R.string.quick_add),v->startActivity(new Intent(this,AppPickerActivity.class)
                .putExtra("shortcut",true).putExtra("shortcutSlot",1)));
    }

    private void addQuickButton(String icon,String label,View.OnClickListener listener){
        LinearLayout tile=AppUi.column(this);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(5),dp(10),dp(5),dp(9));
        tile.setBackground(AppUi.interactive(this,color(R.color.osc_card),color(R.color.osc_silver),12,0x12515C60));
        TextView iconView=AppUi.heading(this,icon,21,color(R.color.osc_graphite_soft));
        iconView.setGravity(Gravity.CENTER);
        tile.addView(iconView,AppUi.fixed(dp(34),dp(32)));
        TextView name=AppUi.text(this,label,10,color(R.color.osc_text));
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(name,AppUi.matchWrap());
        tile.setContentDescription(label);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setOnClickListener(listener);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(76),1);
        p.setMargins(0,0,dp(6),0);
        quickGrid.addView(tile,p);
    }

    private void buildBluetoothTip(LinearLayout root){
        bluetoothTip=AppUi.row(this);
        bluetoothTip.setPadding(dp(11),dp(10),dp(7),dp(10));
        bluetoothTip.setBackground(AppUi.rounded(this,color(R.color.osc_red_soft),10));
        TextView tip=AppUi.text(this,getString(R.string.bluetooth_tip),11,color(R.color.osc_text));
        tip.setMaxLines(2);
        bluetoothTip.addView(tip,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView close=AppUi.text(this,getString(R.string.close),10,color(R.color.osc_red));
        close.setGravity(Gravity.CENTER);
        close.setMinWidth(dp(48));
        close.setMinHeight(dp(48));
        close.setContentDescription(getString(R.string.close_bluetooth_tip));
        AppUi.setClickableText(close,v->{
            settings().edit().putBoolean(AppSettings.BLUETOOTH_TIP,true).apply();
            if(bluetoothTip.getParent() instanceof ViewGroup)((ViewGroup)bluetoothTip.getParent()).removeView(bluetoothTip);
        });
        bluetoothTip.addView(close,AppUi.fixed(dp(48),dp(48)));
        LinearLayout.LayoutParams p=AppUi.matchWrap();
        p.setMargins(0,dp(8),0,0);
        root.addView(bluetoothTip,p);
    }

    private void render(){
        boolean touch=Status.touchReady,capturing=Status.capturing,usb=Status.usbOpen,configured=Status.configured;
        String favorite=settings().getString(AppSettings.shortcutKey(0),"");
        String signature=touch+"|"+capturing+"|"+usb+"|"+configured+"|"+Status.localTest+"|"+Status.stage
                +"|"+Status.encoded.get()+"|"+Status.videoWritten.get()+"|"+favorite;
        if(signature.equals(lastRenderedState))return;
        lastRenderedState=signature;
        String state,pill,action;
        if(capturing){
            state=getString(Status.localTest?R.string.state_local_preview:R.string.state_projecting);
            pill=getString(R.string.pill_running);
            action=getString(R.string.action_car_home);
            actionMode=4;
            actionRunning=true;
        }else if(!touch){
            state=getString(R.string.state_touch_missing);
            pill=getString(R.string.pill_step_1);
            action=getString(R.string.action_touch);
            actionMode=1;
            actionRunning=true;
        }else if(!usb){
            state=getString(R.string.state_waiting_car);
            pill=getString(R.string.pill_step_2);
            action=getString(R.string.action_connect);
            actionMode=2;
            actionRunning=true;
        }else if(!configured){
            state=getString(R.string.state_car_connected);
            pill=getString(R.string.pill_preparing);
            action=getString(R.string.action_waiting);
            actionMode=3;
            actionRunning=false;
        }else{
            state=getString(R.string.state_car_connected);
            pill=getString(R.string.pill_ready);
            action=getString(R.string.action_capture);
            actionMode=3;
            actionRunning=true;
        }
        stateTitle.setText(state);
        statePill.setText(pill);
        primaryButton.setText(action);
        primaryButton.setEnabled(actionRunning);
        primaryButton.setAlpha(actionRunning?1f:.58f);
        String[] labels={getString(R.string.step_touch),getString(R.string.step_car),getString(R.string.step_capture)};
        String[] details={
                getString(touch?R.string.step_done:R.string.step_missing),
                getString(usb?R.string.step_connected:R.string.step_disconnected),
                getString(capturing?R.string.step_running:configured?R.string.step_startable:R.string.step_waiting)
        };
        int active=!touch?0:!usb?1:2;
        for(int i=0;i<stepViews.size();i++){
            TextView step=stepViews.get(i);
            step.setText(labels[i]+"\n"+details[i]);
            int border=i<active?0xffd1d5d6:i==active?color(R.color.osc_red):0xff555b5e;
            GradientDrawable body=AppUi.rounded(this,0,0);
            body.setStroke(dp(2),border);
            step.setBackground(body);
            step.setTextColor(i<=active?color(R.color.osc_dark_text):color(R.color.osc_dark_muted));
        }
        String runtime=getString(Status.stage==null||Status.stage.isEmpty()?R.string.status_waiting_next:
                !Status.touchReady?R.string.state_touch_missing:
                !usb?R.string.state_waiting_car:
                !configured?R.string.status_handshake:
                capturing?R.string.status_running:R.string.status_ready_capture);
        statusLine.setText(getString(R.string.status_format,runtime));
        edgeNote.setText(getString(Status.capturing?R.string.edge_note_running:R.string.edge_note));
        if(quickGrid!=null)buildQuickButtons();
    }

    private void onPrimaryAction(){
        switch(actionMode){
            case 1:
                try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
                catch(ActivityNotFoundException e){toast(getString(R.string.toast_settings_unavailable));}
                break;
            case 2:findAccessory();break;
            case 3:requestCapture(false);break;
            case 4:startActivity(CarHomeActivity.intent(this));break;
            default:toast(getString(R.string.toast_reading_state));
        }
    }

    private void findAccessory(){
        if(Status.capturing && Status.localTest){toast(getString(R.string.toast_preview_stop_first));return;}
        UsbManager manager=getSystemService(UsbManager.class);
        UsbAccessory[] accessories=manager.getAccessoryList();
        if(accessories==null || accessories.length==0){
            Trace.log("USB_NOT_FOUND","No accessory");
            toast(getString(R.string.toast_usb_missing));
            return;
        }
        UsbAccessory chosen=null;
        for(UsbAccessory accessory:accessories){
            Trace.log("USB_DISCOVERED","manufacturer="+accessory.getManufacturer()
                    +" model="+accessory.getModel()+" version="+accessory.getVersion());
            if("WeLink".equals(accessory.getModel()))chosen=accessory;
        }
        if(chosen==null){toast(getString(R.string.toast_usb_unsupported));return;}
        if(manager.hasPermission(chosen)){connect(chosen);return;}
        PendingIntent permission=PendingIntent.getBroadcast(this,0,
                new Intent(USB_PERMISSION).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
        manager.requestPermission(chosen,permission);
    }

    private void connect(UsbAccessory accessory){
        startForegroundService(new Intent(this,LinkService.class)
                .setAction(LinkService.CONNECT)
                .putExtra(UsbManager.EXTRA_ACCESSORY,accessory));
    }

    private void requestCapture(boolean local){
        if(!Status.touchReady){toast(getString(R.string.toast_accessibility_missing));return;}
        if(Status.capturing){
            if(!local)startActivity(CarHomeActivity.intent(this));
            else toast(getString(R.string.toast_already_running));
            return;
        }
        if(local && Status.usbOpen){toast(getString(R.string.toast_usb_stop_first));return;}
        if(!local && !(Status.usbOpen && Status.configured)){toast(getString(R.string.toast_car_wait));return;}
        captureForLocal=local;
        MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
        Intent request=Build.VERSION.SDK_INT>=34
                ?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                :manager.createScreenCaptureIntent();
        startActivityForResult(request,REQUEST_CAPTURE);
    }

    private void openMap(String packageName){
        if(!Status.capturing){toast(getString(R.string.capture_first));return;}
        Intent launch=getPackageManager().getLaunchIntentForPackage(packageName);
        if(launch==null){toast(getString(R.string.toast_map_missing));return;}
        if(Status.usbOpen)startService(new Intent(this,LinkService.class)
                .setAction(LinkService.MAP).putExtra("package",packageName));
        try{startActivity(launch);}
        catch(ActivityNotFoundException e){toast(getString(R.string.toast_map_error));}
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQUEST_CAPTURE){
            if(result!=RESULT_OK || data==null){
                Trace.log("CAPTURE_DENIED","Screen sharing not granted");
                render();
                return;
            }
            if(!Status.touchReady){toast(getString(R.string.toast_accessibility_missing));render();return;}
            settings().edit().putBoolean(AppSettings.TOUCH,true).apply();
            Status.showCarHome=true;
            startForegroundService(new Intent(this,LinkService.class)
                    .setAction(LinkService.CAPTURE).putExtra(LinkService.GRANT,data)
                    .putExtra("result",result).putExtra("local",captureForLocal));
        }else if(request==REQUEST_EXPORT && result==RESULT_OK && data!=null && data.getData()!=null){
            final Uri destination=data.getData();
            new Thread(()->{
                try(OutputStream out=getContentResolver().openOutputStream(destination)){
                    if(out==null)throw new java.io.IOException("Cannot open destination");
                    Trace.export(out);
                    runOnUiThread(()->toast(getString(R.string.diagnostic_saved)));
                }catch(Exception e){
                    Trace.log("EXPORT_ERROR",e.toString());
                    runOnUiThread(()->toast(getString(R.string.diagnostic_failed)));
                }
            },"osc-export").start();
        }
    }

    private android.content.SharedPreferences settings(){
        return getSharedPreferences(AppSettings.FILE,MODE_PRIVATE);
    }
    private int dp(float value){return AppUi.dp(this,value);}
    private int color(int resource){return getColor(resource);}
    private void toast(CharSequence message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}

    @Override protected void onSaveInstanceState(Bundle state){
        state.putBoolean("captureForLocal",captureForLocal);
        super.onSaveInstanceState(state);
    }
    @Override protected void onResume(){super.onResume();render();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(receiverRegistered)unregisterReceiver(usbReceiver);
        super.onDestroy();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(intent.getAction()))
            handler.postDelayed(this::findAccessory,350);
    }
}
