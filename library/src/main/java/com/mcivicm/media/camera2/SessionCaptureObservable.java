package com.mcivicm.media.camera2;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Pair;
import android.view.Surface;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 摄像头捕捉结果
 */

public class SessionCaptureObservable extends Observable<Pair<Integer, ? extends CameraMetadata>> {

    private CameraCaptureSession cameraCaptureSession = null;
    private int captureTemplate = 0;
    private RequestBuilderInitializer initializer;
    private Handler handler = null;

    public SessionCaptureObservable(CameraCaptureSession cameraCaptureSession, int captureTemplate, RequestBuilderInitializer initializer, Handler handler) {
        this.cameraCaptureSession = cameraCaptureSession;
        this.captureTemplate = captureTemplate;

        this.initializer = initializer;
        this.handler = handler;
    }

    @Override
    protected void subscribeActual(Observer<? super Pair<Integer, ? extends CameraMetadata>> observer) {
        observer.onSubscribe(new CaptureResultAdapter(observer));
    }


    /**
     * 初始化请求体，主要是给一些关键参数赋值
     */
    public interface RequestBuilderInitializer {
        void onCreateRequestBuilder(CaptureRequest.Builder builder);
    }

    private class CaptureResultAdapter extends CameraCaptureSession.CaptureCallback implements Disposable {

        private Observer<? super Pair<Integer, ? extends CameraMetadata>> observer;
        private AtomicBoolean disposed = new AtomicBoolean(false);

        CaptureResultAdapter(Observer<? super Pair<Integer, ? extends CameraMetadata>> observer) {
            this.observer = observer;
            if (cameraCaptureSession != null) {
                try {
                    switch (captureTemplate) {
                        case CameraDevice.TEMPLATE_PREVIEW:
                            CaptureRequest.Builder preview = cameraCaptureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            if (initializer != null) {
                                initializer.onCreateRequestBuilder(preview);
                            }
                            cameraCaptureSession.setRepeatingRequest(preview.build(), this, handler);//需要重复请求
                            break;
                        case CameraDevice.TEMPLATE_STILL_CAPTURE:
                            CaptureRequest.Builder still = cameraCaptureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                            if (initializer != null) {
                                initializer.onCreateRequestBuilder(still);
                            }
                            cameraCaptureSession.capture(still.build(), this, handler);
                            break;
                        case CameraDevice.TEMPLATE_RECORD:
                            CaptureRequest.Builder record = cameraCaptureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                            if (initializer != null) {
                                initializer.onCreateRequestBuilder(record);
                            }
                            cameraCaptureSession.setRepeatingRequest(record.build(), this, handler);//需要重复请求
                            break;
                    }
                } catch (Exception e) {
                    observer.onError(e);
                }
            }
        }

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            if (!isDisposed()) {
                observer.onNext(Pair.create(captureTemplate, partialResult));
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (!isDisposed()) {
                observer.onNext(Pair.create(captureTemplate, result));
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            if (!isDisposed()) {
                observer.onError(new Exception(failure.getReason() == CaptureFailure.REASON_ERROR ? "捕获出错" : "捕获取消"));
            }
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
        }

        @Override
        public void dispose() {
            if (!isDisposed()) {
                disposed.set(true);
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
