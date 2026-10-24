package org.opensaiclink.trial;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class ProtocolTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++; if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        Random random = new Random(23);
        List<WlFrame> expected = new ArrayList<>();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (int i = 0; i < 500; i++) {
            byte[] payload = new byte[random.nextInt(8000)]; random.nextBytes(payload);
            WlFrame frame = new WlFrame(random.nextInt(256), payload);
            expected.add(frame); stream.write(frame.encode());
        }
        byte[] encoded = stream.toByteArray();
        for (int chunk : new int[]{1, 7, 8, 16384, 65536}) {
            WlFrame.Parser parser = new WlFrame.Parser(); List<WlFrame> actual = new ArrayList<>();
            for (int p = 0; p < encoded.length; p += chunk) {
                byte[] part = Arrays.copyOfRange(encoded, p, Math.min(encoded.length, p + chunk));
                parser.feed(part, part.length, actual::add);
            }
            check(actual.size() == expected.size(), "fragment count " + chunk);
            for (int i = 0; i < actual.size(); i++) {
                check(actual.get(i).type == expected.get(i).type, "unsigned frame type");
                check(Arrays.equals(actual.get(i).payload, expected.get(i).payload), "fragment payload");
            }
        }
        WlFrame.Parser parser = new WlFrame.Parser(); List<WlFrame> result = new ArrayList<>();
        byte[] recovery = AnnexB.join(new byte[]{'x','W','L',9,0,-1,-1,-1,-1}, new WlFrame(103,new byte[0]).encode());
        parser.feed(recovery,recovery.length,result::add);
        check(result.size()==1 && result.get(0).type==103, "bad length resync and zero payload");
        byte[] extended = {'W','L',9,2,0,0,0,4,11,12,31,32};
        parser.feed(extended,extended.length,result::add);
        check(Arrays.equals(result.get(1).payload,new byte[]{31,32}),"extension stripping");
        VideoQueue queue = new VideoQueue(2);
        check(!queue.video(new byte[]{1},false),"first frame requires IDR");
        check(queue.video(new byte[]{2},true),"IDR accepted");
        check(queue.video(new byte[]{3},false),"P frame accepted");
        check(!queue.video(new byte[]{4},false),"overflow waits for new IDR");
        check(queue.video(new byte[]{5},true),"new IDR recovers");
        queue.control(new WlFrame(103,new byte[0]));
        check(queue.take().type==103,"control priority");
        check(queue.take().payload[0]==5,"stale interframes removed");
        queue.close(); check(queue.take()==null,"closed queue wakes writer");
        java.util.concurrent.atomic.AtomicLong time=new java.util.concurrent.atomic.AtomicLong(1000);
        VideoQueue aged=new VideoQueue(3,180,time::get);
        check(aged.video(new byte[]{1},true),"fresh key accepted");
        time.addAndGet(181);
        check(!aged.video(new byte[]{2},false),"aged chain discarded even below count limit");
        check(aged.dropped==2,"expired frame and dependent P counted");
        check(aged.video(new byte[]{3},true),"new key resumes aged queue");
        time.addAndGet(100);
        check(aged.take().payload[0]==3 && aged.lastTakenAgeMillis==100,"queue residence measured");
        aged.close();
        TouchInput.Event touch=TouchInput.parse("motionevent3 640 240 0 50 down");
        check(touch!=null && touch.action==TouchInput.DOWN,"actual car v3 down parsed");
        check(TouchInput.parse("motionevent3 640 240 1 50 down").id==1,"second pointer slot retained");
        check(TouchInput.isCommit("motionevent3 0 0 0 0 comm"),"v3 synchronization marker is not a tap");
        check(TouchInput.parse("motionevent3 0 0 0 0 comm")==null,"sync marker ignored");
        check(TouchInput.parse("motionevent 40 50 move").action==TouchInput.MOVE,"legacy event accepted");
        for(String invalid:new String[]{"motionevent -1 2 down","motionevent3 1 2 0 -1 down","motionevent3 1 2 0 50 tap","motionevent3 x 2 0 50 down","motionevent3 1 2 0 50 down junk"})
            check(TouchInput.parse(invalid)==null,"malformed touch rejected");
        float[] mapped=TouchInput.map(touch,1280,480,3200,1440,3200,1440);
        check(mapped!=null && Math.abs(mapped[0]-1600)<.1 && Math.abs(mapped[1]-720)<.1,"landscape center maps to physical display");
        check(TouchInput.map(new TouchInput.Event(0,50,200),1280,480,3200,1440,3200,1440)==null,"landscape side bar cannot inject");
        check(TouchInput.map(new TouchInput.Event(0,500,240),1280,480,1440,3200,1440,3200)==null,"portrait side bar cannot inject");
        check(TouchInput.map(touch,1280,480,1440,3200,3200,1440)==null,"rotation transition mismatch rejected");
        if(args.length>0){
            int events=0,commits=0,downs=0,ups=0,moves=0;
            try(java.nio.file.DirectoryStream<java.nio.file.Path> paths=java.nio.file.Files.newDirectoryStream(java.nio.file.Paths.get(args[0]),"*-touch.txt")){
                for(java.nio.file.Path path:paths){
                    for(String line:java.nio.file.Files.readAllLines(path,java.nio.charset.StandardCharsets.UTF_8)){
                        line=line.replace("\uFEFF","").trim();if(line.isEmpty())continue;
                        if(TouchInput.isCommit(line)){commits++;continue;}
                        TouchInput.Event event=TouchInput.parse(line);check(event!=null,"recorded car event parses");events++;
                        if(event.action==0)downs++;if(event.action==1)ups++;if(event.action==2)moves++;
                    }
                }
            }
            check(events==82 && commits==82 && downs==21 && ups==21 && moves==40,"entire new car touch trace replayed");
        }
        byte[] avcc={0,0,0,2,0x67,0x42,0,0,0,1,0x68};
        check(Arrays.equals(AnnexB.normalize(avcc),new byte[]{0,0,0,1,0x67,0x42,0,0,0,1,0x68}),"AVCC conversion");
        boolean rejected=false;
        try { AnnexB.normalize(new byte[]{0,0,0,9,0x67}); } catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"truncated NAL rejected");
        byte[] sps={0,0,0,1,0x67,0x42};
        byte[] pps={0,0,0,1,0x68,0x11};
        byte[] idr={0,0,0,1,0x65,0x22};
        byte[] au=AnnexB.join(AnnexB.join(sps,pps),idr);
        List<byte[]> packets=AnnexB.welinkPayloads(au);
        check(packets.size()==3,"A7 needs three WL payloads for SPS/PPS/IDR");
        check(Arrays.equals(packets.get(0),sps),"SPS is independent");
        check(Arrays.equals(packets.get(1),pps),"PPS is independent");
        check(Arrays.equals(packets.get(2),idr),"IDR is independent");
        byte[] sei={0,0,1,0x06,0,0,3,1,0x22};
        byte[] secondSlice={0,0,1,0x65,0x33};
        packets=AnnexB.welinkPayloads(AnnexB.join(au,AnnexB.join(sei,secondSlice)));
        check(packets.size()==3,"multi-slice picture remains a single payload");
        check(packets.get(2).length==idr.length+sei.length+1+secondSlice.length+1,
                "mixed start codes canonicalized without splitting slices");
        final List<Integer> writes=new ArrayList<>();
        ByteArrayOutputStream wire=new ByteArrayOutputStream(){
            @Override public synchronized void write(byte[] data,int offset,int length){
                writes.add(length);super.write(data,offset,length);
            }
        };
        WlPacketWriter.write(wire,new WlFrame(6,au),null);
        check(writes.equals(Arrays.asList(8,6,8,6,8,6)),"separate headers and full payload writes");
        List<WlFrame> wireFrames=new ArrayList<>();byte[] serialized=wire.toByteArray();
        new WlFrame.Parser().feed(serialized,serialized.length,wireFrames::add);
        check(wireFrames.size()==3 && wireFrames.get(2).type==6,"three independently framed video packets");
        byte[] largeIdr=new byte[30000];largeIdr[3]=1;largeIdr[4]=0x65;
        wire.reset();writes.clear();WlPacketWriter.write(wire,new WlFrame(6,largeIdr),null);
        check(writes.equals(Arrays.asList(8,30000)),"do not add arbitrary 16 KiB USB boundaries");
        wire.reset();writes.clear();WlPacketWriter.write(wire,new WlFrame(103,new byte[0]),null);
        check(writes.equals(Arrays.asList(8)),"zero-payload heartbeat writes only its header");
        System.out.println("PASS " + checks + " assertions; 500 frames across five USB fragmentation patterns");
    }
}
