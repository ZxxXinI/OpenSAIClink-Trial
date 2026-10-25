package org.opensaiclink.trial;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.usb.*;
import android.os.*;

public final class LinkService extends Service {
    public static final String CONNECT="org.opensaiclink.trial.CONNECT";
    public static final String CAPTURE="org.opensaiclink.trial.CAPTURE";
    public static final String STOP="org.opensaiclink.trial.STOP";
    public static final String MAP="org.opensaiclink.trial.MAP";
    public static final String SESSION_STOPPED="org.opensaiclink.trial.SESSION_STOPPED";
    public static final String GRANT="grant";
    private final Handler main=new Handler(Looper.getMainLooper());
    private HandlerThread workerThread;
    private Handler worker;
    private AoaTransport transport;
    private WelinkSession session;
    private volatile ScreenEncoder encoder;
    private volatile boolean destroyed;
    private boolean receiverRegistered;
    private long lastVideo;
    private int ticks;
    private SessionState.Stage terminal=SessionState.Stage.IDLE;
    private String terminalMessage="";
    private final BroadcastReceiver detachReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(intent.getAction())){
                Trace.log("USB_DETACHED","Accessory detached");stopWith(SessionState.Stage.DISCONNECTED,"\u6570\u636e\u7ebf\u5df2\u65ad\u5f00\uff0c\u8bf7\u91cd\u65b0\u8fde\u63a5");
            }
        }
    };
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(destroyed)return;
            long sent=Status.videoWritten.get();
            if(transport!=null && transport.isOpen() && Status.configured && sent==lastVideo)session.noUpdate();
            lastVideo=sent;
            if(transport!=null && transport.writeStallMillis()>3000){
                Trace.log("USB_TX_STALLED",transport.pendingWrite());stopWith(SessionState.Stage.ERROR,"\u4f20\u8f93\u6682\u65e0\u54cd\u5e94\uff0c\u8bf7\u62d4\u63d2\u6570\u636e\u7ebf\u540e\u91cd\u65b0\u8fde\u63a5");return;
            }
            if(++ticks%5==0)Trace.log("STATS","usb="+Status.usbOpen+" config="+Status.configured
                    +" capture="+Status.capturing+" pause="+Status.huPaused+" encoded="+Status.encoded.get()
                    +" videoWritten="+sent+" bytesWritten="+Status.bytesWritten.get()
                    +" videoPackets="+Status.videoPacketsWritten.get()
                    +" fps="+Status.encoderFps+" queueMs="+Status.queueMs+" maxQueueMs="+Status.maxQueueMs.get()
                    +" writeMs="+Status.writeMs+" maxWriteMs="+Status.maxWriteMs.get()
                    +" encodeAgeMs="+Status.encodeAgeMs+" maxEncodeAgeMs="+Status.maxEncodeAgeMs.get()
                    +" fill="+Status.fillVideo+" renderFrames="+Status.renderFrames.get()+" renderMs="+Status.renderMs+" maxRenderMs="+Status.maxRenderMs.get()
                    +" maxPointers="+Status.touchMaxPointers
                    +" touchRx="+Status.touchReceived.get()+" touchDone="+Status.touchDispatched.get()
                    +" dropped="+(transport==null?0:transport.dropped()));
            main.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(){
        super.onCreate();Trace.init(this);
        workerThread=new HandlerThread("wl-capture-owner");workerThread.start();worker=new Handler(workerThread.getLooper());
        NotificationChannel channel=new NotificationChannel("projection",getString(R.string.app_name),NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        registerReceiver(detachReceiver,new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED),Context.RECEIVER_EXPORTED);
        receiverRegistered=true;main.post(tick);
    }
    private void foreground(boolean projection){
        Intent open=projection?CarHomeActivity.intent(this):new Intent(this,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,LinkService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notificationBuilder=new Notification.Builder(this,"projection")
                .setSmallIcon(android.R.drawable.presence_video_online).setContentTitle(getString(R.string.app_name))
                .setContentText(getString(projection?R.string.notification_capture:R.string.notification_usb))
                .setContentIntent(content).setOngoing(true).setVisibility(Notification.VISIBILITY_PUBLIC);
        if(projection)notificationBuilder.addAction(new Notification.Action.Builder(null,getString(R.string.car_return),content).build());
        Notification notification=notificationBuilder.addAction(new Notification.Action.Builder(null,getString(R.string.stop),stop).build()).build();
        int types=(Status.usbOpen?ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE:0)
                |(projection?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION:0);
        if(types!=0)startForeground(10,notification,types);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();
        try{
            if(STOP.equals(action)){stopWith(SessionState.Stage.IDLE,"");return START_NOT_STICKY;}
            if(CONNECT.equals(action)){
                if(Status.capturing && Status.localTest)throw new IllegalStateException("Stop local test before USB connection");
                if(transport!=null && transport.isOpen())return START_NOT_STICKY;
                UsbAccessory accessory=intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY,UsbAccessory.class);
                UsbManager manager=getSystemService(UsbManager.class);
                if(accessory==null || !manager.hasPermission(accessory))throw new SecurityException("USB permission missing");
                Status.usbOpen=true;foreground(false);Status.usbOpen=false;
                if(SessionController.state.snapshot().stage!=SessionState.Stage.CONNECTING){stopSelf();return START_NOT_STICKY;}
                worker.post(()->{try{if(!destroyed)connect(manager,accessory);}catch(Exception e){Trace.log("CONNECT_ERROR",e.toString());stopWith(SessionState.Stage.ERROR,"\u8fde\u63a5\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6570\u636e\u7ebf\u540e\u91cd\u8bd5");}});
            }else if(CAPTURE.equals(action)){
                if(!Status.touchReady)throw new IllegalStateException("Enable accessibility service before screen sharing");
                if(Status.capturing){Trace.log("CAPTURE_ALREADY_ACTIVE","Existing session retained");return START_NOT_STICKY;}
                boolean local=false;
                if(!local && !(Status.usbOpen && Status.configured))throw new IllegalStateException("HU config required before capture");
                Intent grant=intent.getParcelableExtra(GRANT,Intent.class);
                int result=intent.getIntExtra("result",Activity.RESULT_CANCELED);
                if(grant==null || result!=Activity.RESULT_OK)throw new SecurityException("Screen sharing grant missing");
                if(!SessionController.state.start()){Trace.log("CAPTURE_REJECT","Session not ready");return START_NOT_STICKY;}
                SessionController.publish();Status.localTest=local;foreground(true);
                worker.post(()->startCapture(result,grant,local));
            }else if(MAP.equals(action)){
                if(session!=null && Status.capturing)session.resume(intent.getStringExtra("package"));
            }
        }catch(Exception e){
            Trace.log("SERVICE_ERROR",e.toString());Status.stage="Error: "+e.getMessage();
            stopWith(SessionState.Stage.ERROR,"\u65e0\u6cd5\u5f00\u59cb\u8fde\u63a5\u6216\u6295\u5c4f\uff0c\u8bf7\u68c0\u67e5\u6743\u9650\u540e\u91cd\u8bd5");
        }
        return START_NOT_STICKY;
    }
    private void connect(UsbManager manager,UsbAccessory accessory)throws Exception{
        transport=new AoaTransport(new AoaTransport.Listener(){
            @Override public void frame(WlFrame frame){WelinkSession current=session;if(!destroyed && current!=null)current.receive(frame);}
            @Override public void closed(String reason){main.post(()->{
                Trace.log("USB_CLOSED",reason);stopWith(SessionState.Stage.DISCONNECTED,"\u8f66\u673a\u8fde\u63a5\u5df2\u7ed3\u675f\uff0c\u8bf7\u91cd\u65b0\u8fde\u63a5");
            });}
            @Override public void needKeyFrame(){ScreenEncoder current=encoder;if(current!=null)current.requestKeyFrame();}
        });
        session=new WelinkSession(transport,new WelinkSession.Listener(){
            @Override public void touch(TouchInput.Event event){RemoteTouchService.deliver(event);}
            @Override public void touchCommit(){RemoteTouchService.deliverCommit();}
            @Override public void back(){RemoteTouchService.back();}
            @Override public void configurationChanged(){
                SessionController.state.configured();SessionController.publish();
                main.post(()->{ScreenEncoder current=encoder;if(Status.capturing && current!=null){session.resume("");current.requestKeyFrame();}});
            }
            @Override public void sourceChanged(boolean paused){
                Status.huPaused=paused;transport.resetVideo();SessionController.state.pause(paused);SessionController.publish();
                if(paused)RemoteTouchService.releaseCurrent();
                Trace.log("HU_SOURCE",paused?"Paused by head unit":"Resumed by head unit");
                ScreenEncoder current=encoder;if(!paused && current!=null)current.requestKeyFrame();
            }
        });
        Status.configured=false;Status.huPaused=false;
        Status.rxFrames.set(0);Status.bytesWritten.set(0);Status.videoWritten.set(0);Status.videoPacketsWritten.set(0);
        Status.touchReceived.set(0);Status.touchDispatched.set(0);
        transport.open(manager,accessory);if(destroyed){transport.close();return;}Status.usbOpen=true;SessionController.state.usb();SessionController.publish();Status.stage="USB open; waiting for HU messages";
        Trace.log("USB_OPEN","manufacturer="+accessory.getManufacturer()+" model="+accessory.getModel()+" version="+accessory.getVersion());
        main.post(()->{if(!destroyed)foreground(false);});
    }
    private void startCapture(int result,Intent grant,boolean local){
        if(destroyed)return;
        try{
            if(!Status.touchReady)throw new IllegalStateException("Accessibility service disconnected before capture");
            getSharedPreferences("trial",MODE_PRIVATE).edit().putBoolean("touch",true).apply();
            if(encoder!=null)encoder.stop();
            Status.encoded.set(0);Status.videoWritten.set(0);Status.videoPacketsWritten.set(0);
            Status.maxQueueMs.set(0);Status.maxWriteMs.set(0);Status.maxEncodeAgeMs.set(0);
            Status.queueMs=0;Status.writeMs=0;Status.encodeAgeMs=0;
            Status.renderFrames.set(0);Status.maxRenderMs.set(0);Status.renderMs=0;Status.fillVideo=false;Status.touchMaxPointers=0;
            if(transport!=null)transport.resetVideo();
            if(local){Status.width=1280;Status.height=480;Status.fps=15;}
            Status.capturing=true;Status.captureStage="Starting capture";
            ProjectionRotation.apply(this);RemoteTouchService.refreshOrientation();
            encoder=new ScreenEncoder(this,worker,new ScreenEncoder.Listener(){
                @Override public void frame(byte[] data,boolean key){
                    if(destroyed)return;
                    if(Status.encoded.get()==1){SessionController.state.streaming();SessionController.publish();}
                    if(!local && Status.usbOpen && Status.configured && !Status.huPaused && transport!=null)transport.video(data,key);
                }
                @Override public void stopped(String reason){if(!destroyed){Trace.log("CAPTURE_ENDED",reason);stopWith(SessionState.Stage.DISCONNECTED,"\u5c4f\u5e55\u5171\u4eab\u5df2\u7ed3\u675f");}}
            });
            encoder.start(result,grant,Status.width,Status.height,Status.fps);
            if(destroyed){encoder.stop();Status.capturing=false;ProjectionRotation.restore(this);return;}
            Status.capturing=true;Status.captureStage=local?"Local capture active; USB not tested":"Capture active; car display needs visual check";
            if(!local)main.postDelayed(()->{if(session!=null && Status.capturing)session.resume("");},1000);
        }catch(Exception e){
            Trace.log("CAPTURE_START_ERROR",e.toString());stopWith(SessionState.Stage.ERROR,"\u5c4f\u5e55\u5171\u4eab\u65e0\u6cd5\u542f\u52a8\uff0c\u8bf7\u91cd\u65b0\u6388\u6743\u540e\u91cd\u8bd5");
        }
    }
    private void stopWith(SessionState.Stage outcome,String message){main.post(()->{if(destroyed)return;terminal=outcome;terminalMessage=message;SessionController.state.stop();SessionController.publish();Status.capturing=false;RemoteTouchService.releaseCurrent();stopSelf();});}
    @Override public void onDestroy(){
        destroyed=true;main.removeCallbacksAndMessages(null);SessionController.state.stop();SessionController.publish();
        if(receiverRegistered)unregisterReceiver(detachReceiver);
        Status.capturing=false;Status.usbOpen=false;Status.configured=false;Status.localTest=false;Status.huPaused=false;Status.showCarHome=false;
        RemoteTouchService.releaseCurrent();
        sendBroadcast(new Intent(SESSION_STOPPED).setPackage(getPackageName()));
        new Thread(()->{
            try{if(transport!=null)transport.close();}catch(Exception e){Trace.log("CLOSE_USB_ERROR",e.toString());}
            worker.post(()->{try{if(encoder!=null)encoder.stop();ProjectionRotation.restore(this);}catch(Exception e){Trace.log("CLOSE_CAPTURE_ERROR",e.toString());}
                finally{Status.stage="Stopped";Status.captureStage="Stopped";SessionController.state.ended(terminal,terminalMessage);SessionController.publish();workerThread.quitSafely();Trace.log("SERVICE_STOPPED","Resources released");}});
        },"wl-session-close").start();
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
