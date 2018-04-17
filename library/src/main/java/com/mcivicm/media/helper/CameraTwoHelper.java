package com.mcivicm.media.helper;

import android.hardware.camera2.CameraManager;
import android.support.annotation.NonNull;

/**
 * Created by bdlm2 on 2018/4/17.
 */

public class CameraTwoHelper {
    private class CameraDeviceAvailability extends CameraManager.AvailabilityCallback {

        @Override
        public void onCameraAvailable(@NonNull String cameraId) {
            super.onCameraAvailable(cameraId);
        }

        @Override
        public void onCameraUnavailable(@NonNull String cameraId) {
            super.onCameraUnavailable(cameraId);
        }
    }
}
