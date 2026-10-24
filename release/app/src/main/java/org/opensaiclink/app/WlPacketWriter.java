package org.opensaiclink.app;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

/** Match the original sender's separate header/payload writes and A7 NAL routing. */
public final class WlPacketWriter {
    public interface Observer {
        void starting(int type,int payloadBytes,int firstNalType);
        void headerWritten();
        void completed(int type,int wireBytes,int firstNalType);
    }
    private WlPacketWriter(){}
    public static void write(OutputStream output,WlFrame frame,Observer observer)throws IOException{
        List<byte[]> payloads=frame.type==6?AnnexB.welinkPayloads(frame.payload):Collections.singletonList(frame.payload);
        for(byte[] payload:payloads){
            byte[] bytes=new WlFrame(frame.type,payload).encode();
            int nal=frame.type==6 && payload.length>4?(payload[4]&31):-1;
            if(observer!=null)observer.starting(frame.type,payload.length,nal);
            output.write(bytes,0,8);
            if(observer!=null)observer.headerWritten();
            if(payload.length>0)output.write(bytes,8,payload.length);
            if(observer!=null)observer.completed(frame.type,bytes.length,nal);
        }
    }
}
