package uk.cpjsmith.ponypaper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

/**
 * Trampoline that calls {@link android.app.admin.DevicePolicyManager#lockNow()}
 * from an Activity. Some OEMs ignore {@code lockNow} from a {@code TYPE_DREAM}
 * window; a 1×1 show-when-locked activity (without turning the screen on) is
 * enough to lock and power the panel off.
 *
 * <p>Unlike {@link UnlockRequestActivity}, this must not dismiss the keyguard
 * or turn the screen on.
 */
public class SleepRequestActivity extends Activity {

    static void launch(Context context) {
        if (!DreamSleepAdmin.isActive(context)) return;
        Intent intent = new Intent(context, SleepRequestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            context.startActivity(intent);
        } catch (RuntimeException e) {
            Log.w("PonyPaper", "SleepRequestActivity launch failed", e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyFloatingWindow();
        applyLockScreenFlags();
        suppressOpenTransition();

        View pixel = new View(this);
        pixel.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
        setContentView(pixel);

        DreamSleepAdmin.lockNow(this);
        finishQuietly();
    }

    private void applyFloatingWindow() {
        Window window = getWindow();
        if (window == null) return;
        window.setFormat(PixelFormat.TRANSLUCENT);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = 1;
        lp.height = 1;
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = 0;
        lp.dimAmount = 0f;
        window.setAttributes(lp);
        window.setLayout(1, 1);
    }

    /**
     * Sit over the lock screen if the dream has already ended, but never wake
     * the panel. {@code lockNow} is what turns it off.
     */
    private void applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(false);
        } else {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private void suppressOpenTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    private void finishQuietly() {
        if (isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }
}
