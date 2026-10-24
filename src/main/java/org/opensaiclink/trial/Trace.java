package org.opensaiclink.trial;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public final class Trace {
    private static File directory, logFile;
    private static final ArrayDeque<String> tail = new ArrayDeque<>();
    private Trace() {}
    public static synchronized void init(Context context) {
        if (logFile != null) return;
        directory = new File(context.getFilesDir(), "diagnostics"); directory.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        logFile = new File(directory, "session-" + stamp + ".txt");
        try (FileOutputStream out = new FileOutputStream(logFile)) {
            out.write(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf});
        } catch (IOException e) { Log.e("OpenSAICLink", "Cannot create log", e); }
        log("START", "E6/0.6.0 Android="+Build.VERSION.SDK_INT+" model="+Build.MODEL
                +" abis="+Arrays.toString(Build.SUPPORTED_ABIS));
    }
    public static synchronized void log(String event, String detail) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())
                + " " + event + " " + detail.replace('\n',' ').replace('\r',' ');
        if (line.length() > 6000) line = line.substring(0,6000);
        tail.add(line); while(tail.size()>55)tail.remove();
        Log.i("OpenSAICLink",line);
        if(logFile==null)return;
        try(FileOutputStream out=new FileOutputStream(logFile,true)) {
            out.write((line+"\n").getBytes(StandardCharsets.UTF_8));
        }catch(IOException e){Log.e("OpenSAICLink","Log write failed",e);}
    }
    public static synchronized String tail() {
        StringBuilder out=new StringBuilder();for(String s:tail)out.append(s).append('\n');return out.toString();
    }
    public static synchronized File newSample() {
        return new File(directory,"capture-"+System.currentTimeMillis()+".h264");
    }
    public static synchronized void export(OutputStream destination) throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(destination)) {
            File[] files=directory.listFiles(); if(files==null)return;
            byte[] buffer=new byte[16384];
            for(File file:files) {
                if(!file.isFile() || !(file.getName().endsWith(".txt")||file.getName().endsWith(".h264")))continue;
                zip.putNextEntry(new ZipEntry(file.getName()));
                try(InputStream in=new FileInputStream(file)){
                    int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);
                }
                zip.closeEntry();
            }
        }
    }
}
