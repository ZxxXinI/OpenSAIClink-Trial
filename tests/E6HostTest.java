package org.opensaiclink.trial;
import java.nio.file.*;
import java.util.*;
public final class E6HostTest {
    static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        TouchFrames frames=new TouchFrames();
        frames.accept(new TouchInput.Event(0,800,200,0));frames.commit();frames.accept(new TouchInput.Event(0,500,400,1));frames.commit();
        frames.accept(new TouchInput.Event(1,500,400,0));TouchFrames.Frame up=frames.commit();
        check(!up.points.get(0).down && up.points.get(0).x==800 && up.points.get(0).y==200,"bad A7 UP never moves released finger onto another");
        check(up.points.get(1).down && up.points.get(1).x==500,"other finger remains held");frames.clear();
        Map<Integer,int[]> positions=new HashMap<>();int corrected=0,events=0;
        for(String line:Files.readAllLines(Paths.get(args[0]),java.nio.charset.StandardCharsets.UTF_8)){
            int start=line.indexOf("motionevent");if(start<0)continue;String command=line.substring(start);
            if(TouchInput.isCommit(command)){frames.commit();continue;}
            TouchInput.Event e=TouchInput.parse(command);check(e!=null,"real command parses");events++;
            int[] previous=positions.get(e.id);check(frames.accept(e),"real pointer lifecycle");
            if(e.action==TouchInput.UP){
                TouchFrames.Frame f=frames.commit();TouchFrames.Point released=null;for(TouchFrames.Point p:f.points)if(p.id==e.id)released=p;
                check(released!=null && previous!=null && released.x==previous[0] && released.y==previous[1],"real UP preserves last valid position");
                if(e.x!=previous[0] || e.y!=previous[1])corrected++;
                positions.remove(e.id);
            }else positions.put(e.id,new int[]{e.x,e.y});
        }
        check(positions.isEmpty() && corrected>=20,"all released, many observed bad UPs corrected");
        System.out.println("PASS "+checks+" E6 checks; "+events+" real events, "+corrected+" inconsistent UP coordinates corrected");
    }
}
