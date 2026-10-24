package org.opensaiclink.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.Intent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;
import java.io.FileOutputStream;

/** Lightweight in-app UI probe; system permissions remain an ADB orchestrated scenario. */
public final class UiGate extends Instrumentation {
    private int checks;
    private Bundle result=new Bundle();
    private Bundle arguments;
    private void check(boolean condition,String label)throws Exception{
        checks++;
        Trace.log("UI_GATE",label+"="+condition);
        if(!condition)throw new AssertionError(label);
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String text){
        if(root==null)return null;
        CharSequence value=root.getText(),description=root.getContentDescription();
        if(text.contentEquals(value==null?"":value) || text.contentEquals(description==null?"":description))
            return root;
        for(int i=0;i<root.getChildCount();i++){
            AccessibilityNodeInfo found=find(root.getChild(i),text);
            if(found!=null)return found;
        }
        return null;
    }
    private AccessibilityNodeInfo waitForTarget(String activity,long timeoutMillis)throws Exception{
        long deadline=SystemClock.uptimeMillis()+timeoutMillis;
        while(SystemClock.uptimeMillis()<deadline){
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null && "org.opensaiclink.app".contentEquals(root.getPackageName()))
                return root;
            SystemClock.sleep(150);
        }
        return getUiAutomation().getRootInActiveWindow();
    }
    private void walkLabels(AccessibilityNodeInfo node,StringBuilder output,int depth){
        if(node==null || depth>30)return;
        CharSequence text=node.getText(),description=node.getContentDescription();
        Rect bounds=new Rect();
        node.getBoundsInScreen(bounds);
        if((text!=null && text.length()>0) || (description!=null && description.length()>0))
            output.append("NODE text=").append(text).append(" desc=").append(description)
                    .append(" class=").append(node.getClassName()).append(" bounds=").append(bounds)
                    .append(" clickable=").append(node.isClickable()).append('\n');
        for(int i=0;i<node.getChildCount();i++)walkLabels(node.getChild(i),output,depth+1);
    }
    private void screenshot(String name)throws Exception{
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){
            bitmap.compress(Bitmap.CompressFormat.PNG,100,output);
        }
    }
    @Override public void onCreate(Bundle arguments){
        super.onCreate(arguments);
        this.arguments=arguments;
        start();
    }
    @Override public void onStart(){
        try{
            Trace.init(getTargetContext());
            String activity=arguments.getString("activity","home");
            if("home".equals(activity)){
                getTargetContext().startActivity(new Intent()
                        .setClassName(getTargetContext(),"org.opensaiclink.app.MainActivity")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            }else if("settings".equals(activity)){
                getTargetContext().startActivity(new Intent()
                        .setClassName(getTargetContext(),"org.opensaiclink.app.SettingsActivity")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            }else if("car".equals(activity)){
                Status.capturing=true;
                Status.localTest=true;
                Status.usbOpen=false;
                Status.configured=false;
                getTargetContext().startActivity(CarHomeActivity.intent(getTargetContext()));
            }
            AccessibilityNodeInfo root=waitForTarget(activity,6000);
            check(root!=null,"home window available");
            check("org.opensaiclink.app".contentEquals(root.getPackageName()),"target package active");
            StringBuilder visibleLabels=new StringBuilder();
            walkLabels(root,visibleLabels,0);
            check(visibleLabels.length()>0,"accessible UI labels available");
            String[] labels="settings".equals(activity)
                    ?new String[]{"\u8bbe\u7f6e","\u8fde\u63a5\u4e0e\u663e\u793a","\u6743\u9650"}
                    :"car".equals(activity)
                    ?new String[]{"\u5bfc\u822a","\u6253\u5f00\u624b\u673a\u5bfc\u822a",
                        "\u5bfc\u822a\u4e0e\u5e38\u7528","\u8bbe\u7f6e","\u4e3b\u9875"}
                    :new String[]{"OpenSAICLink","\u51c6\u5907\u51fa\u53d1",
                        "\u5f00\u542f\u8f66\u673a\u89e6\u63a7","\u9ad8\u5fb7\u5730\u56fe","\u97f3\u4e50",
                        "\u6dfb\u52a0\u5e94\u7528"};
            for(String label:labels){
                check(find(root,label)!=null,"home control "+label);
            }
            if(arguments.getBoolean("screenshot",false))screenshot("ui-gate-"+activity);
            result.putString("result","PASS "+checks+" UI checks");
        }catch(Throwable e){
            Trace.log("UI_GATE_FAILURE",e.toString());
            result.putString("failure",e.toString());
        }
        finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
