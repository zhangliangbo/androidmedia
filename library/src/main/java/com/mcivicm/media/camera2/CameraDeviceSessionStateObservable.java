package com.mcivicm.media.camera2;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.Surface;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 对话的状态
 */

public class CameraDeviceSessionStateObservable extends Observable<CameraCaptureSession> {

    private CameraDevice cameraDevice = null;
    private List<Surface> surfaceList = null;
    private Handler handler = null;

    public CameraDeviceSessionStateObservable(CameraDevice cameraDevice, List<Surface> surfaceList, Handler handler) {
        this.cameraDevice = cameraDevice;
        this.surfaceList = surfaceList;
        this.handler = handler;
    }

    @Override
    protected void subscribeActual(Observer<? super CameraCaptureSession> observer) {
        observer.onSubscribe(new SessionStateAdapter(observer));
    }

    private class SessionStateAdapter extends CameraCaptureSession.StateCallback implements Disposable {

        private Observer<? super CameraCaptureSession> observer;

        private AtomicBoolean disposed = new AtomicBoolean(false);

        SessionStateAdapter(Observer<? super CameraCaptureSession> observer) {
            this.observer = observer;
            if (cameraDevice != null && surfaceList != null) {
                try {
                    cameraDevice.createCaptureSession(surfaceList, this, handler);
                } catch (CameraAccessException e) {
                    observer.onError(e);
                }
            }
        }

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
            if (!isDisposed()) {
                observer.onComplete();
            }
        }

        @Override
        public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
            super.onSurfacePrepared(session, surface);
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (!isDisposed()) {
                observer.onNext(session);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            if (!isDisposed()) {
                observer.onError(new Exception("打开摄像头会话失败"));
            }
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
