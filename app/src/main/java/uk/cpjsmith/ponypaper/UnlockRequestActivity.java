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
 * {@link KeyguardManager#requestDismissKeyguard} requires an {@link Activity}
 * that is either show-when-locked or would be visible if keyguard were not
 * hiding it. {@link PonyDreamService} cannot call it directly because the
 * framework's dream activity is not public API.
 *
 * <p>This trampoline deliberately does <em>not</em> use show-when-locked. The
 * dream calls {@link android.service.dreams.DreamService#wakeUp()} right after launching us, so we sit
 * behind keyguard as the top activity and still satisfy
 * {@code requestDismissKeyguard}. Marking a 1×1 floating window show-when-locked
 * occludes keyguard on Pixel and can leave the keyguard scrim stuck when the
 * dream starts again after cancel. {@link SleepRequestActivity} still needs
 * show-when-locked for its lock path.
 *
 * <p>If the keyguard is secure, this brings up the system unlock method (PIN,
 * pattern, password, or biometrics) instead of leaving the user on the lock
 * screen chrome that still needs a swipe. If the device is already unlocked or
 * the keyguard is not showing, the activity finishes immediately.
 *
 * <p>The window is floating and 1×1 px so exit is not treated as a full-screen
 * task wipe. {@link #finishQuietly()} and zero-duration theme animations further
 * suppress residual transitions after unlock.
 *
 * <p>After unlock the trampoline must leave immediately. A focused floating
 * window is touch-modal by default and will eat every pointer event outside its
 * 1×1 bounds — the home screen looks normal but is dead until Back finishes us.
 * That shows up most when a landscape dream rotates into the bouncer and the
 * dismiss callback is dropped ({@code Ignoring dismiss because we're already
 * going away}). {@link WindowManager.LayoutParams#FLAG_NOT_TOUCH_MODAL},
 * {@code configChanges}, and {@link #finishIfUnlocked()} cover that path.
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

    @Override
    protected void onResume() {
        super.onResume();
        // Callback may never fire if keyguard was already going away (common when
        // a landscape dream rotates into the bouncer). Leave as soon as unlocked.
        finishIfUnlocked();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            finishIfUnlocked();
        }
    }

    /**
     * Shrink to a floating 1×1 window so WindowManager close anims (if any) are
     * not a full-screen wipe. Theme already sets {@code windowIsFloating}.
     * {@link WindowManager.LayoutParams#FLAG_NOT_TOUCH_MODAL} lets touches outside
     * the pixel pass through — without it a stuck trampoline bricks the launcher.
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
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        window.setAttributes(lp);
        window.setLayout(1, 1);
    }

    /** Finish once the keyguard is gone; no-op while the bouncer is still up. */
    private void finishIfUnlocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard != null && !keyguard.isKeyguardLocked()) {
            finishQuietly();
        }
    }

    /**
     * Keep the panel awake for the bouncer, but do not occlude keyguard.
     * {@link #setShowWhenLocked(boolean)} stays false (see class docs). API 27+
     * prefers Activity setters; window flags cover older targets.
     */
    private void applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false);
            setTurnScreenOn(true);
        } else {
            applyLegacyLockScreenFlags();
        }
    }

    /** Pre-O_MR1: turn screen on only — no {@code FLAG_SHOW_WHEN_LOCKED}. */
    @SuppressWarnings("deprecation")
    private void applyLegacyLockScreenFlags() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    /**
     * Drop launch animation for this instance (theme covers most cases; this is belt-and-braces).
     */
    private void suppressOpenTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
        } else {
            legacyOverridePendingTransition();
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
            legacyOverridePendingTransition();
        }
    }

    /** Pre-34; {@link #overrideActivityTransition} replaces this. */
    @SuppressWarnings("deprecation")
    private void legacyOverridePendingTransition() {
        overridePendingTransition(0, 0);
    }
}
