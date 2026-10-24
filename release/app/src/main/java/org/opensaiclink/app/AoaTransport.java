package org.opensaiclink.app;

import android.hardware.usb.*;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AoaTransport {
    public interface Listener {
        void frame(WlFrame frame);
        void closed(String reason);
        void needKeyFrame();
    }
    private final Listener listener;
    private final VideoQueue queue=new VideoQueue(3);
    private final AtomicBoolean open=new AtomicBoolean();
    private ParcelFileDescriptor descriptor;
    private InputStream input;
    private OutputStream output;
    private volatile long writeStarted;
    private volatile int writingType,writingNal,writingBytes;
    private volatile boolean headerDone;
    private int videoPacketsLogged;

    public AoaTransport(Listener listener){this.listener=listener;}
    public void open(UsbManager manager,UsbAccessory accessory) throws IOException {
        descriptor=manager.openAccessory(accessory);
        if(descriptor==null)throw new IOException("USB accessory open returned null; original app may own it");
        try {
            input=new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.getFileDescriptor()));
            output=new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor.getFileDescriptor()));
            open.set(true);
            new Thread(this::readLoop,"wl-usb-reader").start();
            new Thread(this::writeLoop,"wl-usb-writer").start();
        }catch(IOException e){close();throw e;}
    }
    public boolean isOpen(){return open.get();}
    public void send(int type,byte[] payload){
        if(!open.get())return;
        try{queue.control(new WlFrame(type,payload));}catch(RuntimeException e){fail(e.toString());}
    }
    public void video(byte[] data,boolean key){
        if(!open.get())return;
        if(!queue.video(data,key))listener.needKeyFrame();
    }
    public long dropped(){return queue.dropped;}
    public long writeStallMillis(){
        long started=writeStarted;
        return started==0?0:Math.max(0,SystemClock.elapsedRealtime()-started);
    }
    public String pendingWrite(){return "type="+writingType+" nal="+writingNal+" payload="+writingBytes
            +" headerDone="+headerDone+" pendingMs="+writeStallMillis();}
    public void resetVideo(){queue.resetVideo();}
    private void readLoop(){
        WlFrame.Parser parser=new WlFrame.Parser();byte[] buffer=new byte[16384];
        try{
            while(open.get()){
                int n=input.read(buffer);if(n<0)throw new EOFException("USB accessory EOF");
                if(n==0)continue;
                parser.feed(buffer,n,frame->{Status.rxFrames.incrementAndGet();listener.frame(frame);});
            }
        }catch(Exception e){if(open.get())fail("read: "+e);}
    }
    private void writeLoop(){
        try{
            while(open.get()){
                WlFrame frame=queue.take();if(frame==null)break;
                long frameStart=SystemClock.elapsedRealtime();
                if(frame.type==6){
                    Status.queueMs=queue.lastTakenAgeMillis;
                    Status.maxQueueMs.accumulateAndGet(Status.queueMs,Math::max);
                }
                WlPacketWriter.write(output,frame,new WlPacketWriter.Observer(){
                    @Override public void starting(int type,int length,int nal){
                        writingType=type;writingBytes=length;writingNal=nal;headerDone=false;
                        writeStarted=SystemClock.elapsedRealtime();
                    }
                    @Override public void headerWritten(){headerDone=true;}
                    @Override public void completed(int type,int length,int nal){
                        long elapsed=writeStallMillis();writeStarted=0;
                        Status.bytesWritten.addAndGet(length);
                        if(type==6){
                            Status.videoPacketsWritten.incrementAndGet();
                            if(videoPacketsLogged++<12)Trace.log("TX_VIDEO_PACKET","nal="+nal+" wireBytes="+length+" writeMs="+elapsed);
                        }
                    }
                });
                if(frame.type==6){
                    Status.videoWritten.incrementAndGet();
                    Status.writeMs=SystemClock.elapsedRealtime()-frameStart;
                    Status.maxWriteMs.accumulateAndGet(Status.writeMs,Math::max);
                    if(Status.writeMs>180)Trace.log("SLOW_VIDEO_WRITE","frameMs="+Status.writeMs+" queueMs="+Status.queueMs);
                }
            }
        }catch(Exception e){if(open.get())fail("write: "+e);}
    }
    private void fail(String reason){
        if(!open.compareAndSet(true,false))return;
        close();listener.closed(reason);
    }
    public void close(){
        open.set(false);queue.close();
        try{if(descriptor!=null)descriptor.close();}catch(IOException ignored){}
        try{if(input!=null)input.close();}catch(IOException ignored){}
        try{if(output!=null)output.close();}catch(IOException ignored){}
    }
}
