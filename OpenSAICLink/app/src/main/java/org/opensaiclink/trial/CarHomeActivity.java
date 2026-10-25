package org.opensaiclink.trial;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;
public final class CarHomeActivity extends Activity implements SessionController.Observer {
    private GlassUi ui;
    private SettingsStore settings;
    private boolean preview;
    private int picking;
    private TextView clock;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){
        public void run(){
            if(clock!=null)clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
            handler.postDelayed(this,30000);
        }
    }
    ;
    public static Intent intent(Context c){
        return new Intent(c,CarHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        preview=getIntent().getBooleanExtra("preview",false);
        if(b!=null)picking=b.getInt("picking");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        settings=new SettingsStore(this);
        render();
    }
    private void render(){
        ui=new GlassUi(this);
        LinearLayout outer=ui.column();
        ui.install(this,outer);
        LinearLayout body=ui.column();
        body.setPadding(ui.dp(18),ui.dp(6),ui.dp(18),ui.dp(8));
        outer.addView(body,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=ui.text("OpenSAICLink",23,ui.ink);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(ui.text(preview?"\u4e3b\u9875\u9884\u89c8":"\u25cf \u5df2\u8fde\u63a5",13,ui.accent));
        clock=ui.text("",20,ui.ink);
        clock.setPadding(ui.dp(18),0,ui.dp(18),0);
        header.addView(clock);
        Button settingsButton=ui.button("\u8bbe\u7f6e",false,v->startActivity(new Intent(this,MainActivity.class).putExtra("tab",2).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        header.addView(settingsButton,new LinearLayout.LayoutParams(ui.dp(78),ui.dp(42)));
        body.addView(header,new LinearLayout.LayoutParams(-1,ui.dp(52)));
        HomeLayout layout=settings.layout();
        for(int row=0;row<3;row++){
            LinearLayout line=new LinearLayout(this);
            body.addView(line,new LinearLayout.LayoutParams(-1,0,1));
            for(int col=0;col<3;col++){
                final int index=row*3+col;
                String pkg=layout.get(index);
                View tile=ui.tile(pkg,index,false,v->open(index,pkg),index==5?null:v->{
                    edit(index,pkg);
                    return true;
                }
                );
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
                lp.setMargins(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4));
                line.addView(tile,lp);
            }
        }
    }
    private void open(int i,String pkg){
        if(i==5){
            startActivity(new Intent(this,AppPickerActivity.class).putExtra("pick",false).putExtra("preview",preview));
            return;
        }
        if(pkg.isEmpty()){
            pick(i);
            return;
        }
        if(preview){
            edit(i,pkg);
            return;
        }
        AppLauncher.open(this,pkg);
    }
    private void pick(int i){
        picking=i;
        startActivityForResult(new Intent(this,AppPickerActivity.class).putExtra("pick",true),30);
    }
    private void edit(int i,String pkg){
        new AlertDialog.Builder(this).setTitle(ui.appName(pkg,i)).setItems(new String[]{
            "\u66f4\u6362\u5e94\u7528","\u6e05\u7a7a\u4f4d\u7f6e"
        }
        ,(d,which)->{
            if(which==0)pick(i);
            else {
                if(!settings.save(settings.layout().with(i,"")))Toast.makeText(this,"\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5",Toast.LENGTH_LONG).show();
                render();
            }
        }
        ).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==30&&result==RESULT_OK&&data!=null){
            String pkg=data.getStringExtra("package");
            if(pkg!=null&&!settings.save(settings.layout().with(picking,pkg)))Toast.makeText(this,"\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5",Toast.LENGTH_LONG).show();
            render();
        }
    }
    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);
        setIntent(i);
        preview=i.getBooleanExtra("preview",false);
        render();
        changed(SessionController.state.snapshot());
    }
    @Override protected void onSaveInstanceState(Bundle b){
        b.putInt("picking",picking);
        super.onSaveInstanceState(b);
    }
    @Override protected void onResume(){
        super.onResume();
        Status.carHomeVisible=true;
        RemoteTouchService.refreshOrientation();
        render();
        handler.post(tick);
        SessionController.observe(this);
    }
    @Override protected void onPause(){
        Status.carHomeVisible=false;
        RemoteTouchService.refreshOrientation();
        SessionController.remove(this);
        handler.removeCallbacks(tick);
        super.onPause();
    }
    public void changed(SessionState.Snapshot s){
        if(!preview&&!s.projecting()&&s.stage!=SessionState.Stage.STARTING){
            finish();
        }
    }
}
