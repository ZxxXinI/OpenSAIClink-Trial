package org.opensaiclink.trial;
import java.util.Map;
/** Durable migration and explicit one-shot recording policy, independently testable. */ public final class ConfigurationLogic {
    public interface Migration {
        int version();
        Map<String,?> oldValues();
        boolean hasBackup();
        boolean backup();
        boolean install(String layout);
    }
    public interface Recording {
        boolean armed();
        boolean disarm();
    }
    public static void migrate(Migration store){
        if(store.version()>=1)return;
        HomeLayout layout=HomeLayout.migrate(store.oldValues());
        if(!store.hasBackup()&&!store.backup())throw new IllegalStateException("Cannot back up settings");
        if(!store.install(layout.encode()))throw new IllegalStateException("Cannot migrate settings");
    }
    public static boolean claim(Recording setting){
        return setting.armed()&&setting.disarm();
    }
    private ConfigurationLogic(){
    }
}
