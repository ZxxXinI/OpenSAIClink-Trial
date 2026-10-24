package org.opensaiclink.trial;
/** Host-only stub of the Android UI key helper. */
public final class CarHomeActivity {
    private CarHomeActivity(){}
    public static String shortcutKey(int slot){
        if(slot<0 || slot>3)throw new IllegalArgumentException("slot");
        return slot==0?"car_shortcut_package":"car_shortcut_package_"+slot;
    }
}
