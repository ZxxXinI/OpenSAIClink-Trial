package org.opensaiclink.trial;
import android.content.*;
import org.json.JSONObject;
public final class SettingsStore {
    private final SharedPreferences formal,legacy;
    public SettingsStore(Context c){
        formal=c.getSharedPreferences("formal",0);
        legacy=c.getSharedPreferences("trial",0);
        migrate();
    }
    private synchronized void migrate(){
        ConfigurationLogic.migrate(new ConfigurationLogic.Migration(){
            public int version(){
                return formal.getInt("schema",0);
            }
            public java.util.Map<String,?> oldValues(){
                return legacy.getAll();
            }
            public boolean hasBackup(){
                return formal.contains("e6_backup");
            }
            public boolean backup(){
                boolean ok=formal.edit().putString("e6_backup",new JSONObject(legacy.getAll()).toString()).commit();
                if(!ok)formal.edit().remove("e6_backup").apply();
                return ok;
            }
            public boolean install(String layout){
                boolean ok=formal.edit().putString("layout",layout).putInt("schema",1).commit();
                if(!ok)formal.edit().remove("schema").apply();
                return ok;
            }
        }
        );
        if(!legacy.contains("fill_display"))legacy.edit().putBoolean("fill_display",false).apply();
    }
    public HomeLayout layout(){
        return HomeLayout.initial(formal.getInt("schema",0),formal.getString("layout",""),legacy.getAll());
    }
    public boolean save(HomeLayout value){
        return formal.edit().putString("layout",value.encode()).commit();
    }
    public String theme(){
        return formal.getString("theme","system");
    }
    public void theme(String value){
        formal.edit().putString("theme",value).apply();
    }
    public boolean option(String key,boolean fallback){
        return legacy.getBoolean(key,fallback);
    }
    public void setOption(String key,boolean value){
        legacy.edit().putBoolean(key,value).apply();
    }
    public boolean recordingArmed(){
        return formal.getBoolean("record_next",false);
    }
    public void armRecording(boolean value){
        formal.edit().putBoolean("record_next",value).apply();
    }
    public synchronized boolean consumeRecording(){
        return ConfigurationLogic.claim(new ConfigurationLogic.Recording(){
            public boolean armed(){
                return recordingArmed();
            }
            public boolean disarm(){
                return formal.edit().putBoolean("record_next",false).commit();
            }
        }
        );
    }
}
