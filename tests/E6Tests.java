package org.opensaiclink.trial;
import java.util.*;
/** Small deterministic suite for builds without private capture corpora. */
public final class E6Tests {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        check(TouchInput.isCommit("motionevent3 0 0 0 0 comm"),"commit marker");
        check(TouchInput.parse("motionevent3 0 0 0 0 comm")==null,"commit is not a touch");
        TouchInput.Event second=TouchInput.parse("motionevent3 900 200 1 50 down");
        check(second!=null && second.id==1 && second.action==TouchInput.DOWN,"second pointer parses");
        check(TouchInput.parse("motionevent3 1 2 0 50 tap")==null,"unknown action rejected");
        TouchFrames frames=new TouchFrames();
        check(frames.accept(new TouchInput.Event(0,100,100,0)),"first down");
        check(frames.accept(new TouchInput.Event(0,900,200,1)),"second down");
        check(frames.commit().held()==2,"two held pointers");
        check(frames.accept(new TouchInput.Event(1,900,200,0)),"primary up");
        TouchFrames.Frame released=frames.commit();
        check(!released.points.get(0).down && released.points.get(0).x==100,"released pointer keeps old position");
        check(released.points.get(1).down,"other pointer remains held");
        check(frames.accept(new TouchInput.Event(1,900,200,1)),"final up");
        check(frames.commit().held()==0,"all released");
        check(!frames.accept(new TouchInput.Event(2,1,1,0)),"orphan move rejected");
        frames.clear();
        check(!DisplayGeometry.canFill(1280,480,1440,3200),"portrait is never stretched");
        check(DisplayGeometry.canFill(1280,480,3200,1440),"landscape fit allowed");
        float[] crop=DisplayGeometry.crop(1280,480,3200,1440,true);
        check(Math.abs(crop[2]-5f/6)<.0001,"landscape crop removes side bars");
        float[] edge=TouchInput.map(new TouchInput.Event(0,32,24),1280,480,3200,1440,3200,1440,true);
        check(edge!=null && Math.abs(edge[0]-80)<.01 && Math.abs(edge[1]-72)<.01,"filled coordinates inverse map");
        check(TouchInput.map(new TouchInput.Event(0,640,240),1280,480,1440,3200,3200,1440,true)==null,"rotation mismatch rejected");
        check(CarHomeActivity.shortcutKey(0).equals("car_shortcut_package"),"original favorite key");
        check(CarHomeActivity.shortcutKey(1).equals("car_shortcut_package_1"),"slot one key");
        check(CarHomeActivity.shortcutKey(3).equals("car_shortcut_package_3"),"slot three key");
        System.out.println("PASS "+checks+" clean-checkout E6 checks");
    }
}
