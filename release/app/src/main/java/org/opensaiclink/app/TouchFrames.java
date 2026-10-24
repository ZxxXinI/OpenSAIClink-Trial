package org.opensaiclink.app;

import java.util.*;

/** Build complete pointer snapshots; only motion-only snapshots may be coalesced. */
public final class TouchFrames {
    public static final class Point {
        public final int id,x,y;public final boolean down;
        Point(int id,int x,int y,boolean down){this.id=id;this.x=x;this.y=y;this.down=down;}
    }
    public static final class Frame {
        public final List<Point> points;public final boolean transition;
        Frame(List<Point> points,boolean transition){this.points=Collections.unmodifiableList(points);this.transition=transition;}
        public int held(){int n=0;for(Point p:points)if(p.down)n++;return n;}
    }
    private final TreeMap<Integer,Point> points=new TreeMap<>();
    private boolean dirty,transition;
    public boolean accept(TouchInput.Event e){
        Point prior=points.get(e.id);
        if(e.action==TouchInput.DOWN){
            if(prior!=null || points.size()>=2)return false;
            points.put(e.id,new Point(e.id,e.x,e.y,true));transition=true;
        }else{
            if(prior==null || !prior.down)return false;
            boolean held=e.action!=TouchInput.UP;
            // A7 can repeat another finger coordinate in UP. Never move a finger while releasing it.
            points.put(e.id,new Point(e.id,held?e.x:prior.x,held?e.y:prior.y,held));if(!held)transition=true;
        }
        dirty=true;return true;
    }
    public Frame commit(){
        if(!dirty)return null;
        Frame frame=new Frame(new ArrayList<>(points.values()),transition);
        points.values().removeIf(p->!p.down);dirty=false;transition=false;return frame;
    }
    public void clear(){points.clear();dirty=false;transition=false;}
}
