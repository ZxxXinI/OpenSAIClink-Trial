package org.opensaiclink.trial;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.text.Collator;
import java.util.*;

public final class AppPickerActivity extends CarActivity {
    private static final class Entry {
        final String packageName;final CharSequence label;final Drawable icon;
        Entry(String packageName,CharSequence label,Drawable icon){this.packageName=packageName;this.label=label;this.icon=icon;}
    }
    private volatile boolean disposed;
    private boolean shortcut;
    private int shortcutSlot;
    private final List<Entry> entries=new ArrayList<>();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(isFinishing())return;
        shortcut=getIntent().getBooleanExtra("shortcut",false);
        shortcutSlot=Math.max(0,Math.min(3,getIntent().getIntExtra("shortcutSlot",0)));
        createPage(shortcut?R.string.car_pick_favorite:R.string.car_other_apps,true);
        TextView loading=text(getString(R.string.car_loading),18,MUTED);loading.setPadding(dp(10),dp(16),0,0);page.addView(loading);
        ListView list=new ListView(this);list.setDividerHeight(dp(6));page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,position,id)->{
            Entry selected=entries.get(position);
            if(shortcut){
                getSharedPreferences("trial",MODE_PRIVATE).edit().putString(CarHomeActivity.shortcutKey(shortcutSlot),selected.packageName).apply();
                Trace.log("CAR_SHORTCUT_SAVED","slot="+shortcutSlot+" package="+selected.packageName);Toast.makeText(this,R.string.car_favorite_saved,Toast.LENGTH_SHORT).show();finish();
            }else AppLauncher.open(this,selected.packageName);
        });
        new Thread(()->{
            ArrayList<Entry> found=new ArrayList<>();HashSet<String> seen=new HashSet<>();
            try{
                PackageManager pm=getPackageManager();
                Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for(ResolveInfo result:pm.queryIntentActivities(query,0)){
                    if(disposed)return;
                    ActivityInfo info=result.activityInfo;
                    if(info==null || !info.exported || !info.enabled || !info.applicationInfo.enabled || info.packageName.equals(getPackageName()) || !seen.add(info.packageName))continue;
                    if(pm.getLaunchIntentForPackage(info.packageName)==null)continue;
                    found.add(new Entry(info.packageName,result.loadLabel(pm),result.loadIcon(pm)));
                }
                Collator collator=Collator.getInstance();found.sort((a,b)->collator.compare(a.label.toString(),b.label.toString()));
            }catch(RuntimeException e){Trace.log("CAR_APP_LIST_ERROR",e.toString());}
            runOnUiThread(()->{
                if(disposed)return;entries.addAll(found);loading.setText(entries.isEmpty()?R.string.car_no_apps:R.string.car_apps_hint);
                list.setAdapter(new BaseAdapter(){
                    @Override public int getCount(){return entries.size();}
                    @Override public Object getItem(int position){return entries.get(position);}
                    @Override public long getItemId(int position){return position;}
                    @Override public View getView(int position,View recycled,ViewGroup parent){
                        Entry entry=entries.get(position);TextView label=text(entry.label,21,TEXT);
                        label.setGravity(Gravity.CENTER_VERTICAL);label.setMinHeight(dp(66));label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);
                        label.setPadding(dp(18),dp(9),dp(18),dp(9));label.setBackground(cardBackground(CARD));
                        entry.icon.setBounds(0,0,dp(42),dp(42));label.setCompoundDrawablesRelative(entry.icon,null,null,null);label.setCompoundDrawablePadding(dp(18));
                        return label;
                    }
                });Trace.log("CAR_APPS_READY","count="+entries.size()+" shortcut="+shortcut);
            });
        },"wl-app-list").start();
    }
    @Override protected void onDestroy(){disposed=true;super.onDestroy();}
}
