package org.opensaiclink.trial;

import java.util.concurrent.atomic.AtomicLong;

public final class Status {
    public static volatile boolean usbOpen, configured, capturing, localTest, huPaused;
    public static volatile int width = 1280, height = 720, fps = 15;
    public static volatile int contentWidth,contentHeight,encoderFps;
    public static volatile boolean touchReady,showCarHome,carHomeVisible;
    public static volatile boolean fillVideo,rotationOverlay;
    public static volatile int touchMaxPointers;
    public static final AtomicLong renderFrames=new AtomicLong(),maxRenderMs=new AtomicLong();
    public static final AtomicLong touchReceived=new AtomicLong(),touchDispatched=new AtomicLong();
    public static volatile long queueMs,writeMs,encodeAgeMs,renderMs;
    public static final AtomicLong maxQueueMs=new AtomicLong(),maxWriteMs=new AtomicLong(),maxEncodeAgeMs=new AtomicLong();
    public static volatile String stage = "Ready", captureStage = "Idle";
    public static final AtomicLong rxFrames = new AtomicLong();
    public static final AtomicLong encoded = new AtomicLong();
    public static final AtomicLong videoWritten = new AtomicLong();
    public static final AtomicLong videoPacketsWritten = new AtomicLong();
    public static final AtomicLong bytesWritten = new AtomicLong();
    private Status() {}
}
