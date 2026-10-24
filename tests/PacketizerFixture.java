package org.opensaiclink.trial;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Generate real application wire bytes for the A7 ARM parser regression. */
public final class PacketizerFixture {
    public static void main(String[] args)throws Exception{
        byte[] first=Files.readAllBytes(Paths.get(args[0]));
        try(FileOutputStream out=new FileOutputStream(args[1])){
            WlPacketWriter.write(out,new WlFrame(6,first),null);
        }
    }
}
