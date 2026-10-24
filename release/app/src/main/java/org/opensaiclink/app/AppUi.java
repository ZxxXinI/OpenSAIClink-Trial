package org.opensaiclink.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Product-specific drawing helpers shared by the phone and vehicle pages. */
final class AppUi {
    private AppUi(){}

    static int dp(Context context,float value){
        return Math.round(value*context.getResources().getDisplayMetrics().density);
    }

    static int color(Context context,int color){
        return context.getColor(color);
    }

    static TextView text(Context context,CharSequence value,float size,int color){
        TextView view=new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    static TextView heading(Context context,CharSequence value,float size,int color){
        TextView view=text(context,value,size,color);
        view.setTypeface(view.getTypeface(),android.graphics.Typeface.BOLD);
        return view;
    }

    static GradientDrawable rounded(Context context,int fill,float radius){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context,radius));
        return shape;
    }

    static GradientDrawable outlined(Context context,int fill,int stroke,float radius){
        GradientDrawable shape=rounded(context,fill,radius);
        shape.setStroke(dp(context,1),stroke);
        return shape;
    }

    static Drawable interactive(Context context,int fill,int stroke,float radius,int ripple){
        GradientDrawable shape=outlined(context,fill,stroke,radius);
        return new RippleDrawable(ColorStateList.valueOf(ripple),shape,null);
    }

    static LinearLayout row(Context context){
        LinearLayout row=new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static LinearLayout column(Context context){
        LinearLayout column=new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    static LinearLayout.LayoutParams matchWrap(){
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weighted(float weight){
        return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,weight);
    }

    static LinearLayout.LayoutParams fixed(int width,int height){
        return new LinearLayout.LayoutParams(width,height);
    }

    static TextView compactButton(Context context,CharSequence value,int fill,int textColor){
        TextView button=text(context,value,14,textColor);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context,48));
        button.setPadding(dp(context,12),dp(context,11),dp(context,12),dp(context,11));
        button.setBackground(interactive(context,fill,0xffd2d7d9,10,0x22515b5f));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(value);
        return button;
    }

    static TextView darkLabel(Context context,CharSequence value,float size){
        TextView view=text(context,value,size,0xfff8faf8);
        return view;
    }

    static void setMargins(View view,int left,int top,int right,int bottom){
        ViewGroup.LayoutParams raw=view.getLayoutParams();
        if(raw instanceof ViewGroup.MarginLayoutParams){
            ViewGroup.MarginLayoutParams params=(ViewGroup.MarginLayoutParams)raw;
            params.setMargins(left,top,right,bottom);
            view.setLayoutParams(params);
        }
    }

    static void setClickableText(TextView view,View.OnClickListener click){
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(click);
    }
}
