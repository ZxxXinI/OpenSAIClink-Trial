package org.opensaiclink.trial;
public final class SessionState {
    public enum Stage {
        IDLE,CONNECTING,READY,AUTHORIZING,STARTING,STREAMING,PAUSED,STOPPING,DISCONNECTED,ERROR
    }
    public static final class Snapshot {
        public final Stage stage;
        public final boolean usb,configured,touch;
        public final String message;
        Snapshot(Stage s,boolean u,boolean c,boolean t,String m){
            stage=s;
            usb=u;
            configured=c;
            touch=t;
            message=m;
        }
        public boolean canStart(){
            return stage==Stage.READY && usb && configured && touch;
        }
        public boolean projecting(){
            return stage==Stage.STREAMING || stage==Stage.PAUSED;
        }
    }
    private Stage stage=Stage.IDLE;
    private boolean usb,configured,touch;
    private String message="";
    public synchronized Snapshot snapshot(){
        return new Snapshot(stage,usb,configured,touch,message);
    }
    public synchronized void touch(boolean value){
        touch=value;
    }
    public synchronized boolean connect(){
        if(stage!=Stage.IDLE && stage!=Stage.DISCONNECTED && stage!=Stage.ERROR)return false;
        stage=Stage.CONNECTING;
        usb=false;
        configured=false;
        message="";
        return true;
    }
    public synchronized void usb(){
        if(stage==Stage.CONNECTING)usb=true;
    }
    public synchronized void configured(){
        if(stage==Stage.CONNECTING){
            usb=true;
            configured=true;
            stage=Stage.READY;
        }
    }
    public synchronized boolean authorize(){
        if(!snapshot().canStart())return false;
        stage=Stage.AUTHORIZING;
        return true;
    }
    public synchronized void cancelAuthorization(){
        if(stage==Stage.AUTHORIZING)stage=configured?Stage.READY:Stage.IDLE;
    }
    public synchronized boolean start(){
        if(stage!=Stage.AUTHORIZING || !usb || !configured || !touch)return false;
        stage=Stage.STARTING;
        return true;
    }
    public synchronized void streaming(){
        if(stage==Stage.STARTING)stage=Stage.STREAMING;
    }
    public synchronized void pause(boolean pause){
        if(stage==Stage.STREAMING || stage==Stage.PAUSED)stage=pause?Stage.PAUSED:Stage.STREAMING;
    }
    public synchronized boolean stop(){
        if(stage==Stage.STOPPING)return false;
        stage=Stage.STOPPING;
        return true;
    }
    public synchronized void ended(Stage terminal,String detail){
        usb=false;
        configured=false;
        stage=terminal;
        message=detail==null?"":detail;
    }
}
