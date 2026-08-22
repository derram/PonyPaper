package uk.cpjsmith.ponypaper;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Device-admin helper so the dream can lock the device after idle timeout.
 * Pixel/AOSP already sleeps when a timeout-started dream {@code finish()}es;
 * several OEMs (notably Samsung) return to keyguard instead.
 */
final class DreamSleepAdmin {

    private static final String TAG = "PonyPaper";

    private DreamSleepAdmin() {}

    static ComponentName component(Context context) {
        return new ComponentName(context, SleepAdminReceiver.class);
    }

    /**
     * Pixel/AOSP PowerManager sleeps after a timeout-started dream
     * {@code finish()}. Other OEMs typically do not.
     */
    static boolean systemSleepsAfterDreamFinish() {
        return matchesOem(Build.MANUFACTURER, "google")
                || matchesOem(Build.BRAND, "google");
    }

    /**
     * Whether idle timeout needs {@link DevicePolicyManager#lockNow()} to
     * actually power the panel off (non-Pixel OEMs).
     */
    static boolean needsLockToSleep() {
        return !systemSleepsAfterDreamFinish();
    }

    static boolean isActive(Context context) {
        DevicePolicyManager dpm = devicePolicyManager(context);
        return dpm != null && dpm.isAdminActive(component(context));
    }

    /**
     * Whether idle timeout can actually power the panel off: AOSP {@code finish()}
     * or an active device admin that can {@link DevicePolicyManager#lockNow()}.
     */
    static boolean canTurnScreenOff(Context context) {
        return systemSleepsAfterDreamFinish() || isActive(context);
    }

    /**
     * @return true if {@link DevicePolicyManager#lockNow()} was invoked
     */
    static boolean lockNow(Context context) {
        if (!isActive(context)) return false;
        DevicePolicyManager dpm = devicePolicyManager(context);
        if (dpm == null) return false;
        try {
            dpm.lockNow();
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "lockNow failed", e);
            return false;
        }
    }

    static Intent addAdminIntent(Context context) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context));
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.pref_dream_device_admin_explanation));
        return intent;
    }

    static void removeAdmin(Context context) {
        DevicePolicyManager dpm = devicePolicyManager(context);
        if (dpm == null) return;
        ComponentName admin = component(context);
        if (!dpm.isAdminActive(admin)) return;
        try {
            dpm.removeActiveAdmin(admin);
        } catch (SecurityException e) {
            Log.w(TAG, "removeActiveAdmin failed", e);
        }
    }

    private static DevicePolicyManager devicePolicyManager(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    private static boolean matchesOem(String value, String oem) {
        return value != null && value.equalsIgnoreCase(oem);
    }
}
