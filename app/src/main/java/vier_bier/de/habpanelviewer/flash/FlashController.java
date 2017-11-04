package vier_bier.de.habpanelviewer.flash;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import vier_bier.de.habpanelviewer.CameraException;
import vier_bier.de.habpanelviewer.StateListener;
import vier_bier.de.habpanelviewer.status.ApplicationStatus;

/**
 * Controller for the back-facing cameras flash light.
 */
@TargetApi(Build.VERSION_CODES.M)
public class FlashController implements StateListener {
    private FlashControlThread controller;
    private CameraManager camManager;
    private String torchId;

    private boolean enabled;
    private String flashItemName;
    private String flashItemState;

    private Pattern flashOnPattern;
    private Pattern flashPulsatingPattern;

    private ApplicationStatus mStatus;

    public FlashController(CameraManager cameraManager) throws CameraException {
        camManager = cameraManager;
        EventBus.getDefault().register(this);

        try {
            for (String camId : camManager.getCameraIdList()) {
                CameraCharacteristics characteristics = camManager.getCameraCharacteristics(camId);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing == CameraCharacteristics.LENS_FACING_BACK && hasFlash) {
                    torchId = camId;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            throw new CameraException(e);
        }

        if (torchId == null) {
            throw new CameraException("Could not find back facing camera with flash!");
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;
        addStatusItems();
    }

    private void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (isEnabled()) {
            mStatus.set("Flash Control", "enabled\n" + flashItemName + "=" + flashItemState);
        } else {
            mStatus.set("Flash Control", "disabled");
        }
    }

    private boolean isEnabled() {
        return enabled;
    }

    public void terminate() {
        if (controller != null) {
            controller.terminate();
            controller = null;
        }
    }

    private FlashControlThread createController() {
        if (controller == null) {
            controller = new FlashControlThread();
            controller.start();
        }

        return controller;
    }

    @Override
    public void updateState(String name, String state) {
        if (name.equals(flashItemName)) {
            if (flashItemState != null && flashItemState.equals(state)) {
                Log.i("Habpanelview", "unchanged flash item state=" + state);
                return;
            }

            Log.i("Habpanelview", "flash item state=" + state + ", old state=" + flashItemState);
            flashItemState = state;
            addStatusItems();

            if (flashOnPattern != null && state != null && flashOnPattern.matcher(state).matches()) {
                createController().enableFlash();
            } else if (flashPulsatingPattern != null && state != null && flashPulsatingPattern.matcher(state).matches()) {
                createController().pulseFlash();
            } else {
                if (controller != null) {
                    controller.disableFlash();
                }
            }
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        flashPulsatingPattern = null;
        flashOnPattern = null;
        if (flashItemName == null || !flashItemName.equalsIgnoreCase(prefs.getString("pref_flash_item", ""))) {
            flashItemName = prefs.getString("pref_flash_item", "");
            flashItemState = null;
        }
        enabled = prefs.getBoolean("pref_flash_enabled", false);

        String pulsatingRegexpStr = prefs.getString("pref_flash_pulse_regex", "");
        if (!pulsatingRegexpStr.isEmpty()) {
            try {
                flashPulsatingPattern = Pattern.compile(pulsatingRegexpStr);
            } catch (PatternSyntaxException e) {
                // is handled in the preferences
            }
        }

        String steadyRegexpStr = prefs.getString("pref_flash_steady_regex", "");
        if (!steadyRegexpStr.isEmpty()) {
            try {
                flashOnPattern = Pattern.compile(steadyRegexpStr);
            } catch (PatternSyntaxException e) {
                // is handled in the preferences
            }
        }

        addStatusItems();
    }

    private class FlashControlThread extends Thread {
        private final AtomicBoolean fRunning = new AtomicBoolean(true);
        private AtomicBoolean fPulsing = new AtomicBoolean(false);
        private AtomicBoolean fOn = new AtomicBoolean(false);

        private boolean fFlashOn = false;

        private FlashControlThread() {
            super("FlashControlThread");
            setDaemon(true);
        }

        private void terminate() {
            fRunning.set(false);
            try {
                join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void run() {
            Log.d("Habpanelview", "FlashControlThread started");

            while (fRunning.get()) {
                synchronized (fRunning) {
                    setFlash(fOn.get() || fPulsing.get() && !fFlashOn);

                    try {
                        fRunning.wait(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            setFlash(false);
            Log.d("Habpanelview", "FlashControlThread finished");
        }

        private void pulseFlash() {
            Log.d("Habpanelview", "pulseFlash");

            synchronized (fRunning) {
                fPulsing.set(true);
                fOn.set(false);

                fRunning.notifyAll();
            }
        }

        private void disableFlash() {
            Log.d("Habpanelview", "disableFlash");

            synchronized (fRunning) {
                fPulsing.set(false);
                fOn.set(false);

                fRunning.notifyAll();
            }
        }

        private void enableFlash() {
            Log.d("Habpanelview", "disableFlash");

            synchronized (fRunning) {
                fPulsing.set(false);
                fOn.set(true);

                fRunning.notifyAll();
            }
        }

        private void setFlash(boolean flashing) {
            if (flashing != fFlashOn) {
                fFlashOn = flashing;

                try {
                    if (torchId != null) {
                        camManager.setTorchMode(torchId, flashing);
                        Log.d("Habpanelview", "Set torchmode " + flashing);
                    }
                } catch (CameraAccessException e) {
                    if (e.getReason() != CameraAccessException.MAX_CAMERAS_IN_USE) {
                        Log.e("Habpanelview", "Failed to toggle flash!", e);
                    }
                }
            }
        }
    }
}