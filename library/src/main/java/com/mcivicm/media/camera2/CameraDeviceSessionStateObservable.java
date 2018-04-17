package com.mcivicm.media.camera2;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.support.annotation.NonNull;
import android.view.Surface;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 对话的状态
 */

public class CameraDeviceSessionStateObservable extends Observable {

    @Override
    protected void subscribeActual(Observer observer) {

    }

    private class SessionStateAdapter extends CameraCaptureSession.StateCallback implements Disposable {

        private AtomicBoolean disposed = new AtomicBoolean(false);

        @Override
        public void onReady(@NonNull CameraCaptureSession session) {
            super.onReady(session);
        }

        @Override
        public void onActive(@NonNull CameraCaptureSession session) {
            super.onActive(session);
        }

        @Override
        public void onCaptureQueueEmpty(@NonNull CameraCaptureSession session) {
            super.onCaptureQueueEmpty(session);
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
        }

        @Override
        public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
            super.onSurfacePrepared(session, surface);
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {

        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }

        @Override
        public void dispose() {
            if (!disposed.get()) {
                disposed.set(true);
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }

}
