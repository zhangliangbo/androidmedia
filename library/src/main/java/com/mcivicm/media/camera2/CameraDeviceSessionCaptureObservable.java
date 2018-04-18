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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 摄像头捕捉结果
 */

public class CameraDeviceSessionCaptureObservable extends Observable<Pair<Integer, ? extends CameraMetadata>> {

    private CameraCaptureSession cameraCaptureSession = null;
    private int captureTemplate = 0;
    private List<Surface> surfaceList;
    private Handler handler = null;

    public CameraDeviceSessionCaptureObservable(CameraCaptureSession cameraCaptureSession, int captureTemplate, List<Surface> surfaceList, Handler handler) {
        this.cameraCaptureSession = cameraCaptureSession;
        this.captureTemplate = captureTemplate;
        if (surfaceList == null) {
            this.surfaceList = new ArrayList<>();
        } else {
            this.surfaceList = surfaceList;
        }
        this.handler = handler;
    }

    @Override
    protected void subscribeActual(Observer<? super Pair<Integer, ? extends CameraMetadata>> observer) {
        observer.onSubscribe(new CaptureResultAdapter(observer));
    }

    private class CaptureResultAdapter extends CameraCaptureSession.CaptureCallback implements Disposable {

        private Observer<? super Pair<Integer, ? extends CameraMetadata>> observer;
        private AtomicBoolean disposed = new AtomicBoolean(false);

        CaptureResultAdapter(Observer<? super Pair<Integer, ? extends CameraMetadata>> observer) {
            this.observer = observer;
            if (cameraCaptureSession != null) {
                switch (captureTemplate) {
                    case CameraDevice.TEMPLATE_PREVIEW:
                        try {
                            CaptureRequest.Builder preview = cameraCaptureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            for (Surface surface : surfaceList) {
                                preview.addTarget(surface);
                            }
                            cameraCaptureSession.setRepeatingRequest(preview.build(), this, handler);
                        } catch (Exception e) {
                            observer.onError(e);
                        }
                        break;
                    case CameraDevice.TEMPLATE_STILL_CAPTURE:
                        try {
                            CaptureRequest.Builder still = cameraCaptureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                            for (Surface surface : surfaceList) {
                                still.addTarget(surface);
                            }
                            cameraCaptureSession.capture(still.build(), this, handler);
                        } catch (Exception e) {
                            observer.onError(e);
                        }
                        break;
                    case CameraDevice.TEMPLATE_RECORD:
                        try {
                            CaptureRequest.Builder record = cameraCaptureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                            for (Surface surface : surfaceList) {
                                record.addTarget(surface);
                            }
                            cameraCaptureSession.setRepeatingRequest(record.build(), this, handler);
                        } catch (Exception e) {
                            observer.onError(e);
                        }
                        break;
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
                try {
                    cameraCaptureSession.abortCaptures();
                } catch (CameraAccessException e) {
                    //ignore
                }
                disposed.set(true);
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
