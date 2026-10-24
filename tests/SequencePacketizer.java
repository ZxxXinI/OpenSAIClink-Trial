package org.opensaiclink.trial;
import java.io.*;

/** Replay demuxed access units through the shipping Java packet writer. */
public final class SequencePacketizer {
    public static void main(String[] args)throws Exception{
        try(DataInputStream input=new DataInputStream(new FileInputStream(args[0]));
            FileOutputStream output=new FileOutputStream(args[1])){
            while(input.available()>0){
                int length=input.readInt();
                if(length<1 || length>WlFrame.MAX_PAYLOAD)throw new IOException("Invalid access unit");
                byte[] bytes=new byte[length];input.readFully(bytes);
                WlPacketWriter.write(output,new WlFrame(6,bytes),null);
            }
        }
    }
}
