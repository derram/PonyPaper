package uk.cpjsmith.ponypaper;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

/**
 * Thin trampoline launched when the user exits the dream and wants to unlock.
 * {@link KeyguardManager#requestDismissKeyguard} requires a visible (or
 * show-when-locked) {@link Activity}; {@link PonyDreamService} cannot call it
 * directly because the framework's dream activity is not public API.
 *
 * <p>If the keyguard is secure, this brings up the system unlock method (PIN,
 * pattern, password, or biometrics) instead of leaving the user on the lock
 * screen chrome that still needs a swipe. If the device is already unlocked or
 * the keyguard is not showing, the activity finishes immediately.
 *
 * <p>The window is floating and 1×1 px so exit is not treated as a full-screen
 * task wipe. {@link #finishQuietly()} and zero-duration theme animations further
 * suppress residual transitions after unlock.
 */
public class UnlockRequestActivity extends Activity {

    /**
     * Starts this trampoline from a non-activity context (e.g. the dream).
     * Safe to call when already unlocked — the activity exits without UI.
     */
    static void launch(Context context) {
        Intent intent = new Intent(context, UnlockRequestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyFloatingWindow();
        applyLockScreenFlags();
        suppressOpenTransition();

        // Tiny content so WRAP_CONTENT floating layout stays 1×1, not empty full-screen.
        View pixel = new View(this);
        pixel.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
        setContentView(pixel);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // requestDismissKeyguard is API 26+; older builds only get wakeUp from the dream.
            finishQuietly();
            return;
        }

        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard == null || !keyguard.isKeyguardLocked()) {
            finishQuietly();
            return;
        }

        keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override
            public void onDismissSucceeded() {
                finishQuietly();
            }

            @Override
            public void onDismissCancelled() {
                finishQuietly();
            }

            @Override
            public void onDismissError() {
                finishQuietly();
            }
        });
    }

    /**
     * Shrink to a floating 1×1 window so WindowManager close anims (if any) are
     * not a full-screen wipe. Theme already sets {@code windowIsFloating}.
     */
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
     * Ensure we can sit over the lock screen long enough for the bouncer request.
     * API 27+ prefers Activity setters; window flags cover older targets.
     */
    private void applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    /**
     * Drop launch animation for this instance (theme covers most cases; this is belt-and-braces).
     */
    private void suppressOpenTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    /**
     * End the trampoline without the default translucent task close wipe.
     * {@link #finishAndRemoveTask()} matches {@link Intent#FLAG_ACTIVITY_NEW_TASK}
     * launch; zero transitions suppress residual activity/task anims.
     */
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
