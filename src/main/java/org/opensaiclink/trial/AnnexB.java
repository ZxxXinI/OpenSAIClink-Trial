package org.opensaiclink.trial;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Normalize MediaCodec output to the byte-stream format used by WL type 6. */
public final class AnnexB {
    private AnnexB() {}
    public static byte[] from(ByteBuffer source) {
        byte[] data = new byte[source.remaining()]; source.duplicate().get(data);
        return normalize(data);
    }

    public static byte[] normalize(byte[] data) {
        if (data.length < 4) return data;
        if (data[0] == 0 && data[1] == 0
                && (data[2] == 1 || (data[2] == 0 && data[3] == 1))) return data;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int p = 0;
        while (p + 4 <= data.length) {
            long n = ((long) (data[p] & 255) << 24) | ((long) (data[p + 1] & 255) << 16)
                    | ((long) (data[p + 2] & 255) << 8) | (data[p + 3] & 255);
            p += 4;
            if (n <= 0 || n > data.length - p) throw new IllegalArgumentException("Invalid AVC NAL length");
            out.write(0); out.write(0); out.write(0); out.write(1);
            out.write(data, p, (int) n); p += (int) n;
        }
        if (p != data.length) throw new IllegalArgumentException("Truncated AVC NAL");
        return out.toByteArray();
    }

    public static byte[] join(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length); return out;
    }

    /** A7 classifies the FIRST NAL in each WL payload. Parameter sets need their
     * own packets; retain all picture slices together as one access unit. */
    public static List<byte[]> welinkPayloads(byte[] accessUnit) {
        List<Integer> starts=new ArrayList<>();
        List<Integer> prefixes=new ArrayList<>();
        for(int i=0;i+3<=accessUnit.length;){
            int prefix=0;
            if(i+4<=accessUnit.length && accessUnit[i]==0 && accessUnit[i+1]==0
                    && accessUnit[i+2]==0 && accessUnit[i+3]==1)prefix=4;
            else if(accessUnit[i]==0 && accessUnit[i+1]==0 && accessUnit[i+2]==1)prefix=3;
            if(prefix>0){starts.add(i);prefixes.add(prefix);i+=prefix;}else i++;
        }
        if(starts.isEmpty() || starts.get(0)!=0)throw new IllegalArgumentException("Missing Annex B start code");
        List<byte[]> packets=new ArrayList<>();
        ByteArrayOutputStream picture=new ByteArrayOutputStream();
        for(int i=0;i<starts.size();i++){
            int body=starts.get(i)+prefixes.get(i);
            int end=i+1<starts.size()?starts.get(i+1):accessUnit.length;
            if(body>=end)throw new IllegalArgumentException("Empty AVC NAL");
            byte[] nal=new byte[4+end-body];nal[3]=1;
            System.arraycopy(accessUnit,body,nal,4,end-body);
            int type=nal[4]&31;
            if(type==7 || type==8)packets.add(nal);
            else picture.write(nal,0,nal.length);
        }
        if(picture.size()>0)packets.add(picture.toByteArray());
        return packets;
    }
}
