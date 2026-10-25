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
    private static File directory,logFile;
    private static final ArrayDeque<String> tail=new ArrayDeque<>();
    private static final int LIMIT=1024*1024;
    public static synchronized void init(Context c){
        if(directory!=null)return;
        directory=new File(c.getFilesDir(),"diagnostics-v1");
        directory.mkdirs();
        logFile=new File(directory,"session.log");
        log("START","1.0.0-rc1 Android="+Build.VERSION.SDK_INT+" model="+Build.MODEL);
    }
    public static synchronized void log(String event,String detail){
        String line=new SimpleDateFormat("MM-dd HH:mm:ss.SSS",Locale.US).format(new Date())+" "+event+" "+detail.replace('\n',' ').replace('\r',' ');
        if(line.length()>3000)line=line.substring(0,3000);
        tail.add(line);
        while(tail.size()>30)tail.remove();
        Log.i("OpenSAICLink",line);
        if(logFile==null)return;
        try{
            if(logFile.length()>LIMIT){
                File previous=new File(directory,"previous.log");
                try(InputStream in=new FileInputStream(logFile);OutputStream out=new FileOutputStream(previous)){
                    byte[] b=new byte[8192];
                    int n;
                    while((n=in.read(b))!=-1)out.write(b,0,n);
                }
                try(OutputStream out=new FileOutputStream(logFile)){
                    out.write(new byte[]{
                        (byte)239,(byte)187,(byte)191
                    }
                    );
                }
            }
            try(OutputStream out=new FileOutputStream(logFile,true)){
                out.write((line+"\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        catch(IOException e){
            Log.e("OpenSAICLink","Diagnostic write failed",e);
        }
    }
    public static synchronized String tail(){
        return String.join("\n",tail);
    }
    public static File newSample(){
        return new File(directory,"last-capture.h264");
    }
    public static boolean hasVideo(){
        return directory!=null&&newSample().isFile()&&newSample().length()>0;
    }
    public static void export(OutputStream output,boolean video)throws IOException{
        if(directory==null)throw new IOException("Diagnostics not initialized");
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            for(String name:new String[]{
                "session.log","previous.log","last-capture.h264"
            }
            ){
                if(name.endsWith("h264")&&!video)continue;
                File f=new File(directory,name);
                if(!f.isFile())continue;
                long remaining=f.length();
                zip.putNextEntry(new ZipEntry(name));
                try(InputStream in=new FileInputStream(f)){
                    byte[] b=new byte[8192];
                    int n;
                    while(remaining>0&&(n=in.read(b,0,(int)Math.min(b.length,remaining)))!=-1){
                        zip.write(b,0,n);
                        remaining-=n;
                    }
                }
                zip.closeEntry();
            }
        }
    }
}
