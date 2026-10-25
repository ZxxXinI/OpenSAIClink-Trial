package org.opensaiclink.trial;
import android.Manifest;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.usb.*;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
public final class MainActivity extends Activity implements SessionController.Observer {
    private static final String USB_PERMISSION="org.opensaiclink.trial.USB_PERMISSION";
    private final OnBackInvokedCallback backCallback=()->leaveDraft(this::finish);
    private GlassUi ui;
    private SettingsStore settings;
    private LinearLayout root,body,nav;
    private int tab,picking;
    private HomeLayout draft,saved;
    private TextView connectionTitle,connectionHint,usbValue,touchValue,shareValue;
    private Button primary,stopButton;
    private boolean registered,resumed,autoHome,usbPending,exportVideo;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            if(!USB_PERMISSION.equals(i.getAction()))return;
            usbPending=false;
            UsbAccessory a=i.getParcelableExtra(UsbManager.EXTRA_ACCESSORY,UsbAccessory.class);
            if(a!=null && getSystemService(UsbManager.class).hasPermission(a))connect(a);
            else toast("\u672a\u83b7\u5f97 USB \u6388\u6743\uff0c\u8bf7\u91cd\u65b0\u8fde\u63a5");
            updateConnection(SessionController.state.snapshot());
        }
    }
    ;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
        Trace.init(this);
        ProjectionRotation.recover(this);
        try{
            settings=new SettingsStore(this);
            saved=settings.layout();
            draft=saved;
        }
        catch(Exception e){
            new AlertDialog.Builder(this).setTitle("\u8bbe\u7f6e\u6682\u65f6\u65e0\u6cd5\u8bfb\u53d6").setMessage("\u539f\u914d\u7f6e\u5df2\u4fdd\u7559\uff0c\u8bf7\u5173\u95ed\u540e\u91cd\u65b0\u6253\u5f00\u5e94\u7528\u91cd\u8bd5\u3002").setPositiveButton("\u5173\u95ed",(d,w)->finish()).setCancelable(false).show();
            return;
        }
        if(state!=null){
            tab=state.getInt("tab");
            picking=state.getInt("picking");
            autoHome=state.getBoolean("autoHome");
            exportVideo=state.getBoolean("exportVideo");
            try{
                draft=HomeLayout.decode(state.getString("draft",saved.encode()));
                saved=HomeLayout.decode(state.getString("saved",saved.encode()));
            }
            catch(Exception ignored){
                draft=saved=settings.layout();
            }
        }
        else tab=getIntent().getIntExtra("tab",0);
        registerReceiver(receiver,new IntentFilter(USB_PERMISSION),Context.RECEIVER_NOT_EXPORTED);
        registered=true;
        render();
        if(UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(getIntent().getAction()))getWindow().getDecorView().post(this::findAccessory);
    }
    private void render(){
        if(settings==null)return;
        ui=new GlassUi(this);
        connectionTitle=null;
        primary=null;
        root=ui.column();
        ui.install(this,root);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        body=ui.column();
        body.setPadding(ui.dp(20),ui.dp(18),ui.dp(20),ui.dp(18));
        scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        ui.add(body,ui.text("OpenSAICLink",18,ui.ink),-2,0);
        if(tab==0)connectionPage();
        else if(tab==1)appsPage();
        else settingsPage();
        nav=new LinearLayout(this);
        nav.setPadding(ui.dp(10),ui.dp(6),ui.dp(10),ui.dp(8));
        nav.setBackground(ui.glass(false));
        String[] labels={
            "\u8fde\u63a5","\u5e94\u7528","\u8bbe\u7f6e"
        }
        ;
        for(int i=0;i<3;i++){
            final int selected=i;
            Button b=ui.button(labels[i],tab==i,v->switchTab(selected));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ui.dp(50),1);
            lp.setMargins(ui.dp(4),0,ui.dp(4),0);
            nav.addView(b,lp);
        }
        root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        updateConnection(SessionController.state.snapshot());
    }
    private void heading(String title,String hint){
        ui.add(body,ui.text(title,30,ui.ink),-2,22);
        if(!hint.isEmpty())ui.add(body,ui.text(hint,14,ui.muted),-2,8);
    }
    private void connectionPage(){
        connectionTitle=ui.text("\u8fde\u63a5\u4f60\u7684\u8f66\u673a",30,ui.ink);
        ui.add(body,connectionTitle,-2,22);
        connectionHint=ui.text("",14,ui.muted);
        ui.add(body,connectionHint,-2,8);
        LinearLayout hero=ui.card();
        TextView symbol=ui.text("\u624b\u673a  \u2500\u2500\u2500  \u8f66\u673a",25,ui.accent);
        symbol.setGravity(Gravity.CENTER);
        symbol.setPadding(0,ui.dp(24),0,ui.dp(24));
        hero.addView(symbol);
        TextView sub=ui.text("\u4f7f\u7528\u624b\u673a\u4e0a\u7684\u5730\u56fe\u4e0e\u5e38\u7528\u5e94\u7528",15,ui.muted);
        sub.setGravity(Gravity.CENTER);
        hero.addView(sub);
        ui.add(body,hero,-2,22);
        LinearLayout checks=ui.card();
        usbValue=checkRow(checks,"USB \u4e0e\u8f66\u673a",this::findAccessory);
        touchValue=checkRow(checks,"\u8f66\u673a\u89e6\u63a7 \u00b7 \u5fc5\u9700",()->openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        shareValue=checkRow(checks,"\u5c4f\u5e55\u5171\u4eab \u00b7 \u5fc5\u9700",null);
        ui.add(body,checks,-2,16);
        primary=ui.button("\u8fde\u63a5\u8f66\u673a",true,v->{
            SessionState.Snapshot s=SessionController.state.snapshot();
            if(s.projecting())startActivity(CarHomeActivity.intent(this));
            else if(!s.touch)openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            else if(!s.usb)findAccessory();
            else if(s.canStart())requestCapture();
        }
        );
        ui.add(body,primary,56,20);
        stopButton=ui.button("\u505c\u6b62\u6295\u5c4f",false,v->stopService(new Intent(this,LinkService.class)));
        ui.add(body,stopButton,50,10);
        ui.add(body,ui.button("\u9884\u89c8\u8f66\u8f7d\u4e3b\u9875",false,v->startActivity(CarHomeActivity.intent(this).putExtra("preview",true))),50,14);
        TextView usageNote=ui.text("\u4f7f\u7528\u8bf4\u660e\uff1a\u672c\u5e94\u7528\u76ee\u524d\u5904\u4e8e\u8bd5\u9a8c\u9636\u6bb5\u3002\u6295\u5c4f\u65f6\uff0c\u90e8\u5206\u56fe\u5c42\u4e0d\u4f1a\u5728\u8f66\u673a\u4e0a\u663e\u793a\uff1b\u90e8\u5206\u5e94\u7528\u9700\u8981\u5f00\u542f\u624b\u673a\u7684\u201c\u81ea\u52a8\u65cb\u8f6c\u5c4f\u5e55\u201d\u529f\u80fd\u3002",12,ui.muted);
        usageNote.setLineSpacing(ui.dp(3),1f);
        ui.add(body,usageNote,-2,12);
    }
    private TextView checkRow(LinearLayout parent,String title,Runnable action){
        LinearLayout r=new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0,ui.dp(15),0,ui.dp(15));
        r.addView(ui.text(title,15,ui.ink),new LinearLayout.LayoutParams(0,-2,1));
        TextView val=ui.text("",14,ui.accent);
        r.addView(val);
        if(action!=null)r.setOnClickListener(v->action.run());
        parent.addView(r);
        return val;
    }
    private void updateConnection(SessionState.Snapshot s){
        if(connectionTitle==null)return;
        connectionTitle.setText(SessionController.label(s));
        connectionHint.setText(!s.message.isEmpty()?s.message:s.projecting()?"\u624b\u673a\u5e94\u7528\u6b63\u5728\u663e\u793a\u5230\u8f66\u673a\uff0c\u968f\u65f6\u53ef\u8fd4\u56de\u4e3b\u9875\u3002":s.stage==SessionState.Stage.AUTHORIZING?"\u8bf7\u5728\u7cfb\u7edf\u5f39\u7a97\u4e2d\u9009\u62e9\u5171\u4eab\u6574\u4e2a\u5c4f\u5e55\u3002":"\u5148\u5f00\u542f\u8f66\u673a\u89e6\u63a7\uff0c\u518d\u8fde\u63a5\u6570\u636e\u7ebf\u5e76\u5f00\u59cb\u6295\u5c4f\u3002");
        usbValue.setText(s.configured?"\u5df2\u5c31\u7eea":s.usb?"\u7b49\u5f85\u8f66\u673a\u54cd\u5e94":usbPending?"\u7b49\u5f85\u6388\u6743":"\u672a\u8fde\u63a5");
        touchValue.setText(s.touch?"\u5df2\u5f00\u542f":"\u53bb\u5f00\u542f \u203a");
        shareValue.setText(s.projecting()?"\u5171\u4eab\u4e2d":s.stage==SessionState.Stage.AUTHORIZING?"\u7b49\u5f85\u6388\u6743":"\u5f00\u59cb\u65f6\u6388\u6743");
        boolean busy=s.stage==SessionState.Stage.CONNECTING||s.stage==SessionState.Stage.AUTHORIZING||s.stage==SessionState.Stage.STARTING||s.stage==SessionState.Stage.STOPPING||usbPending;
        primary.setText(s.projecting()?"\u8fd4\u56de\u8f66\u8f7d\u4e3b\u9875":!s.touch?"\u5f00\u542f\u8f66\u673a\u89e6\u63a7":s.canStart()?"\u5f00\u59cb\u6295\u5c4f":busy?"\u8bf7\u7a0d\u5019\u2026":"\u8fde\u63a5\u8f66\u673a");
        primary.setEnabled(!busy);
        primary.setAlpha(busy?.45f:1f);
        boolean active=s.usb||s.stage==SessionState.Stage.CONNECTING||s.projecting();
        stopButton.setVisibility(active?View.VISIBLE:View.GONE);
        stopButton.setEnabled(s.stage!=SessionState.Stage.STOPPING);
    }
    private boolean dirty(){
        return draft!=null&&!draft.equals(saved);
    }
    private void appsPage(){
        heading("\u8f66\u8f7d\u5e94\u7528","\u516b\u4e2a\u5e94\u7528\u4f4d\u7531\u4f60\u5b89\u6392\uff0c\u957f\u6309\u62d6\u52a8\u6392\u5e8f");
        LinearLayout panel=ui.card();
        panel.addView(ui.text("\u4e3b\u9875\u5e03\u5c40",17,ui.ink));
        for(int row=0;row<3;row++){
            LinearLayout line=new LinearLayout(this);
            ui.add(panel,line,112,8);
            for(int col=0;col<3;col++){
                final int index=row*3+col;
                View tile=ui.tile(draft.get(index),index,true,v->{
                    if(index==5)toast("\u5168\u90e8\u5e94\u7528\u4e3a\u56fa\u5b9a\u5165\u53e3");
                    else editCell(index);
                }
                ,index==5?null:v->{
                    v.startDragAndDrop(ClipData.newPlainText("cell",Integer.toString(index)),new View.DragShadowBuilder(v),Integer.valueOf(index),0);
                    return true;
                }
                );
                tile.setOnDragListener((v,event)->{
                    if(index==5 || !(event.getLocalState() instanceof Integer))return false;
                    switch(event.getAction()){
                        case DragEvent.ACTION_DRAG_ENTERED:v.setAlpha(.6f);
                        break;
                        case DragEvent.ACTION_DRAG_EXITED:case DragEvent.ACTION_DRAG_ENDED:v.setAlpha(1);
                        break;
                        case DragEvent.ACTION_DROP:int from=(Integer)event.getLocalState();
                        draft=draft.move(from,index);
                        v.post(this::render);
                        break;
                    }
                    return true;
                }
                );
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
                lp.setMargins(ui.dp(3),0,ui.dp(3),0);
                line.addView(tile,lp);
            }
        }
        ui.add(body,panel,-2,20);
        ui.add(body,ui.text(dirty()?"\u5e03\u5c40\u6709\u672a\u4fdd\u5b58\u7684\u4fee\u6539":"\u70b9\u51fb\u5e94\u7528\u4f4d\u66f4\u6362\uff0c\u957f\u6309\u62d6\u52a8\u5230\u65b0\u4f4d\u7f6e",13,ui.muted),-2,14);
        ui.add(body,ui.button("\u4fdd\u5b58\u5e03\u5c40",true,v->{
            if(saveDraft())toast("\u4e3b\u9875\u5e03\u5c40\u5df2\u4fdd\u5b58");
            render();
        }
        ),54,14);
        ui.add(body,ui.button("\u9884\u89c8\u5df2\u4fdd\u5b58\u7684\u4e3b\u9875",false,v->leaveDraft(()->startActivity(CarHomeActivity.intent(this).putExtra("preview",true)))),50,10);
    }
    private void editCell(int index){
        picking=index;
        new AlertDialog.Builder(this).setTitle("\u5e94\u7528\u4f4d\u7f6e "+(index+1)).setItems(new String[]{
            "\u9009\u62e9\u5e94\u7528","\u6e05\u7a7a\u4f4d\u7f6e"
        }
        ,(d,w)->{
            if(w==0)startActivityForResult(new Intent(this,AppPickerActivity.class).putExtra("pick",true),200);
            else {
                draft=draft.with(index,"");
                render();
            }
        }
        ).show();
    }
    private boolean saveDraft(){
        if(!settings.save(draft)){
            toast("\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5");
            return false;
        }
        saved=draft;
        return true;
    }
    private void leaveDraft(Runnable next){
        if(!dirty()){
            next.run();
            return;
        }
        new AlertDialog.Builder(this).setTitle("\u4fdd\u5b58\u5e03\u5c40\u4fee\u6539\uff1f").setMessage("\u79bb\u5f00\u524d\u53ef\u4ee5\u4fdd\u5b58\u6216\u653e\u5f03\u8fd9\u6b21\u4fee\u6539\u3002").setPositiveButton("\u4fdd\u5b58",(d,w)->{
            if(saveDraft())next.run();
        }
        ).setNegativeButton("\u653e\u5f03",(d,w)->{
            draft=saved=settings.layout();
            next.run();
        }
        ).setNeutralButton("\u53d6\u6d88",null).show();
    }
    private void switchTab(int next){
        if(tab==next)return;
        leaveDraft(()->{
            tab=next;
            if(tab==1)draft=saved=settings.layout();
            render();
        }
        );
    }
    private void settingsPage(){
        heading("\u8bbe\u7f6e","");
        LinearLayout display=section("\u663e\u793a\u4e0e\u64cd\u4f5c");
        display.addView(ui.row("\u4e3b\u9898",themeLabel(),v->new AlertDialog.Builder(this).setTitle("\u4e3b\u9898").setSingleChoiceItems(new String[]{
            "\u8ddf\u968f\u7cfb\u7edf","\u6df1\u8272","\u6d45\u8272"
        }
        ,"dark".equals(settings.theme())?1:"light".equals(settings.theme())?2:0,(d,w)->{
            settings.theme(new String[]{
                "system","dark","light"
            }
            [w]);
            d.dismiss();
            render();
        }
        ).show()));
        display.addView(ui.row("\u753b\u9762\u6bd4\u4f8b",settings.option("fill_display",false)?"\u94fa\u6ee1\u8f66\u673a":"\u5b8c\u6574\u663e\u793a",v->new AlertDialog.Builder(this).setTitle("\u753b\u9762\u6bd4\u4f8b").setSingleChoiceItems(new String[]{
            "\u5b8c\u6574\u663e\u793a\uff08\u4fdd\u7559\u6bd4\u4f8b\uff09","\u94fa\u6ee1\u8f66\u673a\uff08\u53ef\u80fd\u62c9\u5bbd\u753b\u9762\uff09"
        }
        ,settings.option("fill_display",false)?1:0,(d,w)->{
            settings.setOption("fill_display",w==1);
            d.dismiss();
            nextCaptureNotice();
            render();
        }
        ).show()));
        toggle(display,"\u60ac\u6d6e\u4e3b\u9875\u6309\u94ae","return_overlay",true,false);
        toggle(display,"\u53cc\u6307\u7f29\u653e","multitouch",true,false);
        toggle(display,"\u4f18\u5148\u4f4e\u5ef6\u8fdf","responsive",true,true);
        toggle(display,"\u6295\u5c4f\u65f6\u81ea\u52a8\u65cb\u8f6c","landscape",true,true);
        LinearLayout connection=section("\u8fde\u63a5");
        connection.addView(ui.row("\u6743\u9650\u7ba1\u7406","",v->permissions()));
        connection.addView(ui.row("\u58f0\u97f3\u4f7f\u7528\u8bf4\u660e","",v->message("\u58f0\u97f3\u4f7f\u7528\u8bf4\u660e","\u58f0\u97f3\u6cbf\u7528\u624b\u673a\u4e0e\u8f66\u673a\u7684\u84dd\u7259\u8fde\u63a5\u3002\u5148\u5728\u8f66\u673a\u6253\u5f00\u84dd\u7259\u97f3\u4e50\uff0c\u518d\u64ad\u653e\u624b\u673a\u97f3\u4e50\u6216\u5bfc\u822a\u8bed\u97f3\u3002\u97f3\u4e50\u5165\u53e3\u7528\u4e8e\u6253\u5f00\u4f60\u9009\u62e9\u7684\u624b\u673a\u5e94\u7528\u3002")));
        LinearLayout help=section("\u5e2e\u52a9\u4e0e\u8bca\u65ad");
        help.addView(ui.row("\u4f7f\u7528\u6307\u5357","",v->message("\u4f7f\u7528\u6307\u5357","1. \u5f00\u542f\u8f66\u673a\u89e6\u63a7\u5e76\u8fde\u63a5\u6570\u636e\u7ebf\u3002\n2. \u70b9\u51fb\u5f00\u59cb\u6295\u5c4f\uff0c\u5728\u7cfb\u7edf\u5f39\u7a97\u6388\u6743\u5171\u4eab\u6574\u4e2a\u5c4f\u5e55\u3002\n3. \u8fdb\u5165\u5730\u56fe\u6216\u5176\u4ed6\u5e94\u7528\uff0c\u70b9\u60ac\u6d6e\u4e3b\u9875\u8fd4\u56de\u3002\n4. \u505c\u6b62\u6295\u5c4f\u540e\u6062\u590d\u624b\u673a\u65b9\u5411\u3002\n\n\u624b\u673a\u539f\u684c\u9762\u4fdd\u6301\u7cfb\u7edf\u539f\u6765\u7684\u65b9\u5411\u3002")));
        Switch record=new Switch(this);
        record.setText("\u8bb0\u5f55\u4e0b\u4e00\u6b21\u6295\u5c4f\u95ee\u9898");
        record.setTextColor(ui.ink);
        record.setTextSize(15);
        record.setPadding(ui.dp(4),ui.dp(16),ui.dp(4),ui.dp(16));
        record.setChecked(settings.recordingArmed());
        record.setOnCheckedChangeListener((b,on)->{
            if(on)new AlertDialog.Builder(this).setTitle("\u5305\u542b\u5c4f\u5e55\u5f55\u5236\u7247\u6bb5\uff1f").setMessage("\u4e0b\u4e00\u6b21\u6295\u5c4f\u4f1a\u4fdd\u5b58\u6700\u591a 10 MiB \u7684\u753b\u9762\u7247\u6bb5\uff0c\u53ef\u80fd\u5305\u542b\u5c4f\u5e55\u4e0a\u7684\u4e2a\u4eba\u5185\u5bb9\u3002\u7ed3\u675f\u540e\u81ea\u52a8\u5173\u95ed\uff0c\u53ef\u5728\u5bfc\u51fa\u65f6\u9009\u62e9\u662f\u5426\u5305\u542b\u7247\u6bb5\u3002").setPositiveButton("\u5f00\u542f",(d,w)->settings.armRecording(true)).setNegativeButton("\u53d6\u6d88",(d,w)->{
                settings.armRecording(false);
                record.setChecked(false);
            }
            ).setOnCancelListener(d->{
                settings.armRecording(false);
                record.setChecked(false);
            }
            ).show();
            else settings.armRecording(false);
        }
        );
        help.addView(record);
        help.addView(ui.row("\u5bfc\u51fa\u8bca\u65ad",Trace.hasVideo()?"\u53ef\u9009\u5f55\u5c4f":"\u4ec5\u65e5\u5fd7",v->exportDialog()));
        help.addView(ui.row("\u5173\u4e8e","1.0.0-rc3",v->message("OpenSAICLink","\u7248\u672c 1.0.0-rc3\n\u9762\u5411\u5df2\u9a8c\u8bc1\u7684 K80 / A7 \u8fde\u63a5\u65b9\u5f0f\u3002\n\u4ec5\u5728\u4f60\u6388\u6743\u540e\u5171\u4eab\u5c4f\u5e55\u548c\u6267\u884c\u8f66\u673a\u89e6\u63a7\u3002")));
    }
    private LinearLayout section(String name){
        LinearLayout card=ui.card();
        ui.add(card,ui.text(name,14,ui.muted),-2,0);
        ui.add(body,card,-2,18);
        return card;
    }
    private String themeLabel(){
        return "dark".equals(settings.theme())?"\u6df1\u8272":"light".equals(settings.theme())?"\u6d45\u8272":"\u8ddf\u968f\u7cfb\u7edf";
    }
    private void toggle(LinearLayout parent,String label,String key,boolean fallback,boolean next){
        Switch s=new Switch(this);
        s.setText(label);
        s.setTextColor(ui.ink);
        s.setTextSize(15);
        s.setPadding(ui.dp(4),ui.dp(15),ui.dp(4),ui.dp(15));
        s.setChecked(settings.option(key,fallback));
        s.setOnCheckedChangeListener((b,on)->{
            settings.setOption(key,on);
            if("return_overlay".equals(key))RemoteTouchService.refreshOrientation();
            if("multitouch".equals(key))RemoteTouchService.releaseCurrent();
            if("landscape".equals(key)&&!on)ProjectionRotation.restore(this);
            if(next)nextCaptureNotice();
        }
        );
        parent.addView(s);
    }
    private void nextCaptureNotice(){
        if(Status.capturing)toast("\u5df2\u4fdd\u5b58\uff0c\u4e0b\u6b21\u6295\u5c4f\u751f\u6548");
    }
    private void permissions(){
        new AlertDialog.Builder(this).setTitle("\u6743\u9650\u7ba1\u7406").setItems(new String[]{
            "\u8f66\u673a\u89e6\u63a7\uff08\u65e0\u969c\u788d\uff0c\u5fc5\u9700\uff09","\u81ea\u52a8\u65cb\u8f6c\uff08\u4fee\u6539\u7cfb\u7edf\u8bbe\u7f6e\uff09","\u6295\u5c4f\u901a\u77e5"
        }
        ,(d,w)->{
            if(w==0)openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            else if(w==1)openSettings(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+getPackageName())));
            else if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{
                Manifest.permission.POST_NOTIFICATIONS
            }
            ,12);
            else openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        }
        ).show();
    }
    private void message(String title,String text){
        new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("\u77e5\u9053\u4e86",null).show();
    }
    private void openSettings(Intent i){
        try{
            startActivity(i);
        }
        catch(Exception e){
            toast("\u8bf7\u5728\u7cfb\u7edf\u8bbe\u7f6e\u4e2d\u624b\u52a8\u6253\u5f00\u5bf9\u5e94\u6743\u9650");
        }
    }
    private void toast(String value){
        Toast.makeText(this,value,Toast.LENGTH_SHORT).show();
    }
    private void findAccessory(){
        SessionState.Snapshot s=SessionController.state.snapshot();
        if(s.stage==SessionState.Stage.STOPPING||s.stage==SessionState.Stage.CONNECTING||s.projecting()||s.stage==SessionState.Stage.AUTHORIZING||s.stage==SessionState.Stage.STARTING)return;
        UsbManager manager=getSystemService(UsbManager.class);
        UsbAccessory[] all=manager.getAccessoryList();
        if(all==null||all.length==0){
            toast("\u8bf7\u7528\u6570\u636e\u7ebf\u8fde\u63a5\u8f66\u673a\u5e76\u6253\u5f00 SAICLink");
            return;
        }
        UsbAccessory chosen=null;
        for(UsbAccessory a:all)if("WeLink".equals(a.getModel()))chosen=a;
        if(chosen==null){
            toast("\u672a\u53d1\u73b0\u652f\u6301\u7684\u8f66\u673a\u8fde\u63a5");
            return;
        }
        if(manager.hasPermission(chosen)){
            connect(chosen);
            return;
        }
        usbPending=true;
        updateConnection(s);
        manager.requestPermission(chosen,PendingIntent.getBroadcast(this,0,new Intent(USB_PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE));
    }
    private void connect(UsbAccessory a){
        if(!SessionController.state.connect())return;
        SessionController.publish();
        try{
            startForegroundService(new Intent(this,LinkService.class).setAction(LinkService.CONNECT).putExtra(UsbManager.EXTRA_ACCESSORY,a));
        }
        catch(Exception e){
            SessionController.state.ended(SessionState.Stage.ERROR,"\u8fde\u63a5\u672a\u542f\u52a8\uff0c\u8bf7\u56de\u5230\u5e94\u7528\u91cd\u8bd5");
            SessionController.publish();
            Trace.log("CONNECT_REQUEST_ERROR",e.toString());
        }
    }
    private void requestCapture(){
        if(!SessionController.state.authorize()){
            toast("\u8bf7\u5148\u8fde\u63a5\u8f66\u673a\u5e76\u5f00\u542f\u8f66\u673a\u89e6\u63a7");
            return;
        }
        SessionController.publish();
        try{
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
            startActivityForResult(manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()),100);
        }
        catch(Exception e){
            SessionController.state.cancelAuthorization();
            SessionController.publish();
            toast("\u65e0\u6cd5\u6253\u5f00\u5c4f\u5e55\u5171\u4eab\u6388\u6743");
        }
    }
    private void exportDialog(){
        CheckBox include=new CheckBox(this);
        include.setText("\u5305\u542b\u4e0a\u6b21\u5f55\u5c4f\u7247\u6bb5\uff08\u6700\u591a 10 MiB\uff09");
        include.setEnabled(Trace.hasVideo());
        new AlertDialog.Builder(this).setTitle("\u5bfc\u51fa\u8bca\u65ad").setMessage("\u9ed8\u8ba4\u53ea\u5bfc\u51fa\u8fde\u63a5\u65e5\u5fd7\u3002\u52fe\u9009\u540e\u5c06\u9644\u5e26\u4e0a\u6b21\u624b\u52a8\u8bb0\u5f55\u7684\u5c4f\u5e55\u7247\u6bb5\u3002").setView(include).setPositiveButton("\u9009\u62e9\u4fdd\u5b58\u4f4d\u7f6e",(d,w)->{
            exportVideo=include.isChecked();
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"OpenSAICLink-diagnostic-"+System.currentTimeMillis()+".zip"),101);
        }
        ).setNegativeButton("\u53d6\u6d88",null).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==200&&result==RESULT_OK&&data!=null){
            String pkg=data.getStringExtra("package");
            if(pkg!=null){
                draft=draft.with(picking,pkg);
                render();
            }
        }
        else if(request==100){
            SessionState.Snapshot s=SessionController.state.snapshot();
            if(result!=RESULT_OK||data==null){
                SessionController.state.cancelAuthorization();
                SessionController.publish();
                return;
            }
            if(s.stage!=SessionState.Stage.AUTHORIZING||!s.touch||!s.configured||!s.usb){
                SessionController.state.cancelAuthorization();
                SessionController.publish();
                toast("\u8fde\u63a5\u6216\u89e6\u63a7\u72b6\u6001\u5df2\u53d8\u5316\uff0c\u8bf7\u91cd\u65b0\u5f00\u59cb");
                return;
            }
            autoHome=true;
            try{
                startForegroundService(new Intent(this,LinkService.class).setAction(LinkService.CAPTURE).putExtra(LinkService.GRANT,data).putExtra("result",result));
            }
            catch(Exception e){
                autoHome=false;
                SessionController.state.cancelAuthorization();
                SessionController.publish();
                toast("\u6295\u5c4f\u672a\u542f\u52a8\uff0c\u8bf7\u91cd\u8bd5");
            }
        }
        else if(request==101&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            final boolean include=exportVideo;
            Uri destination=data.getData();
            new Thread(()->{
                try(OutputStream out=getContentResolver().openOutputStream(destination)){
                    if(out==null)throw new IOException("No destination");
                    Trace.export(out,include);
                    runOnUiThread(()->toast("\u8bca\u65ad\u5305\u5df2\u4fdd\u5b58"));
                }
                catch(Exception e){
                    Trace.log("EXPORT_ERROR",e.toString());
                    runOnUiThread(()->toast("\u5bfc\u51fa\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"));
                }
            }
            ,"diagnostic-export").start();
        }
    }
    public void changed(SessionState.Snapshot s){
        updateConnection(s);
        if(resumed&&autoHome&&s.projecting()){
            autoHome=false;
            startActivity(CarHomeActivity.intent(this));
        }
        if(s.stage==SessionState.Stage.ERROR||s.stage==SessionState.Stage.DISCONNECTED||s.stage==SessionState.Stage.IDLE)autoHome=false;
    }
    @Override protected void onResume(){
        super.onResume();
        resumed=true;
        if(settings!=null){
            if(!dirty())draft=saved=settings.layout();
            render();
            SessionController.observe(this);
        }
    }
    @Override protected void onPause(){
        resumed=false;
        SessionController.remove(this);
        super.onPause();
    }
    @Override public void onConfigurationChanged(Configuration cfg){
        super.onConfigurationChanged(cfg);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle b){
        super.onSaveInstanceState(b);
        b.putInt("tab",tab);
        b.putInt("picking",picking);
        b.putBoolean("autoHome",autoHome);
        b.putBoolean("exportVideo",exportVideo);
        if(draft!=null){
            b.putString("draft",draft.encode());
            b.putString("saved",saved.encode());
        }
    }
    @Override protected void onDestroy(){
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        SessionController.remove(this);
        if(registered)unregisterReceiver(receiver);
        super.onDestroy();
    }
    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);
        setIntent(i);
        if(i.hasExtra("tab"))leaveDraft(()->{
            tab=i.getIntExtra("tab",0);
            render();
        }
        );
        if(UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(i.getAction()))findAccessory();
    }
}
