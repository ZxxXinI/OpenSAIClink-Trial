package org.opensaiclink.trial;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.Surface;
import android.view.WindowManager;
import android.graphics.Rect;
import java.io.*;
import java.nio.ByteBuffer;

public final class ScreenEncoder {
    public interface Listener {
        void frame(byte[] data,boolean key);
        void stopped(String reason);
    }
    private final Context context;
    private final Handler owner;
    private final Listener listener;
    private MediaProjection projection;
    private VirtualDisplay display;
    private MediaCodec codec;
    private Surface surface;
    private DisplayRenderer renderer;
    private Thread drain;
    private volatile boolean running;
    private volatile long lastSyncRequest;
    private byte[] parameterSets=new byte[0];
    private OutputStream sample;
    private int sampleBytes;
    private boolean stopping;
    private final MediaProjection.Callback callback=new MediaProjection.Callback(){
        @Override public void onStop(){
            if(!stopping){stop();listener.stopped("System ended screen sharing");}
        }
        @Override public void onCapturedContentResize(int width,int height){
            Status.contentWidth=width;Status.contentHeight=height;RemoteTouchService.releaseCurrent();
            Trace.log("CAPTURE_RESIZE","content="+width+"x"+height+" fixedEncoder="+Status.width+"x"+Status.height);
        }
        @Override public void onCapturedContentVisibilityChanged(boolean visible){
            Trace.log("CAPTURE_VISIBILITY",Boolean.toString(visible));
        }
    };
    public ScreenEncoder(Context context,Handler owner,Listener listener){
        this.context=context;this.owner=owner;this.listener=listener;
    }
    public void start(int resultCode,Intent grant,int width,int height,int fps)throws Exception{
        if(running)throw new IllegalStateException("A capture session is already active");
        MediaProjectionManager manager=context.getSystemService(MediaProjectionManager.class);
        projection=manager.getMediaProjection(resultCode,grant);
        if(projection==null)throw new IllegalStateException("Screen sharing grant is missing");
        projection.registerCallback(callback,owner);
        Rect bounds=context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        Status.contentWidth=bounds.width();Status.contentHeight=bounds.height();
        boolean responsive=context.getSharedPreferences("trial",Context.MODE_PRIVATE).getBoolean("responsive",true);
        fps=responsive?Math.min(fps,15):fps;Status.encoderFps=fps;
        MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE,responsive?1200000:Math.max(1000000,Math.min(4000000,width*height*3)));
        format.setInteger(MediaFormat.KEY_FRAME_RATE,fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        format.setInteger(MediaFormat.KEY_PRIORITY,0);
        format.setInteger(MediaFormat.KEY_LATENCY,0);
        format.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        format.setInteger(MediaFormat.KEY_LEVEL,MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        format.setInteger(MediaFormat.KEY_MAX_B_FRAMES,0);
        format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER,fps);
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,1000000L/Math.max(1,fps));
        codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        Trace.log("ENCODER_CONFIG",codec.getName()+" "+format);
        codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface=codec.createInputSurface();codec.start();
        if(new SettingsStore(context).consumeRecording())sample=new FileOutputStream(Trace.newSample());
        int dpi=context.getResources().getDisplayMetrics().densityDpi;
        Surface captureSurface=surface;Status.fillVideo=false;
        if(context.getSharedPreferences("trial",Context.MODE_PRIVATE).getBoolean("fill_display",true)){
            try{renderer=new DisplayRenderer(surface,width,height,()->owner.post(()->{stop();listener.stopped("Display adaptation failed; disable fill and retry");}));captureSurface=renderer.input();}
            catch(Exception e){Trace.log("DISPLAY_FALLBACK",e.toString());}
        }
        running=true;drain=new Thread(this::drainLoop,"wl-avc-drain");drain.start();
        display=projection.createVirtualDisplay("OpenSAICLink",width,height,dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,captureSurface,null,owner);
        Trace.log("CAPTURE_STARTED",width+"x"+height+" fps="+fps);
    }
    private void drainLoop(){
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        try{
            while(running){
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat actual=codec.getOutputFormat();parameterSets=new byte[0];
                    for(String key:new String[]{"csd-0","csd-1"}){
                        ByteBuffer csd=actual.getByteBuffer(key);
                        if(csd!=null)parameterSets=AnnexB.join(parameterSets,AnnexB.from(csd));
                    }
                    Trace.log("ENCODER_FORMAT",actual+" csdBytes="+parameterSets.length);
                }else if(index>=0){
                    try{
                        if(info.size>0){
                            ByteBuffer output=codec.getOutputBuffer(index);
                            if(output==null)throw new IOException("Missing codec output buffer");
                            output.position(info.offset);output.limit(info.offset+info.size);
                            byte[] data=AnnexB.from(output);
                            if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0){
                                parameterSets=data;
                            }else{
                                boolean key=(info.flags&MediaCodec.BUFFER_FLAG_KEY_FRAME)!=0;
                                if(key && parameterSets.length>0)data=AnnexB.join(parameterSets,data);
                                long count=Status.encoded.incrementAndGet();
                                long age=(System.nanoTime()/1000L-info.presentationTimeUs)/1000L;
                                Status.encodeAgeMs=age>=0 && age<60000?age:-1;
                                if(Status.encodeAgeMs>=0)Status.maxEncodeAgeMs.accumulateAndGet(Status.encodeAgeMs,Math::max);
                                if(count==1)Trace.log("FIRST_ENCODED_FRAME","bytes="+data.length+" key="+key);
                                if(sample!=null && sampleBytes+data.length<=10*1024*1024){
                                    sample.write(data);sampleBytes+=data.length;
                                }else if(sample!=null){sample.close();sample=null;}
                                listener.frame(data,key);
                            }
                        }
                    }finally{codec.releaseOutputBuffer(index,false);}
                }
            }
        }catch(Exception e){
            if(running){Trace.log("ENCODER_ERROR",e.toString());owner.post(()->{stop();listener.stopped(e.toString());});}
        }
    }
    public void requestKeyFrame(){
        long now=SystemClock.elapsedRealtime();if(now-lastSyncRequest<300)return;lastSyncRequest=now;
        try{MediaCodec active=codec;if(running && active!=null){Bundle b=new Bundle();b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME,0);active.setParameters(b);}}
        catch(Exception e){Trace.log("SYNC_REQUEST_ERROR",e.toString());}
    }
    public void stop(){
        if(stopping)return;stopping=true;
        if(display!=null){display.release();display=null;}
        if(renderer!=null){renderer.close();renderer=null;}
        running=false;Status.fillVideo=false;
        if(drain!=null && drain!=Thread.currentThread())try{drain.join(1200);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}codec=null;}
        if(surface!=null){surface.release();surface=null;}
        if(projection!=null){projection.unregisterCallback(callback);projection.stop();projection=null;}
        if(sample!=null){try{sample.close();}catch(IOException ignored){}sample=null;}
        Trace.log("CAPTURE_STOPPED","sampleBytes="+sampleBytes);
    }
}
