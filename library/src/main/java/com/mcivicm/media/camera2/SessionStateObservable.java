package com.mcivicm.media.camera2;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Pair;
import android.view.Surface;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 对话的状态
 */

public class SessionStateObservable extends Observable<Pair<CameraCaptureSession, SessionState>> {

    private CameraDevice cameraDevice = null;
    private List<Surface> surfaceList = null;
    private Handler handler = null;

    public SessionStateObservable(CameraDevice cameraDevice, List<Surface> surfaceList, Handler handler) {
        this.cameraDevice = cameraDevice;
        this.surfaceList = surfaceList;
        this.handler = handler;
    }

    @Override
    protected void subscribeActual(Observer<? super Pair<CameraCaptureSession, SessionState>> observer) {
        observer.onSubscribe(new SessionStateAdapter(observer));
    }

    private class SessionStateAdapter extends CameraCaptureSession.StateCallback implements Disposable {

        private Observer<? super Pair<CameraCaptureSession, SessionState>> observer;

        private AtomicBoolean disposed = new AtomicBoolean(false);

        SessionStateAdapter(Observer<? super Pair<CameraCaptureSession, SessionState>> observer) {
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
            if(!isDisposed()){
                observer.onNext(Pair.create(session,SessionState.Ready));
            }
        }

        @Override
        public void onActive(@NonNull CameraCaptureSession session) {
            super.onActive(session);
            if(!isDisposed()){
                observer.onNext(Pair.create(session,SessionState.Active));
            }
        }

        @Override
        public void onCaptureQueueEmpty(@NonNull CameraCaptureSession session) {
            super.onCaptureQueueEmpty(session);
            if(!isDisposed()){
                observer.onNext(Pair.create(session,SessionState.CaptureQueueEmpty));
            }
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
            if (!isDisposed()) {
                observer.onNext(Pair.create(session, SessionState.Close));
            }
        }

        @Override
        public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
            if (!isDisposed()) {
                observer.onNext(Pair.create(session, SessionState.SurfacePrepared));
            }
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (!isDisposed()) {
                observer.onNext(Pair.create(session, SessionState.Configured));
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            if (!isDisposed()) {
                observer.onNext(Pair.create(session, SessionState.ConfiguredFailed));
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
