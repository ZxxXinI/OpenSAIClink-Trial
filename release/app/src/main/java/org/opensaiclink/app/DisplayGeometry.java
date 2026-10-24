package org.opensaiclink.app;

/** Shared GPU crop and inverse input transform. Portrait remains aspect-fitted. */
public final class DisplayGeometry {
    private DisplayGeometry(){}
    public static boolean canFill(int videoW,int videoH,int contentW,int contentH){
        if(videoW<=0 || videoH<=0 || contentW<=contentH || contentH<=0)return false;
        double ratio=((double)videoW/videoH)/((double)contentW/contentH);
        return ratio>=.7 && ratio<=1.5;
    }
    public static float[] crop(int videoW,int videoH,int contentW,int contentH,boolean fill){
        if(!fill || !canFill(videoW,videoH,contentW,contentH))return new float[]{0,0,1,1};
        double scale=Math.min((double)videoW/contentW,(double)videoH/contentH);
        float width=(float)(contentW*scale/videoW),height=(float)(contentH*scale/videoH);
        return new float[]{(1-width)/2,(1-height)/2,width,height};
    }
}
