package org.opensaiclink.trial;
import java.util.*;
public final class HomeLayout {
    public static final int ALL=5;
    public static final int[] EDITABLE={
        0,1,2,3,4,6,7,8
    }
    ;
    private final String[] cells;
    public HomeLayout(String[] values){
        if(values.length!=9)throw new IllegalArgumentException("Nine cells required");
        cells=values.clone();
        for(int i=0;i<9;i++){
            if(i==ALL){
                cells[i]="#all";
                continue;
            }
            if(cells[i]==null)cells[i]="";
            if(!cells[i].isEmpty() && !cells[i].matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))throw new IllegalArgumentException("Invalid package");
        }
    }
    public static HomeLayout defaults(){
        return new HomeLayout(new String[]{
            "com.autonavi.minimap","com.baidu.BaiduMap","","","","#all","","",""
        }
        );
    }
    public static HomeLayout migrate(Map<String,?> old){
        HomeLayout l=defaults();
        l=l.with(2,value(old,"car_shortcut_package"));
        for(int i=1;i<=3;i++)l=l.with(i+5,value(old,"car_shortcut_package_"+i));
        return l;
    }
    private static String value(Map<String,?> m,String k){
        Object o=m.get(k);
        return o instanceof String?(String)o:"";
    }
    public String get(int i){
        return cells[i];
    }
    public HomeLayout with(int cell,String pkg){
        if(cell<0 || cell>=9 || cell==ALL)throw new IllegalArgumentException("Fixed cell");
        String[] next=cells.clone();
        next[cell]=pkg;
        return new HomeLayout(next);
    }
    public HomeLayout move(int from,int to){
        if(from==ALL || to==ALL)throw new IllegalArgumentException("Fixed cell");
        int a=-1,b=-1;
        List<String> list=new ArrayList<>();
        for(int i=0;i<8;i++){
            list.add(cells[EDITABLE[i]]);
            if(EDITABLE[i]==from)a=i;
            if(EDITABLE[i]==to)b=i;
        }
        if(a<0 || b<0)throw new IllegalArgumentException("cell");
        list.add(b,list.remove(a));
        String[] next=cells.clone();
        for(int i=0;i<8;i++)next[EDITABLE[i]]=list.get(i);
        return new HomeLayout(next);
    }
    public String encode(){
        return String.join("|",cells);
    }
    public static HomeLayout decode(String s){
        return new HomeLayout(s.split("\\|",-1));
    }
    public static HomeLayout initial(int version,String saved,Map<String,?> old){
        return version>=1?decode(saved):migrate(old);
    }
    @Override public boolean equals(Object other){
        return other instanceof HomeLayout && Arrays.equals(cells,((HomeLayout)other).cells);
    }
    @Override public int hashCode(){
        return Arrays.hashCode(cells);
    }
}
