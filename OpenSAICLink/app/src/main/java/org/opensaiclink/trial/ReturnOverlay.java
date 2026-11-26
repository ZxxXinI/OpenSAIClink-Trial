package org.opensaiclink.trial;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

/** Bottom-right controls shown during projection. */
public final class ReturnOverlay {
    private static final int HOME_WIDTH = 68;
    private static final int BUTTON_HEIGHT = 36;
    private static final int BUTTON_TEXT_SIZE_SP = 16;
    private static final long IDLE_TIMEOUT_MS = 5000;
    private static final float IDLE_ALPHA = 0.20f;

    private final AccessibilityService service;
    private LinearLayout view;
    private Button home;
    private Button brightness;
    private final Runnable dimControls = () -> {
        if (view != null) view.setAlpha(IDLE_ALPHA);
    };

    public ReturnOverlay(AccessibilityService service) {
        this.service = service;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    public void setEnabled(boolean enabled, boolean showHome) {
        if (!enabled) {
            if (view != null) {
                LinearLayout controls = view;
                view = null;
                controls.removeCallbacks(dimControls);
                try {
                    service.getSystemService(WindowManager.class).removeViewImmediate(controls);
                } catch (RuntimeException e) {
                    Trace.log("RETURN_OVERLAY_REMOVE", e.toString());
                }
            }
            return;
        }

        if (view != null) {
            updateButtons(showHome);
            return;
        }

        LinearLayout controls = new LinearLayout(service) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                noteUserInteraction();
                return super.dispatchTouchEvent(event);
            }
        };
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        home = button(controls, R.string.return_overlay_button, HOME_WIDTH, 0, v -> openHome());
        home.setContentDescription(service.getString(R.string.car_return));
        brightness = button(controls, R.string.dim_brightness, 92, 4, v -> toggleBrightness());
        button(controls, R.string.rotate_screen, HOME_WIDTH * 2, 4, v -> rotateScreen());
        updateButtons(showHome);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        params.x = dp(10);
        params.y = dp(12);
        params.setTitle("OpenSAICLink projection controls");
        try {
            service.getSystemService(WindowManager.class).addView(controls, params);
            view = controls;
            noteUserInteraction();
            Trace.log("RETURN_OVERLAY_ON", "Visible projection controls");
        } catch (RuntimeException e) {
            Trace.log("RETURN_OVERLAY_ERROR", e.toString());
        }
    }

    void noteUserInteraction() {
        if (view == null) return;
        view.setAlpha(1f);
        view.removeCallbacks(dimControls);
        view.postDelayed(dimControls, IDLE_TIMEOUT_MS);
    }

    private void updateButtons(boolean showHome) {
        home.setVisibility(showHome ? View.VISIBLE : View.GONE);
        updateBrightnessLabel();
    }

    private void updateBrightnessLabel() {
        int label = ProjectionBrightness.isPending(service) ? R.string.restore_brightness : R.string.dim_brightness;
        String text = service.getString(label);
        if (!text.contentEquals(brightness.getText())) brightness.setText(text);
    }

    private Button button(LinearLayout parent, int label, int width, int margin, View.OnClickListener listener) {
        Button button = new Button(service);
        button.setText(label);
        button.setTextSize(BUTTON_TEXT_SIZE_SP);
        button.setTextColor(0xffffffff);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xe6226e74);
        background.setCornerRadius(dp(BUTTON_HEIGHT / 2));
        button.setBackground(background);
        button.setOnClickListener(v -> {
            noteUserInteraction();
            listener.onClick(v);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(width), dp(BUTTON_HEIGHT));
        params.leftMargin = dp(margin);
        parent.addView(button, params);
        return button;
    }

    private void openHome() {
        if (!Status.capturing || Status.carHomeVisible) return;
        RemoteTouchService.releaseCurrent();
        try {
            service.startActivity(CarHomeActivity.intent(service).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Trace.log("RETURN_OVERLAY_CLICK", "Opened car home");
        } catch (RuntimeException e) {
            Trace.log("RETURN_OVERLAY_ERROR", e.toString());
        }
    }

    private void toggleBrightness() {
        if (!Status.capturing) return;
        if (!Settings.System.canWrite(service)) {
            toast(R.string.display_settings_required);
            return;
        }
        boolean dimmed = ProjectionBrightness.isPending(service);
        boolean changed = dimmed
                ? ProjectionBrightness.restore(service)
                : ProjectionBrightness.apply(service, new SettingsStore(service).projectionBrightness());
        if (!changed) toast(dimmed ? R.string.brightness_restore_failed : R.string.brightness_dim_failed);
        updateBrightnessLabel();
    }

    private void rotateScreen() {
        if (!Status.capturing) return;
        if (!Settings.System.canWrite(service)) {
            toast(R.string.display_settings_required);
            return;
        }
        RemoteTouchService.releaseCurrent();
        if (!ProjectionRotation.toggle(service)) toast(R.string.rotation_failed);
    }

    private void toast(int message) {
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show();
    }
}
