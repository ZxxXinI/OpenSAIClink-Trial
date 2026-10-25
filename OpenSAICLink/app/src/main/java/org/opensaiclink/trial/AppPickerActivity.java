package org.opensaiclink.trial;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.util.*;
import java.text.Collator;
public final class AppPickerActivity extends Activity {
    private static final class Entry {
        final String pkg,name;
        Entry(String p,String n){
            pkg=p;
            name=n;
        }
    }
    private final List<Entry> all=new ArrayList<>(),filtered=new ArrayList<>();
    private boolean disposed,pick,preview;
    private GlassUi ui;
    private BaseAdapter adapter;
    private TextView count;
    private EditText search;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(Status.capturing)setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        pick=getIntent().getBooleanExtra("pick",true);
        preview=getIntent().getBooleanExtra("preview",false);
        ui=new GlassUi(this);
        LinearLayout root=ui.column();
        ui.install(this,root);
        LinearLayout body=ui.column();
        body.setPadding(ui.dp(18),ui.dp(12),ui.dp(18),0);
        root.addView(body,new LinearLayout.LayoutParams(-1,-1));
        ui.add(body,ui.button("\u2039  "+(pick?"\u9009\u62e9\u5e94\u7528":"\u5168\u90e8\u5e94\u7528"),false,v->finish()),48,0);
        search=new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(ui.ink);
        search.setHintTextColor(ui.muted);
        search.setHint("\u641c\u7d22\u5e94\u7528\u540d\u79f0");
        search.setBackground(ui.glass(false));
        search.setPadding(ui.dp(14),0,ui.dp(14),0);
        ui.add(body,search,52,12);
        count=ui.text("\u6b63\u5728\u8bfb\u53d6\u5e94\u7528\u2026",13,ui.muted);
        ui.add(body,count,-2,8);
        ListView list=new ListView(this);
        list.setDivider(null);
        body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        adapter=new BaseAdapter(){
            public int getCount(){
                return filtered.size();
            }
            public Object getItem(int p){
                return filtered.get(p);
            }
            public long getItemId(int p){
                return p;
            }
            public View getView(int p,View old,ViewGroup parent){
                Entry e=filtered.get(p);
                TextView t=ui.text(e.name,18,ui.ink);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));
                t.setMinHeight(ui.dp(64));
                android.graphics.drawable.Drawable icon=ui.appIcon(e.pkg,0);
                icon.setBounds(0,0,ui.dp(36),ui.dp(36));
                t.setCompoundDrawables(icon,null,null,null);
                t.setCompoundDrawablePadding(ui.dp(14));
                return t;
            }
        }
        ;
        list.setAdapter(adapter);
        list.setOnItemClickListener((a,v,pos,id)->{
            Entry e=filtered.get(pos);
            if(pick){
                setResult(RESULT_OK,new Intent().putExtra("package",e.pkg));
                finish();
            }
            else if(Status.capturing)AppLauncher.open(this,e.pkg);
            else Toast.makeText(this,"\u8bf7\u8fde\u63a5\u8f66\u673a\u5e76\u5f00\u59cb\u6295\u5c4f",Toast.LENGTH_SHORT).show();
        }
        );
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){
            }
            public void onTextChanged(CharSequence s,int a,int before,int count){
                filter();
            }
            public void afterTextChanged(Editable e){
            }
        }
        );
        new Thread(()->{
            List<Entry> found=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            try{
                PackageManager pm=getPackageManager();
                for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){
                    ActivityInfo a=r.activityInfo;
                    if(a!=null&&a.enabled&&a.exported&&a.applicationInfo.enabled&&!a.packageName.equals(getPackageName())&&seen.add(a.packageName)&&pm.getLaunchIntentForPackage(a.packageName)!=null)found.add(new Entry(a.packageName,r.loadLabel(pm).toString()));
                }
                Collator collator=Collator.getInstance();
                found.sort((a,b2)->collator.compare(a.name,b2.name));
            }
            catch(Exception e){
                Trace.log("APP_LIST_ERROR",e.toString());
            }
            runOnUiThread(()->{
                if(!disposed){
                    all.addAll(found);
                    filter();
                }
            }
            );
        }
        ,"app-picker").start();
    }
    private void filter(){
        String q=search.getText().toString().toLowerCase(Locale.ROOT).trim();
        filtered.clear();
        for(Entry e:all)if(e.name.toLowerCase(Locale.ROOT).contains(q)||e.pkg.contains(q))filtered.add(e);
        adapter.notifyDataSetChanged();
        count.setText(filtered.isEmpty()?"\u6ca1\u6709\u627e\u5230\u5e94\u7528":"\u5171 "+filtered.size()+" \u4e2a\u5e94\u7528");
    }
    @Override protected void onDestroy(){
        disposed=true;
        super.onDestroy();
    }
}
