package org.opensaiclink.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Shared landscape application picker with long-press slot management. */
public final class AppPickerActivity extends CarActivity {
    private static final class Entry {
        final String packageName;
        final CharSequence label;
        final Drawable icon;
        Entry(String packageName,CharSequence label,Drawable icon){
            this.packageName=packageName;this.label=label;this.icon=icon;
        }
    }
    private volatile boolean disposed;
    private boolean shortcut;
    private int shortcutSlot;
    private final List<Entry> entries=new ArrayList<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(isFinishing())return;
        shortcut=getIntent().getBooleanExtra("shortcut",false);
        shortcutSlot=Math.max(0,Math.min(3,getIntent().getIntExtra("shortcutSlot",0)));
        createPage(true);
        TextView title=heading(getString(shortcut?R.string.choose_app_slot:R.string.choose_app),22,TEXT);
        header.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView loading=text(getString(R.string.loading_apps),12,MUTED);
        loading.setPadding(dp(2),dp(10),0,dp(5));
        content.addView(loading,AppUi.matchWrap());
        ListView list=new ListView(this);
        list.setDividerHeight(dp(5));
        list.setVerticalScrollBarEnabled(true);
        content.addView(list,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        list.setOnItemClickListener((parent,view,position,id)->{
            if(disposed || position<0 || position>=entries.size())return;
            Entry chosen=entries.get(position);
            if(shortcut){
                settings().edit().putString(AppSettings.shortcutKey(shortcutSlot),chosen.packageName).apply();
                Trace.log("CAR_SHORTCUT_SAVED","slot="+shortcutSlot+" package="+chosen.packageName);
                Toast.makeText(this,getString(R.string.app_added_home),Toast.LENGTH_SHORT).show();
                finish();
            }else AppLauncher.open(this,chosen.packageName);
        });
        list.setOnItemLongClickListener((parent,view,position,id)->{
            if(!shortcut || disposed || position<0 || position>=entries.size())return false;
            String current=settings().getString(AppSettings.shortcutKey(shortcutSlot),"");
            Entry chosen=entries.get(position);
            if(!chosen.packageName.equals(current))return false;
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.app_slot_title,shortcutSlot))
                    .setItems(new CharSequence[]{getString(R.string.remove_slot),getString(R.string.change_app)},(dialog,which)->{
                        if(which==0){
                            settings().edit().remove(AppSettings.shortcutKey(shortcutSlot)).apply();
                            finish();
                        }
                    }).show();
            return true;
        });
        new Thread(()->{
            List<Entry> found=loadEntries();
            runOnUiThread(()->{
                if(disposed)return;
                entries.addAll(found);
                loading.setText(getString(entries.isEmpty()?R.string.no_openable_app:R.string.picker_hint));
                list.setAdapter(new BaseAdapter(){
                    @Override public int getCount(){return entries.size();}
                    @Override public Object getItem(int position){return entries.get(position);}
                    @Override public long getItemId(int position){return position;}
                    @Override public View getView(int position,View recycled,ViewGroup parent){
                        Entry entry=entries.get(position);
                        LinearLayout row=AppUi.row(AppPickerActivity.this);
                        row.setPadding(dp(12),dp(7),dp(12),dp(7));
                        row.setBackground(AppUi.interactive(AppPickerActivity.this,SURFACE,
                                0xff454B4E,9,0x28FFFFFF));
                        ImageView icon=new ImageView(AppPickerActivity.this);
                        icon.setImageDrawable(entry.icon);
                        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        row.addView(icon,AppUi.fixed(dp(30),dp(30)));
                        TextView name=text(entry.label,14,TEXT);
                        name.setPadding(dp(11),0,0,0);
                        name.setSingleLine(true);
                        name.setEllipsize(TextUtils.TruncateAt.END);
                        row.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                        TextView arrow=text("\u203a",20,MUTED);
                        arrow.setGravity(Gravity.CENTER);
                        row.addView(arrow,AppUi.fixed(dp(26),dp(30)));
                        return row;
                    }
                });
                Trace.log("CAR_APPS_READY","count="+entries.size()+" shortcut="+shortcut);
            });
        },"osc-app-list").start();
    }

    private List<Entry> loadEntries(){
        List<Entry> found=new ArrayList<>();
        HashSet<String> seen=new HashSet<>();
        try{
            PackageManager manager=getPackageManager();
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for(ResolveInfo result:manager.queryIntentActivities(query,0)){
                if(disposed)break;
                ActivityInfo info=result.activityInfo;
                if(info==null || !info.exported || !info.enabled
                        || !info.applicationInfo.enabled || info.packageName.equals(getPackageName())
                        || !seen.add(info.packageName) || manager.getLaunchIntentForPackage(info.packageName)==null)
                    continue;
                found.add(new Entry(info.packageName,result.loadLabel(manager),result.loadIcon(manager)));
            }
            Collator collator=Collator.getInstance();
            found.sort((left,right)->collator.compare(left.label.toString(),right.label.toString()));
        }catch(RuntimeException e){Trace.log("CAR_APP_LIST_ERROR",e.toString());}
        return found;
    }
    private android.content.SharedPreferences settings(){return getSharedPreferences(AppSettings.FILE,MODE_PRIVATE);}
    @Override protected void onDestroy(){disposed=true;super.onDestroy();}
}
