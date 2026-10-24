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
    private long lastStallReport;
    private final BroadcastReceiver detachReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(intent.getAction())){
                Trace.log("USB_DETACHED","Accessory detached");stopSelf();
            }
        }
    };
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(destroyed)return;
            long sent=Status.videoWritten.get();
            if(transport!=null && transport.isOpen() && Status.configured && sent==lastVideo)session.noUpdate();
            lastVideo=sent;
            if(transport!=null && transport.writeStallMillis()>3000
                    && SystemClock.elapsedRealtime()-lastStallReport>5000){
                lastStallReport=SystemClock.elapsedRealtime();
                Trace.log("USB_TX_STALLED",transport.pendingWrite());
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
            if(STOP.equals(action)){stopSelf();return START_NOT_STICKY;}
            if(CONNECT.equals(action)){
                if(Status.capturing && Status.localTest)throw new IllegalStateException("Stop local test before USB connection");
                if(transport!=null && transport.isOpen())return START_NOT_STICKY;
                UsbAccessory accessory=intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY,UsbAccessory.class);
                UsbManager manager=getSystemService(UsbManager.class);
                if(accessory==null || !manager.hasPermission(accessory))throw new SecurityException("USB permission missing");
                Status.usbOpen=true;foreground(false);Status.usbOpen=false;
                connect(manager,accessory);
            }else if(CAPTURE.equals(action)){
                if(!Status.touchReady)throw new IllegalStateException("Enable accessibility service before screen sharing");
                if(Status.capturing){Trace.log("CAPTURE_ALREADY_ACTIVE","Existing session retained");return START_NOT_STICKY;}
                boolean local=intent.getBooleanExtra("local",false);
                if(!local && !(Status.usbOpen && Status.configured))throw new IllegalStateException("HU config required before capture");
                Intent grant=intent.getParcelableExtra(GRANT,Intent.class);
                int result=intent.getIntExtra("result",Activity.RESULT_CANCELED);
                if(grant==null || result!=Activity.RESULT_OK)throw new SecurityException("Screen sharing grant missing");
                Status.localTest=local;foreground(true);
                worker.post(()->startCapture(result,grant,local));
            }else if(MAP.equals(action)){
                if(session!=null && Status.capturing)session.resume(intent.getStringExtra("package"));
            }
        }catch(Exception e){
            Trace.log("SERVICE_ERROR",e.toString());Status.stage="Error: "+e.getMessage();
            if(!Status.usbOpen && !Status.capturing)stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void connect(UsbManager manager,UsbAccessory accessory)throws Exception{
        transport=new AoaTransport(new AoaTransport.Listener(){
            @Override public void frame(WlFrame frame){WelinkSession current=session;if(current!=null)current.receive(frame);}
            @Override public void closed(String reason){main.post(()->{
                Trace.log("USB_CLOSED",reason);Status.stage="USB closed: "+reason;stopSelf();
            });}
            @Override public void needKeyFrame(){ScreenEncoder current=encoder;if(current!=null)current.requestKeyFrame();}
        });
        session=new WelinkSession(transport,new WelinkSession.Listener(){
            @Override public void touch(TouchInput.Event event){RemoteTouchService.deliver(event);}
            @Override public void touchCommit(){RemoteTouchService.deliverCommit();}
            @Override public void back(){RemoteTouchService.back();}
            @Override public void configurationChanged(){
                main.post(()->{ScreenEncoder current=encoder;if(Status.capturing && current!=null){session.resume("");current.requestKeyFrame();}});
            }
            @Override public void sourceChanged(boolean paused){
                Status.huPaused=paused;transport.resetVideo();
                if(paused)RemoteTouchService.releaseCurrent();
                Trace.log("HU_SOURCE",paused?"Paused by head unit":"Resumed by head unit");
                ScreenEncoder current=encoder;if(!paused && current!=null)current.requestKeyFrame();
            }
        });
        Status.configured=false;Status.huPaused=false;
        Status.rxFrames.set(0);Status.bytesWritten.set(0);Status.videoWritten.set(0);Status.videoPacketsWritten.set(0);
        Status.touchReceived.set(0);Status.touchDispatched.set(0);
        transport.open(manager,accessory);Status.usbOpen=true;Status.stage="USB open; waiting for HU messages";
        Trace.log("USB_OPEN","manufacturer="+accessory.getManufacturer()+" model="+accessory.getModel()+" version="+accessory.getVersion());
        foreground(false);
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
                    if(!local && Status.usbOpen && Status.configured && !Status.huPaused && transport!=null)transport.video(data,key);
                }
                @Override public void stopped(String reason){main.post(()->{
                    Status.capturing=false;Status.captureStage=reason;Trace.log("CAPTURE_ENDED",reason);
                    RemoteTouchService.releaseCurrent();ProjectionRotation.restore(LinkService.this);
                    if(Status.usbOpen)foreground(false);else stopSelf();
                });}
            });
            encoder.start(result,grant,Status.width,Status.height,Status.fps);
            if(destroyed){encoder.stop();Status.capturing=false;ProjectionRotation.restore(this);return;}
            Status.capturing=true;Status.captureStage=local?"Local capture active; USB not tested":"Capture active; car display needs visual check";
            if(!local)main.postDelayed(()->{if(session!=null && Status.capturing)session.resume("");},1000);
        }catch(Exception e){
            Trace.log("CAPTURE_START_ERROR",e.toString());if(encoder!=null)encoder.stop();encoder=null;
            ProjectionRotation.restore(this);
            main.post(()->{Status.capturing=false;Status.captureStage="Error: "+e.getMessage();
                if(Status.usbOpen)foreground(false);else stopSelf();});
        }
    }
    @Override public void onDestroy(){
        destroyed=true;Status.capturing=false;main.removeCallbacksAndMessages(null);
        if(receiverRegistered)unregisterReceiver(detachReceiver);
        if(transport!=null)transport.close();
        RemoteTouchService.releaseCurrent();
        worker.post(()->{if(encoder!=null)encoder.stop();Status.capturing=false;ProjectionRotation.restore(this);workerThread.quitSafely();});
        Status.usbOpen=false;Status.configured=false;Status.capturing=false;Status.localTest=false;Status.huPaused=false;
        Status.showCarHome=false;
        sendBroadcast(new Intent(SESSION_STOPPED).setPackage(getPackageName()));
        Status.stage="Stopped";Status.captureStage="Stopped";Trace.log("SERVICE_STOPPED","USB and capture released");
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
