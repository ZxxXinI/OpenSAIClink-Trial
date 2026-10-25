package org.opensaiclink.trial;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
public final class GlassUi {
    public final Context c;
    public final boolean dark;
    public final int ink,muted,accent=0xff24dedb;
    public GlassUi(Context c){
        this.c=c;
        String mode=new SettingsStore(c).theme();
        dark="dark".equals(mode)||("system".equals(mode)&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        ink=dark?0xfff4f8ff:0xff142d48;
        muted=dark?0xffabc0d7:0xff4e6982;
    }
    public int dp(float n){
        return Math.round(n*c.getResources().getDisplayMetrics().density);
    }
    public TextView text(String value,float size,int color){
        TextView t=new TextView(c);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setFontFeatureSettings("kern");
        return t;
    }
    public Drawable glass(boolean bright){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,dark?new int[]{
            bright?0xff174b68:0xee233a58,0xf0102037
        }
        :new int[]{
            0xfaffffff,bright?0xffd1f4f6:0xffe4eef8
        }
        );
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1),dark?0x708ac8ed:0x88ffffff);
        return new RippleDrawable(ColorStateList.valueOf(dark?0x225fffff:0x22489ca8),g,null);
    }
    public LinearLayout column(){
        LinearLayout v=new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    public LinearLayout card(){
        LinearLayout v=column();
        v.setPadding(dp(18),dp(16),dp(18),dp(16));
        v.setBackground(glass(false));
        return v;
    }
    public Button button(String label,boolean primary,View.OnClickListener listener){
        Button b=new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(primary?0xff062832:ink);
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{
            primary?0xff19cdd6:(dark?0xff19304a:0xffe0edf6),primary?0xff45e8d8:(dark?0xff203953:0xffedf6ff)
        }
        );
        g.setCornerRadius(dp(22));
        g.setStroke(dp(1),primary?0xff61fff0:0x5595c5e6);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),g,null));
        b.setMinHeight(dp(50));
        b.setPadding(dp(14),dp(8),dp(14),dp(8));
        b.setOnClickListener(listener);
        return b;
    }
    public void add(LinearLayout parent,View child,int height,int gap){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));
        lp.topMargin=dp(gap);
        parent.addView(child,lp);
    }
    public LinearLayout row(String title,String value,View.OnClickListener click){
        LinearLayout r=new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(2),dp(15),dp(2),dp(15));
        TextView a=text(title,16,ink),b=text(value+(click==null?"":"  \u203a"),14,muted);
        r.addView(a,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(b);
        if(click!=null){
            r.setBackground(glass(false));
            r.setPadding(dp(14),dp(15),dp(14),dp(15));
            r.setOnClickListener(click);
        }
        return r;
    }
    public void install(Activity a,View root){
        root.setBackground(new Backdrop(dark));
        a.setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(b.left,b.top,b.right,b.bottom);
            return insets;
        }
        );
        WindowInsetsController ctl=a.getWindow().getInsetsController();
        if(ctl!=null)ctl.setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    }
    public Drawable appIcon(String pkg,int index){
        try{
            if(!pkg.isEmpty()&&!"#all".equals(pkg))return c.getPackageManager().getApplicationIcon(pkg);
        }
        catch(Exception ignored){
        }
        return new Symbol(index==5?"grid":index==2&&pkg.isEmpty()?"music":"plus",accent);
    }
    public String appName(String pkg,int index){
        if(index==5)return "\u5168\u90e8\u5e94\u7528";
        if(pkg.isEmpty())return index==2?"\u9009\u62e9\u97f3\u4e50\u5e94\u7528":"\u6dfb\u52a0\u5e94\u7528";
        try{
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();
        }
        catch(Exception ignored){
            return "com.autonavi.minimap".equals(pkg)?"\u9ad8\u5fb7\u5730\u56fe \u00b7 \u672a\u5b89\u88c5":"com.baidu.BaiduMap".equals(pkg)?"\u767e\u5ea6\u5730\u56fe \u00b7 \u672a\u5b89\u88c5":"\u5e94\u7528\u672a\u5b89\u88c5";
        }
    }
    public View tile(String pkg,int index,boolean compact,View.OnClickListener click,View.OnLongClickListener hold){
        LinearLayout v=new LinearLayout(c);
        v.setGravity(Gravity.CENTER);
        v.setOrientation(compact?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        v.setPadding(dp(compact?6:14),dp(7),dp(compact?6:12),dp(7));
        v.setBackground(glass(index<3));
        ImageView image=new ImageView(c);
        image.setImageDrawable(appIcon(pkg,index));
        v.addView(image,new LinearLayout.LayoutParams(dp(compact?30:38),dp(compact?30:38)));
        TextView label=text(appName(pkg,index),compact?12:19,ink);
        label.setGravity(compact?Gravity.CENTER:Gravity.CENTER_VERTICAL);
        label.setMaxLines(compact?2:1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if(!compact){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            lp.leftMargin=dp(12);
            v.addView(label,lp);
        }
        else {
            label.setPadding(0,dp(5),0,0);
            v.addView(label);
        }
        v.setContentDescription(appName(pkg,index));
        v.setOnClickListener(click);
        v.setFocusable(true);
        if(hold!=null)v.setOnLongClickListener(hold);
        return v;
    }
    static final class Backdrop extends Drawable {
        final boolean dark;
        final Paint p=new Paint(3);
        Backdrop(boolean d){
            dark=d;
        }
        public void draw(Canvas c){
            Rect r=getBounds();
            p.setShader(new LinearGradient(0,0,r.width(),r.height(),dark?new int[]{
                0xff071528,0xff0b2839,0xff081428
            }
            :new int[]{
                0xffeef5ff,0xffddf2f5,0xfff7faff
            }
            ,null,Shader.TileMode.CLAMP));
            c.drawRect(r,p);
            p.setShader(new RadialGradient(r.width()*.9f,r.height()*.15f,Math.max(1,r.width()*.7f),dark?0x4448c9dc:0x5560e4df,0x00000000,Shader.TileMode.CLAMP));
            c.drawRect(r,p);
            p.setShader(null);
        }
        public void setAlpha(int a){
        }
        public void setColorFilter(ColorFilter f){
        }
        public int getOpacity(){
            return PixelFormat.OPAQUE;
        }
    }
    static final class Symbol extends Drawable {
        final String kind;
        final Paint p=new Paint(3);
        final int color;
        Symbol(String k,int c){
            kind=k;
            color=c;
        }
        public void draw(Canvas c){
            Rect b=getBounds();
            c.save();
            c.translate(b.left,b.top);
            c.scale(b.width()/48f,b.height()/48f);
            p.setColor(color);
            p.setStrokeWidth(3);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStyle(Paint.Style.STROKE);
            if("grid".equals(kind)){
                for(int y=0;y<2;y++)for(int x=0;x<2;x++)c.drawRoundRect(6+x*21,6+y*21,21+x*21,21+y*21,4,4,p);
            }
            else if("music".equals(kind)){
                c.drawLine(19,34,19,12,p);
                c.drawLine(19,12,37,8,p);
                c.drawLine(37,8,37,30,p);
                c.drawOval(8,30,19,40,p);
                c.drawOval(26,26,37,36,p);
            }
            else{
                c.drawRoundRect(5,5,43,43,11,11,p);
                c.drawLine(24,14,24,34,p);
                c.drawLine(14,24,34,24,p);
            }
            c.restore();
        }
        public void setAlpha(int a){
            p.setAlpha(a);
        }
        public void setColorFilter(ColorFilter f){
            p.setColorFilter(f);
        }
        public int getOpacity(){
            return PixelFormat.TRANSLUCENT;
        }
    }
}
