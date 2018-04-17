package com.mcivicm.media.camera2;

import android.annotation.SuppressLint;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.support.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 摄像头状态
 */

public class CameraDeviceStateObservable extends Observable<CameraDevice> {

    private CameraManager cameraManager = null;
    private String cameraId = null;
    private Handler handler = null;


    public CameraDeviceStateObservable(CameraManager cameraManager, String cameraId, Handler handler) {
        this.cameraManager = cameraManager;
        this.cameraId = cameraId;
        this.handler = handler;
    }

    @Override
    protected void subscribeActual(Observer<? super CameraDevice> observer) {
        observer.onSubscribe(new StateAdapter(observer));
    }

    private class StateAdapter extends CameraDevice.StateCallback implements Disposable {

        Observer<? super CameraDevice> observer;

        AtomicBoolean isDisposed = new AtomicBoolean(false);

        @SuppressLint("MissingPermission")
        StateAdapter(Observer<? super CameraDevice> observer) {
            this.observer = observer;
            if (cameraManager != null) {
                try {
                    cameraManager.openCamera(cameraId, this, handler);
                } catch (CameraAccessException e) {
                    observer.onError(e);
                }
            }
        }

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            if (!isDisposed()) {
                observer.onNext(camera);
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            //失去连接后关闭摄像头，如需使用重新开启
            camera.close();
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            super.onClosed(camera);
            if (!isDisposed()) {
                observer.onComplete();//相机使用结束
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            String errorString = "打开摄像头错误";
            switch (error) {
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    errorString = "摄像头正在使用中";
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    errorString = "已达到摄像头的最大使用数量";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    errorString = "摄像头无法使用";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    errorString = "摄像头发生严重错误";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    errorString = "摄像头服务无法打开";
                    break;
            }
            if (!isDisposed()) {
                observer.onError(new Exception(errorString));
            }
        }

        @Override
        public void dispose() {
            if (!isDisposed.get()) {
                isDisposed.set(true);
            }
        }

        @Override
        public boolean isDisposed() {
            return isDisposed.get();
        }
    }
}
