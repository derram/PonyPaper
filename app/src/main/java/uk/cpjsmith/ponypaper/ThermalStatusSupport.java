package uk.cpjsmith.ponypaper;

import android.os.Handler;
import android.os.PowerManager;
import java.util.concurrent.Executor;

/**
 * API 29+ thermal status registration isolated from {@link PonySceneController}
 * so pre-Q devices never resolve {@link PowerManager.OnThermalStatusChangedListener}
 * when loading the main controller class.
 *
 * <p>Only call these methods when {@code Build.VERSION.SDK_INT >= Q}.
 */
final class ThermalStatusSupport {

    interface Callback {
        void onThermalStatusChanged(int status);
    }

    private ThermalStatusSupport() {}

    /**
     * Registers a thermal status listener on {@code pm}. The callback runs on
     * {@code handler}'s thread. Returns the listener token for {@link #unregister}.
     */
    static Object register(PowerManager pm, final Handler handler, final Callback callback) {
        PowerManager.OnThermalStatusChangedListener listener =
                new PowerManager.OnThermalStatusChangedListener() {
                    @Override
                    public void onThermalStatusChanged(int status) {
                        callback.onThermalStatusChanged(status);
                    }
                };
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
        pm.addThermalStatusListener(executor, listener);
        return listener;
    }

    static void unregister(PowerManager pm, Object listenerToken) {
        if (pm == null || listenerToken == null) return;
        pm.removeThermalStatusListener(
                (PowerManager.OnThermalStatusChangedListener) listenerToken);
    }
}
