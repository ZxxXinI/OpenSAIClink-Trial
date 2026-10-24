package org.opensaiclink.app;
import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.*;
import android.view.Surface;
import java.nio.*;
import java.util.concurrent.*;
/** One GPU pass removes mirror bars for modest landscape aspect differences. */
public final class DisplayRenderer {
    private final HandlerThread thread=new HandlerThread("wl-display-fit");
    private final Handler handler;
    private final int width,height;
    private final Surface output;
    private final Runnable failure;
    private Surface input;
    private SurfaceTexture texture;
    private EGLDisplay egl=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface window=EGL14.EGL_NO_SURFACE;
    private int program,textureId,pos,uv,matrix,crop;
    private boolean queued,closed;
    private final float[] transform=new float[16];
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private volatile Exception initError;
    public DisplayRenderer(Surface output,int width,int height,Runnable failure)throws Exception{
        this.output=output;this.width=width;this.height=height;this.failure=failure;
        vertices.put(new float[]{-1,-1,0,0,1,-1,1,0,-1,1,0,1,1,1,1,1}).position(0);
        thread.start();handler=new Handler(thread.getLooper());CountDownLatch ready=new CountDownLatch(1);
        handler.post(()->{try{initialize();}catch(Exception e){initError=e;}finally{ready.countDown();}});
        if(!ready.await(5,TimeUnit.SECONDS)){close();throw new IllegalStateException("Display renderer initialization timed out");}
        if(initError!=null){close();throw initError;}
    }
    public Surface input(){return input;}
    private void initialize(){
        egl=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];
        require(EGL14.eglInitialize(egl,version,0,version,1),"eglInitialize");
        int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE};
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        require(EGL14.eglChooseConfig(egl,attrs,0,configs,0,1,count,0) && count[0]>0,"eglChooseConfig");
        context=EGL14.eglCreateContext(egl,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        window=EGL14.eglCreateWindowSurface(egl,configs[0],output,new int[]{EGL14.EGL_NONE},0);
        require(EGL14.eglMakeCurrent(egl,window,window,context),"eglMakeCurrent");
        int vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p;attribute vec2 t;uniform mat4 m;uniform vec4 c;varying vec2 v;void main(){gl_Position=vec4(p,0.,1.);v=(m*vec4(c.xy+t*c.zw,0.,1.)).xy;}");
        int fragment=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES s;varying vec2 v;void main(){gl_FragColor=texture2D(s,v);}");
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
        int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);require(linked[0]!=0,"Shader link");
        pos=GLES20.glGetAttribLocation(program,"p");uv=GLES20.glGetAttribLocation(program,"t");matrix=GLES20.glGetUniformLocation(program,"m");crop=GLES20.glGetUniformLocation(program,"c");
        int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);textureId=ids[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        texture=new SurfaceTexture(textureId);texture.setDefaultBufferSize(width,height);input=new Surface(texture);
        texture.setOnFrameAvailableListener(ignored->{if(!closed && !queued){queued=true;handler.post(this::draw);}},handler);
        Trace.log("DISPLAY_RENDERER","GPU fit enabled; portrait remains aspect-fitted");
    }
    private void draw(){
        queued=false;if(closed)return;long start=System.nanoTime();
        try{
            texture.updateTexImage();texture.getTransformMatrix(transform);
            boolean fill=DisplayGeometry.canFill(width,height,Status.contentWidth,Status.contentHeight);
            float[] rect=DisplayGeometry.crop(width,height,Status.contentWidth,Status.contentHeight,fill);
            GLES20.glViewport(0,0,width,height);GLES20.glUseProgram(program);
            vertices.position(0);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(pos);
            vertices.position(2);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(uv);
            GLES20.glUniformMatrix4fv(matrix,1,false,transform,0);GLES20.glUniform4fv(crop,1,rect,0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);require(GLES20.glGetError()==GLES20.GL_NO_ERROR,"GPU draw");
            EGLExt.eglPresentationTimeANDROID(egl,window,texture.getTimestamp());require(EGL14.eglSwapBuffers(egl,window),"eglSwapBuffers");
            if(Status.fillVideo!=fill)Trace.log("DISPLAY_FIT","fill="+fill+" content="+Status.contentWidth+"x"+Status.contentHeight);
            Status.fillVideo=fill;Status.renderFrames.incrementAndGet();Status.renderMs=(System.nanoTime()-start)/1000000L;Status.maxRenderMs.accumulateAndGet(Status.renderMs,Math::max);
        }catch(Exception e){Trace.log("DISPLAY_RENDER_ERROR",e.toString());closed=true;failure.run();}
    }
    private static int shader(int type,String source){
        int id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);int[] ok=new int[1];
        GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0);require(ok[0]!=0,"Shader compile: "+GLES20.glGetShaderInfoLog(id));return id;
    }
    private static void require(boolean ok,String label){if(!ok)throw new IllegalStateException(label);}
    public void close(){
        CountDownLatch done=new CountDownLatch(1);handler.post(()->{
            closed=true;
            try{
                if(texture!=null)texture.setOnFrameAvailableListener(null);
                if(input!=null)input.release();if(texture!=null)texture.release();
                if(program!=0)GLES20.glDeleteProgram(program);if(textureId!=0)GLES20.glDeleteTextures(1,new int[]{textureId},0);
                if(egl!=EGL14.EGL_NO_DISPLAY){
                    EGL14.eglMakeCurrent(egl,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                    if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(egl,window);
                    if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(egl,context);
                    EGL14.eglReleaseThread();EGL14.eglTerminate(egl);
                }
            }finally{Status.fillVideo=false;done.countDown();thread.quitSafely();}
        });
        try{if(!done.await(3,TimeUnit.SECONDS))Trace.log("DISPLAY_CLOSE_TIMEOUT","Renderer did not close within 3 seconds");}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
