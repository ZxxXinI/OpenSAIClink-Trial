package org.opensaiclink.trial;

/** A7 type-8 pointer commands; never treat unknown text as executable input. */
public final class TouchInput {
    public static final int DOWN=0, UP=1, MOVE=2;
    public static final class Event {
        public final int action,x,y,id;
        public Event(int action,int x,int y){this(action,x,y,0);}
        public Event(int action,int x,int y,int id){this.action=action;this.x=x;this.y=y;this.id=id;}
    }
    private TouchInput(){}
    public static boolean isCommit(String text){return "motionevent3 0 0 0 0 comm".equals(text.trim());}
    public static Event parse(String text){
        String[] words=text.trim().split("\\s+");
        boolean legacy=words.length==4 && "motionevent".equals(words[0]);
        boolean v3=words.length==6 && "motionevent3".equals(words[0]);
        if(!legacy && !v3)return null;
        String kind=words[words.length-1];
        int action="down".equals(kind)?DOWN:"up".equals(kind)?UP:"move".equals(kind)?MOVE:-1;
        if(action<0)return null;
        try{
            int x=Integer.parseInt(words[1]),y=Integer.parseInt(words[2]);
            int id=v3?Integer.parseInt(words[3]):0;
            if(id<0 || id>9 || (v3 && (Integer.parseInt(words[4])<0 || Integer.parseInt(words[4])>255)))return null;
            if(x<0 || y<0 || x>16384 || y>16384)return null;
            return new Event(action,x,y,id);
        }catch(NumberFormatException e){return null;}
    }
    /** MediaProjection scales uniformly and centers the captured display. */
    public static float[] map(Event event,int videoWidth,int videoHeight,int contentWidth,int contentHeight,int screenWidth,int screenHeight){
        return map(event,videoWidth,videoHeight,contentWidth,contentHeight,screenWidth,screenHeight,false);
    }
    public static float[] map(Event event,int videoWidth,int videoHeight,int contentWidth,int contentHeight,int screenWidth,int screenHeight,boolean fill){
        if(videoWidth<=0 || videoHeight<=0 || contentWidth<=0 || contentHeight<=0 || screenWidth<=0 || screenHeight<=0)return null;
        if(event.x>=videoWidth || event.y>=videoHeight)return null;
        double contentAspect=(double)contentWidth/contentHeight;
        if(Math.abs(contentAspect-(double)screenWidth/screenHeight)>contentAspect*.03)return null;
        if(fill && DisplayGeometry.canFill(videoWidth,videoHeight,contentWidth,contentHeight))
            return new float[]{(float)event.x*screenWidth/videoWidth,(float)event.y*screenHeight/videoHeight};
        double scale=Math.min((double)videoWidth/contentWidth,(double)videoHeight/contentHeight);
        double left=(videoWidth-contentWidth*scale)/2,top=(videoHeight-contentHeight*scale)/2;
        double x=(event.x-left)/scale,y=(event.y-top)/scale;
        if(x<0 || y<0 || x>=contentWidth || y>=contentHeight)return null;
        return new float[]{(float)Math.min(screenWidth-1,x*screenWidth/contentWidth),(float)Math.min(screenHeight-1,y*screenHeight/contentHeight)};
    }
}
