package org.opensaiclink.trial;

import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/** After congestion, discard dependent frames until a fresh IDR arrives. */
public final class VideoQueue {
    private final ArrayDeque<WlFrame> control = new ArrayDeque<>();
    private final ArrayDeque<Entry> video = new ArrayDeque<>();
    private static final class Entry {
        final WlFrame frame; final long time;
        Entry(byte[] bytes,long time){this.frame=new WlFrame(6,bytes);this.time=time;}
    }
    private final LongSupplier clock;
    private final long maxAgeMillis;
    public volatile long lastTakenAgeMillis;
    private final int capacity;
    private int videoBytes;
    private boolean waitingForKey = true;
    private boolean closed;
    public volatile long dropped;

    public VideoQueue(int capacity) { this(capacity,180,()->System.nanoTime()/1000000L); }
    public VideoQueue(int capacity,long maxAgeMillis,LongSupplier clock) {
        if(capacity<1 || maxAgeMillis<1)throw new IllegalArgumentException("Invalid video queue bounds");
        this.capacity=capacity;this.maxAgeMillis=maxAgeMillis;this.clock=clock;
    }
    private void expire(){
        if(!video.isEmpty() && clock.getAsLong()-video.peek().time>maxAgeMillis){
            dropped+=video.size();resetVideo();
        }
    }

    public synchronized void control(WlFrame frame) {
        if (closed) return;
        if (control.size() >= 128) throw new IllegalStateException("Control queue full");
        control.add(frame); notifyAll();
    }

    public synchronized boolean video(byte[] bytes, boolean key) {
        if (closed) return false;
        expire();
        if (bytes.length > WlFrame.MAX_PAYLOAD) { resetVideo(); dropped++; return false; }
        if (video.size() >= capacity || videoBytes + bytes.length > 4 * 1024 * 1024) {
            dropped += video.size(); resetVideo();
        }
        if (waitingForKey && !key) { dropped++; return false; }
        waitingForKey = false;
        video.add(new Entry(bytes,clock.getAsLong())); videoBytes += bytes.length;
        notifyAll(); return true;
    }

    public synchronized WlFrame take() throws InterruptedException {
        for(;;){
            if(closed)return null;
            if(!control.isEmpty())return control.remove();
            expire();
            if(!video.isEmpty()){
                Entry next=video.remove();videoBytes-=next.frame.payload.length;
                lastTakenAgeMillis=Math.max(0,clock.getAsLong()-next.time);return next.frame;
            }
            wait();
        }
    }

    public synchronized void resetVideo() {
        video.clear(); videoBytes = 0; waitingForKey = true;
    }

    public synchronized void close() {
        closed = true; control.clear(); resetVideo(); notifyAll();
    }
}
