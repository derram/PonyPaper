package uk.cpjsmith.ponypaper;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Device-admin component used only for {@link android.app.admin.DevicePolicyManager#lockNow()}.
 * On OEMs where {@link android.service.dreams.DreamService#finish()} returns to keyguard
 * instead of sleeping, that call is what actually turns the panel off after idle timeout.
 */
public class SleepAdminReceiver extends DeviceAdminReceiver {

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return context.getString(R.string.pref_dream_device_admin_disable_message);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        PonySceneController.syncIdleTimeoutWithCapability(context);
    }
}
