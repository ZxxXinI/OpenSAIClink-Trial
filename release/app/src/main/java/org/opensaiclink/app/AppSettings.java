package org.opensaiclink.app;

import android.content.Context;

/** Preferences owned exclusively by the installed production package. */
final class AppSettings {
    static final String FILE="open_saiclink";
    static final String TOUCH="touch";
    static final String MULTITOUCH="multitouch";
    static final String LANDSCAPE="landscape";
    static final String RETURN_OVERLAY="return_overlay";
    static final String FILL_DISPLAY="fill_display";
    static final String RESPONSIVE="responsive";
    static final String BLUETOOTH_TIP="bluetooth_tip_dismissed";
    static final String RESTORE_AUTO="restore_auto";
    static final String RESTORE_ROTATION="restore_rotation";
    static final String RESTORE_PENDING="restore_pending";
    static final String SHORTCUT_PREFIX="car_shortcut_package";

    private AppSettings(){}

    static String shortcutKey(int slot){
        if(slot<0 || slot>3)throw new IllegalArgumentException("slot");
        return slot==0?SHORTCUT_PREFIX:SHORTCUT_PREFIX+"_"+slot;
    }
}
