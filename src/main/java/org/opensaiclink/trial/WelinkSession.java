package org.opensaiclink.trial;

import android.os.Build;
import org.json.*;
import java.nio.charset.StandardCharsets;

public final class WelinkSession {
    public interface Listener {
        void configurationChanged();void sourceChanged(boolean paused);
        default void touch(TouchInput.Event event){} default void touchCommit(){} default void back(){}
    }
    private final AoaTransport transport;
    private final Listener listener;
    private boolean unsupportedCodec;

    public WelinkSession(AoaTransport transport,Listener listener){this.transport=transport;this.listener=listener;}
    public static JSONObject command(String module,String method,JSONObject extra) throws JSONException {
        JSONObject command=new JSONObject().put("method",method).put("extData",extra);
        return new JSONObject().put("moduleName",module).put("version",0)
                .put("platform","android").put("command",command);
    }
    private void send(String module,String method,JSONObject extra) throws JSONException {
        String data=command(module,method,extra).toString();
        Trace.log("TX_JSON",data);transport.send(9,data.getBytes(StandardCharsets.UTF_8));
    }
    public void receive(WlFrame frame){
        if(frame.type==103){transport.send(103,new byte[0]);return;}
        if(frame.type==101 || frame.type==102 || frame.type==104 || frame.type==105 || frame.type==106){
            Trace.log("RX_CONTROL","type="+frame.type+" bytes="+frame.payload.length);
            if(frame.type==104 && frame.payload.length>=4){
                long result=((long)(frame.payload[0]&255)<<24)|((long)(frame.payload[1]&255)<<16)
                        |((long)(frame.payload[2]&255)<<8)|(frame.payload[3]&255);
                Trace.log("AUTH_RESULT","HU type 104 result="+result+"; this alone does not prove session success");
                if(result==0)throw new IllegalStateException("HU rejected accessory handshake (104=0)");
            }
            return;
        }
        if(frame.type==107){Trace.log("UNSUPPORTED_TUNNEL","HU sent type 107; protocol-1 session needs separate validation");return;}
        if(frame.type!=8 && frame.type!=9){
            if(frame.type!=13)Trace.log("RX_OTHER","type="+frame.type+" bytes="+frame.payload.length);return;
        }
        if(frame.payload.length>65536){Trace.log("RX_REJECT","Oversize control payload");return;}
        String text=new String(frame.payload,StandardCharsets.UTF_8).trim();
        Trace.log("RX_"+frame.type,text);
        try{
            if(text.startsWith("motionevent")){
                if(TouchInput.isCommit(text)){listener.touchCommit();return;}
                TouchInput.Event event=TouchInput.parse(text);
                if(event!=null){Status.touchReceived.incrementAndGet();listener.touch(event);}
                else Trace.log("TOUCH_UNSUPPORTED",text);
                return;
            }
            if("keyevent 4".equals(text)){listener.back();return;}
            if(text.startsWith("config ")){
                int fps=Integer.parseInt(text.split("\\s+")[1]);
                Status.fps=Math.max(5,Math.min(30,fps));Status.configured=!unsupportedCodec;
                Status.stage=unsupportedCodec?"Unsupported HU codec":"HU config received; display needs car check";
                listener.configurationChanged();return;
            }
            if(!text.startsWith("{"))return;
            JSONObject root=new JSONObject(text),cmd=root.optJSONObject("command");if(cmd==null)return;
            String method=cmd.optString("method");
            JSONObject extra=cmd.optJSONObject("extData");if(extra==null)extra=new JSONObject();
            if("WeLink_SDK".equals(root.optString("moduleName"))){
                if("onHuReady".equals(method)){
                    int w=extra.optInt("width",1280),h=extra.optInt("height",720);
                    if(w>=240 && w<=4096 && h>=160 && h<=2160 && !Status.capturing){
                        double scale=Math.min(1.0,Math.min(1280.0/w,720.0/h));
                        Status.width=Math.max(240,((int)(w*scale)/16)*16);
                        Status.height=Math.max(160,((int)(h*scale)/16)*16);
                    }
                    unsupportedCodec=extra.optInt("codecMode",2)==1;
                    send("WeLink_SDK","onMuReady",new JSONObject().put("width",Status.width)
                            .put("height",Status.height).put("systemVersion",Build.VERSION.SDK_INT));
                    Status.stage="onHuReady received / onMuReady sent";
                }else if("requestWeLink".equals(method)){
                    if(unsupportedCodec){Trace.log("UNSUPPORTED_CODEC","HU requested JPEG-only mode");return;}
                    JSONObject vers=new JSONObject();
                    for(String key:new String[]{"wlVer","huVer","muVer"})
                        vers.put(key,new JSONObject().put("version","versions").put("path","path"));
                    send("WeLink_SDK","responseWeLink",new JSONObject().put("state",1).put("errCode",0)
                            .put("responseCode",0).put("displayCode","").put("codecMode",2)
                            .put("width",Status.width).put("height",Status.height).put("hasNewVers",false).put("vers",vers));
                    Status.stage="requestWeLink replied; waiting for config";
                }else if("reqHeartbeat".equals(method)){
                    if(extra.optInt("heartbeatType",1)==1)send("WeLink_SDK","onHeartbeat",new JSONObject());
                }else if("onHuSourceChange".equals(method)){
                    String value=extra.optString("status","");
                    if("0".equals(value))listener.sourceChanged(true);
                    else if("1".equals(value))listener.sourceChanged(false);
                    else Trace.log("SOURCE_UNKNOWN","Unrecognized status="+value);
                }
            }else if("onHuAppFront".equals(method) && extra.has("state")){
                listener.sourceChanged(!extra.optBoolean("state",false));
            }else if("onHuChannel".equals(method) && extra.has("pressDownKey")){
                send("WeLink","onMuChannel",new JSONObject().put("pressDownKey",extra.optInt("pressDownKey",1)));
            }
        }catch(Exception e){Trace.log("CONTROL_ERROR",e.toString());}
    }
    public void resume(String mapPackage){
        try{send("WeLink","onResumeWelink",new JSONObject().put("packageName",mapPackage));}
        catch(JSONException e){Trace.log("JSON_ERROR",e.toString());}
    }
    public void noUpdate(){transport.send(255,"the screen does not update".getBytes(StandardCharsets.UTF_8));}
}
