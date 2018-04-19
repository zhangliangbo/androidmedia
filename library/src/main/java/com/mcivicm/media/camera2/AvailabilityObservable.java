package com.mcivicm.media.camera2;

import android.hardware.camera2.CameraManager;
import android.support.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 摄像头的可用性
 */

public class AvailabilityObservable extends Observable<String> {

    private CameraManager cameraManager = null;

    public AvailabilityObservable(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    @Override
    protected void subscribeActual(Observer<? super String> observer) {
        observer.onSubscribe(new AvailabilityAdapter(observer));
    }

    private class AvailabilityAdapter extends CameraManager.AvailabilityCallback implements Disposable {

        private Observer<? super String> observer;

        private AtomicBoolean disposed = new AtomicBoolean(false);

        AvailabilityAdapter(Observer<? super String> observer) {
            this.observer = observer;
            if (cameraManager != null) {
                cameraManager.registerAvailabilityCallback(this, null);
            }
        }

        @Override
        public void onCameraAvailable(@NonNull String cameraId) {
            super.onCameraAvailable(cameraId);
            if (!isDisposed()) {
                observer.onNext(cameraId);
            }
        }

        @Override
        public void onCameraUnavailable(@NonNull String cameraId) {
            super.onCameraUnavailable(cameraId);
        }

        @Override
        public void dispose() {
            if (!disposed.get()) {
                cameraManager.unregisterAvailabilityCallback(this);
                disposed.set(true);
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
