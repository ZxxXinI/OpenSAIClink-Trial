package org.opensaiclink.trial;
import java.nio.file.*;
import java.util.*;
public final class E5HostTest {
    static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static TouchInput.Event e(int action,int id){return new TouchInput.Event(action,640+id*100,240,id);}
    public static void main(String[] args)throws Exception{
        TouchFrames f=new TouchFrames();check(f.accept(e(0,0)),"first down");check(f.commit().held()==1,"one pointer");
        check(f.accept(e(0,1)),"second down");check(f.commit().held()==2,"two pointers");
        check(!f.accept(e(0,2)),"third pointer rejected");
        check(f.accept(e(1,0)),"primary lifts first");TouchFrames.Frame released=f.commit();
        check(released.held()==1 && released.points.size()==2 && released.transition,"release retained while secondary holds");
        check(f.accept(e(2,1)),"secondary continues");check(f.commit().points.size()==1,"released primary gone");
        check(f.accept(e(1,1)) && f.commit().held()==0,"all lifted");check(f.commit()==null,"comm alone has no gesture");
        check(!f.accept(e(2,1)),"orphan move rejected");f.clear();
        float[] crop=DisplayGeometry.crop(1280,480,3200,1440,true);
        check(Math.abs(crop[0]-1f/12)<.0001 && Math.abs(crop[2]-5f/6)<.0001,"K80 letterbox crop");
        float[] xy=TouchInput.map(new TouchInput.Event(0,32,24),1280,480,3200,1440,3200,1440,true);
        check(Math.abs(xy[0]-80)<.01 && Math.abs(xy[1]-72)<.01,"filled edge maps to physical coordinates");
        check(!DisplayGeometry.canFill(1280,480,1440,3200),"never distort portrait");
        check(Arrays.equals(DisplayGeometry.crop(1280,480,1440,3200,true),new float[]{0,0,1,1}),"portrait stays fitted");
        check(TouchInput.map(e(0,0),1280,480,1440,3200,3200,1440,true)==null,"rotation transition guarded");
        int twoStarts=0,max=0,previous=0,events=0;
        for(String line:Files.readAllLines(Paths.get(args[0]),java.nio.charset.StandardCharsets.UTF_8)){
            int start=line.indexOf("motionevent");if(start<0)continue;String cmd=line.substring(start);
            if(TouchInput.isCommit(cmd)){
                TouchFrames.Frame frame=f.commit();if(frame!=null){int held=frame.held();if(held==2 && previous<2)twoStarts++;previous=held;max=Math.max(max,held);}
            }else{TouchInput.Event event=TouchInput.parse(cmd);check(event!=null && f.accept(event),"recorded lifecycle "+line);events++;}
        }
        check(events==2698 && twoStarts==9 && max==2 && previous==0,"entire real two-pointer trace and clean final release");
        System.out.println("PASS "+checks+" E5 checks; "+events+" real touch events, "+twoStarts+" two-pointer starts");
    }
}
