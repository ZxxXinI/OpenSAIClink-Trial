package org.opensaiclink.trial;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.*;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String USB_PERMISSION="org.opensaiclink.trial.USB_PERMISSION";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,logs;
    private Button captureButton,localButton;
    private boolean captureForLocal;
    private boolean permissionReceiverRegistered;
    private final Runnable refresh=new Runnable(){
        @Override public void run(){
            if(captureButton!=null)captureButton.setEnabled(Status.touchReady && !Status.capturing);
            if(localButton!=null)localButton.setEnabled(Status.touchReady && !Status.capturing);
            status.setText(getString(R.string.status_format,Status.usbOpen?getString(R.string.yes):getString(R.string.no),
                    Status.configured?getString(R.string.yes):getString(R.string.no),
                    Status.capturing?getString(R.string.yes):getString(R.string.no),Status.encoded.get(),Status.videoWritten.get())
                    +"\n"+getString(R.string.touch_status,Status.touchReady?getString(R.string.yes):getString(R.string.no),Status.touchReceived.get(),Status.touchDispatched.get())
                    +"\n"+getString(R.string.rotation_status,Settings.System.canWrite(MainActivity.this)?getString(R.string.yes):getString(R.string.no))
                    +"\n"+Status.stage+"\n"+Status.captureStage);
            logs.setText(Trace.tail());handler.postDelayed(this,1000);
            if(Status.showCarHome && Status.capturing && Status.encoded.get()>0){
                Status.showCarHome=false;startActivity(CarHomeActivity.intent(MainActivity.this));
            }
        }
    };
    private final BroadcastReceiver usbReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(!USB_PERMISSION.equals(intent.getAction()))return;
            UsbAccessory accessory=intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY,UsbAccessory.class);
            UsbManager manager=getSystemService(UsbManager.class);
            if(accessory!=null && manager.hasPermission(accessory))connect(accessory);
            else{Trace.log("USB_PERMISSION_DENIED","User denied USB accessory access");toast(R.string.usb_denied);}
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Trace.init(this);
        ProjectionRotation.recover(this);
        if(state!=null)captureForLocal=state.getBoolean("captureForLocal",false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(16*getResources().getDisplayMetrics().density);
        body.setPadding(pad,pad,pad,pad);body.setBackgroundColor(Color.rgb(244,247,250));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            return insets;
        });
        WindowInsetsController systemBars=getWindow().getInsetsController();
        if(systemBars!=null)systemBars.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        TextView title=new TextView(this);title.setText(R.string.app_name);title.setTextSize(23);title.setTextColor(Color.rgb(20,45,70));body.addView(title);
        TextView hint=new TextView(this);hint.setText(R.string.instructions);hint.setPadding(0,12,0,12);body.addView(hint);
        status=new TextView(this);status.setTextSize(15);body.addView(status);
        addButton(body,R.string.touch_permission,view->openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addButton(body,R.string.connect,view->findAccessory());
        captureButton=addButton(body,R.string.capture,view->requestCapture(false));captureButton.setEnabled(Status.touchReady);
        addButton(body,R.string.car_home,view->{if(Status.capturing)startActivity(CarHomeActivity.intent(this));else toast(R.string.capture_first);});
        LinearLayout maps=new LinearLayout(this);body.addView(maps);
        addButton(maps,R.string.gaode,view->openMap("com.autonavi.minimap"));
        addButton(maps,R.string.baidu,view->openMap("com.baidu.BaiduMap"));
        addButton(body,R.string.pattern,view->{if(Status.capturing)startActivity(new Intent(this,PatternActivity.class));else toast(R.string.capture_first);});
        LinearLayout actions=new LinearLayout(this);body.addView(actions);
        addButton(actions,R.string.stop,view->stopService(new Intent(this,LinkService.class)));
        addButton(actions,R.string.export,view->export());

        addToggle(body,R.string.multitouch_option,"multitouch",true);
        addToggle(body,R.string.landscape_option,"landscape",true);
        addButton(body,R.string.rotation_permission,view->openSettings(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+getPackageName()))));
        addToggle(body,R.string.return_overlay_option,"return_overlay",true);
        addToggle(body,R.string.fill_display_option,"fill_display",true);
        addToggle(body,R.string.responsive_option,"responsive",true);
        localButton=addButton(body,R.string.local_test,view->requestCapture(true));localButton.setEnabled(Status.touchReady);
        TextView limits=new TextView(this);limits.setText(R.string.limits);limits.setPadding(0,12,0,12);body.addView(limits);
        logs=new TextView(this);logs.setTextSize(10);logs.setTextIsSelectable(true);body.addView(logs);
        registerReceiver(usbReceiver,new IntentFilter(USB_PERMISSION),Context.RECEIVER_NOT_EXPORTED);permissionReceiverRegistered=true;
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},5);
        if(UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(getIntent().getAction()))handler.postDelayed(this::findAccessory,500);
    }
    private void addToggle(LinearLayout parent,int label,String key,boolean initial){
        CheckBox box=new CheckBox(this);box.setText(label);
        box.setChecked(getSharedPreferences("trial",MODE_PRIVATE).getBoolean(key,initial));
        box.setOnCheckedChangeListener((button,checked)->{
            getSharedPreferences("trial",MODE_PRIVATE).edit().putBoolean(key,checked).apply();
            if("touch".equals(key) || "multitouch".equals(key))RemoteTouchService.releaseCurrent();
            if("return_overlay".equals(key))RemoteTouchService.refreshOrientation();
            if("landscape".equals(key) && !checked)ProjectionRotation.restore(this);
            if(Status.capturing && ("responsive".equals(key) || "fill_display".equals(key) || "landscape".equals(key)))toast(R.string.next_capture);
        });parent.addView(box);
    }
    private void openSettings(Intent intent){
        try{startActivity(intent);}catch(ActivityNotFoundException e){toast(R.string.settings_unavailable);}
    }
    private Button addButton(LinearLayout parent,int text,View.OnClickListener listener){
        Button button=new Button(this);button.setText(text);button.setOnClickListener(listener);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(
                parent.getOrientation()==LinearLayout.HORIZONTAL?0:ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,parent.getOrientation()==LinearLayout.HORIZONTAL?1:0);
        parent.addView(button,params);return button;
    }
    private void findAccessory(){
        if(Status.capturing && Status.localTest){toast(R.string.stop_local_first);return;}
        UsbManager manager=getSystemService(UsbManager.class);UsbAccessory[] accessories=manager.getAccessoryList();
        if(accessories==null || accessories.length==0){Trace.log("USB_NOT_FOUND","Connect phone directly to the car and select SAICLink");toast(R.string.usb_not_found);return;}
        UsbAccessory chosen=null;
        for(UsbAccessory a:accessories){
            Trace.log("USB_DISCOVERED","manufacturer="+a.getManufacturer()+" model="+a.getModel()+" version="+a.getVersion());
            if("WeLink".equals(a.getModel()))chosen=a;
        }
        if(chosen==null){Trace.log("USB_UNSUPPORTED","No WeLink accessory");toast(R.string.usb_unsupported);return;}
        if(manager.hasPermission(chosen)){connect(chosen);return;}
        PendingIntent permission=PendingIntent.getBroadcast(this,0,new Intent(USB_PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
        manager.requestPermission(chosen,permission);
    }
    private void connect(UsbAccessory accessory){
        startForegroundService(new Intent(this,LinkService.class).setAction(LinkService.CONNECT).putExtra(UsbManager.EXTRA_ACCESSORY,accessory));
    }
    private void requestCapture(boolean local){
        if(!Status.touchReady){toast(R.string.accessibility_required);return;}
        if(Status.capturing){toast(R.string.already_capturing);return;}
        if(local && Status.usbOpen){toast(R.string.stop_usb_first);return;}
        if(!local && !(Status.usbOpen && Status.configured)){toast(R.string.connect_first);return;}
        captureForLocal=local;
        MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
        Intent request=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();
        startActivityForResult(request,100);
    }
    private void openMap(String packageName){
        if(!Status.capturing){toast(R.string.capture_first);return;}
        Intent launch=getPackageManager().getLaunchIntentForPackage(packageName);
        if(launch==null){toast(R.string.map_not_found);return;}
        if(Status.usbOpen)startService(new Intent(this,LinkService.class).setAction(LinkService.MAP).putExtra("package",packageName));
        try{startActivity(launch);}catch(ActivityNotFoundException e){Trace.log("MAP_START_ERROR",e.toString());toast(R.string.map_not_found);}
    }
    private void export(){
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE,"OpenSAICLink-E6-"+System.currentTimeMillis()+".zip");
        startActivityForResult(save,101);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==100){
            if(result!=RESULT_OK || data==null){Trace.log("CAPTURE_DENIED","Screen sharing was not granted");return;}
            if(!Status.touchReady){toast(R.string.accessibility_required);return;}
            getSharedPreferences("trial",MODE_PRIVATE).edit().putBoolean("touch",true).apply();
            Status.showCarHome=true;
            startForegroundService(new Intent(this,LinkService.class).setAction(LinkService.CAPTURE)
                    .putExtra(LinkService.GRANT,data).putExtra("result",result).putExtra("local",captureForLocal));
        }else if(request==101 && result==RESULT_OK && data!=null && data.getData()!=null){
            new Thread(()->{
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                    if(out==null)throw new java.io.IOException("Cannot open destination");Trace.export(out);
                    runOnUiThread(()->toast(R.string.export_done));
                }catch(Exception e){Trace.log("EXPORT_ERROR",e.toString());}
            },"wl-export").start();
        }
    }
    private void toast(int resource){Toast.makeText(this,resource,Toast.LENGTH_LONG).show();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("captureForLocal",captureForLocal);super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(permissionReceiverRegistered)unregisterReceiver(usbReceiver);super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);
        if(UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(intent.getAction()))findAccessory();}
}
