package org.opensaiclink.trial;
import android.os.*;
import java.util.concurrent.CopyOnWriteArrayList;
public final class SessionController {
    public interface Observer {
        void changed(SessionState.Snapshot state);
    }
    public static final SessionState state=new SessionState();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Observer> observers=new CopyOnWriteArrayList<>();
    public static void observe(Observer o){
        observers.addIfAbsent(o);
        o.changed(state.snapshot());
    }
    public static void remove(Observer o){
        observers.remove(o);
    }
    public static void publish(){
        main.post(()->{
            SessionState.Snapshot s=state.snapshot();
            for(Observer o:observers)o.changed(s);
        }
        );
    }
    public static void touch(boolean value){
        state.touch(value);
        publish();
    }
    public static String label(SessionState.Snapshot s){
        switch(s.stage){
            case CONNECTING:return "\u6b63\u5728\u8fde\u63a5\u8f66\u673a";
            case READY:return s.touch?"\u51c6\u5907\u5c31\u7eea":"\u8bf7\u5f00\u542f\u8f66\u673a\u89e6\u63a7";
            case AUTHORIZING:return "\u7b49\u5f85\u5c4f\u5e55\u5171\u4eab\u6388\u6743";
            case STARTING:return "\u6b63\u5728\u5f00\u59cb\u6295\u5c4f";
            case STREAMING:return s.touch?"\u6b63\u5728\u6295\u5c4f":"\u6b63\u5728\u6295\u5c4f \u00b7 \u89e6\u63a7\u672a\u8fde\u63a5";
            case PAUSED:return "\u8f66\u673a\u5df2\u6682\u505c\u663e\u793a";
            case STOPPING:return "\u6b63\u5728\u7ed3\u675f\u6295\u5c4f";
            case DISCONNECTED:return "\u8fde\u63a5\u5df2\u65ad\u5f00";
            case ERROR:return "\u8fde\u63a5\u9700\u8981\u5904\u7406";
            default:return "\u8fde\u63a5\u4f60\u7684\u8f66\u673a";
        }
    }
}
