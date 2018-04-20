package com.mcivicm.media;

import android.app.Service;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;

import com.mcivicm.media.camera2.AvailabilityObservable;
import com.mcivicm.media.camera2.SessionCaptureObservable;
import com.mcivicm.media.camera2.SessionStateObservable;
import com.mcivicm.media.camera2.StateObservable;
import com.mcivicm.media.helper.CameraOneHelper;
import com.mcivicm.media.helper.CameraTwoHelper;
import com.mcivicm.media.helper.ToastHelper;
import com.mcivicm.media.view.VolumeView;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

/**
 * 录制视频
 */

public class RecordVideoActivity extends AppCompatActivity {

    private SurfaceView surfaceView;
    private VolumeView volumeView;
    private AppCompatTextView recordVideo;

    private SurfaceHolder surfaceHolder;
    private Surface mediaRecordSurface;
    final MediaRecorder mediaRecorder = new MediaRecorder();

    private Handler nonMainHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;

    private Disposable recordDisposable;
    private boolean resetQ = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initHandler();
        initCameraDevice();
        setContentView(R.layout.activity_record_video);
        surfaceView = findViewById(R.id.surface_view);
        surfaceView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                CameraTwoHelper.configureVideoMediaRecorder(mediaRecorder, surfaceView.getWidth(), surfaceView.getHeight());
                try {
                    mediaRecorder.prepare();
                    mediaRecordSurface = mediaRecorder.getSurface();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                surfaceView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        volumeView = findViewById(R.id.record_button_layout);
        recordVideo = findViewById(R.id.record_button);

        surfaceView.getHolder().addCallback(new SurfaceCallback());
        recordVideo.setOnTouchListener(new TouchListener());
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void log(String s) {
        Log.d("zhang", s);
    }

    private CameraManager manager() {
        return (CameraManager) getSystemService(Service.CAMERA_SERVICE);
    }

    private Observable<String> availability() {
        return CameraOneHelper.cameraPermission(RecordVideoActivity.this)
                .flatMap(new Function<Boolean, ObservableSource<String>>() {
                    @Override
                    public ObservableSource<String> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            return new AvailabilityObservable(manager());
                        } else {
                            return Observable.empty();
                        }
                    }
                });
    }

    private Observable<CameraDevice> cameraDevice() {
        return availability()
                .flatMap(new Function<String, ObservableSource<CameraDevice>>() {
                    @Override
                    public ObservableSource<CameraDevice> apply(String s) throws Exception {
                        return new StateObservable(manager(), s, nonMainHandler);
                    }
                });
    }

    private void initHandler() {
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        nonMainHandler = new Handler(handlerThread.getLooper());
    }

    private void initCameraDevice() {
        cameraDevice().subscribe(new Observer<CameraDevice>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(CameraDevice cameraDevice) {
                RecordVideoActivity.this.cameraDevice = cameraDevice;
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    private Observable<CameraCaptureSession> cameraCaptureSession(List<Surface> list) {
        if (cameraCaptureSession == null) {
            return new SessionStateObservable(cameraDevice, list, nonMainHandler)
                    .doOnNext(new Consumer<CameraCaptureSession>() {
                        @Override
                        public void accept(CameraCaptureSession cameraCaptureSession) throws Exception {
                            RecordVideoActivity.this.cameraCaptureSession = cameraCaptureSession;
                        }
                    });
        } else {
            return Observable.just(cameraCaptureSession);
        }
    }


    private void startPreview(List<Surface> session, final List<Surface> preview) {
        cameraCaptureSession(session)
                .flatMap(new Function<CameraCaptureSession, ObservableSource<Pair<Integer, ? extends CameraMetadata>>>() {
                    @Override
                    public ObservableSource<Pair<Integer, ? extends CameraMetadata>> apply(CameraCaptureSession cameraCaptureSession) throws Exception {
                        return new SessionCaptureObservable(
                                cameraCaptureSession,
                                CameraDevice.TEMPLATE_PREVIEW,
                                new SessionCaptureObservable.RequestBuilderInitializer() {
                                    @Override
                                    public void onCreateRequestBuilder(CaptureRequest.Builder builder) {
                                        for (Surface surface : preview) {
                                            builder.addTarget(surface);
                                        }
                                    }
                                },
                                nonMainHandler);
                    }
                })
                .subscribe(new io.reactivex.Observer<Pair<Integer, ? extends CameraMetadata>>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Pair<Integer, ? extends CameraMetadata> integerPair) {
                        Log.d("zhang", "previewing.");
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void startRecord(List<Surface> session, final List<Surface> record) {
        cameraCaptureSession(session)
                .flatMap(new Function<CameraCaptureSession, ObservableSource<Pair<Integer, ? extends CameraMetadata>>>() {
                    @Override
                    public ObservableSource<Pair<Integer, ? extends CameraMetadata>> apply(CameraCaptureSession cameraCaptureSession) throws Exception {
                        return new SessionCaptureObservable(
                                cameraCaptureSession,
                                CameraDevice.TEMPLATE_RECORD,
                                new SessionCaptureObservable.RequestBuilderInitializer() {
                                    @Override
                                    public void onCreateRequestBuilder(CaptureRequest.Builder builder) {
                                        for (Surface surface : record) {
                                            builder.addTarget(surface);
                                        }
                                    }
                                }, nonMainHandler);
                    }
                })
                .subscribe(new io.reactivex.Observer<Pair<Integer, ? extends CameraMetadata>>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Pair<Integer, ? extends CameraMetadata> integerPair) {
                        Log.d("zhang", "recording.");
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void recordVideo() {

        Observable.intervalRange(0, 10020, 0, 1, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnDispose(new Action() {
                    @Override
                    public void run() throws Exception {
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        ToastHelper.toast(RecordVideoActivity.this, "录制完成");
                        try {
                            mediaRecorder.stop();
                            mediaRecorder.reset();
                            resetQ = true;
                        } catch (Exception e) {

                        }
                        //继续预览
                        startPreview(Arrays.asList(surfaceHolder.getSurface(), mediaRecordSurface), Arrays.asList(surfaceHolder.getSurface()));
                    }
                })
                .subscribe(new io.reactivex.Observer<Long>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        recordDisposable = d;

                        volumeView.showEdge();
                        volumeView.setOrientation(0);
                        //开始录制视频
                        if (resetQ) {
                            try {
                                CameraTwoHelper.configureVideoMediaRecorder(mediaRecorder, surfaceView.getWidth(), surfaceView.getHeight());
                                mediaRecorder.prepare();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        startRecord(Arrays.asList(surfaceHolder.getSurface(), mediaRecordSurface), Arrays.asList(surfaceHolder.getSurface(), mediaRecordSurface));
                        mediaRecorder.start();

                    }

                    @Override
                    public void onNext(Long aLong) {
                        volumeView.setOrientation((int) (360 * aLong.floatValue() / 10000));
                    }

                    @Override
                    public void onError(Throwable e) {
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        ToastHelper.toast(RecordVideoActivity.this, "录制失败");
                        try {
                            mediaRecorder.stop();
                            mediaRecorder.reset();
                            resetQ = true;
                        } catch (Exception ex) {

                        }
                        //继续预览
                        startPreview(Arrays.asList(surfaceHolder.getSurface(), mediaRecordSurface), Arrays.asList(surfaceHolder.getSurface()));
                    }

                    @Override
                    public void onComplete() {
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        ToastHelper.toast(RecordVideoActivity.this, "录制完成");
                        try {
                            mediaRecorder.stop();
                            mediaRecorder.reset();
                            resetQ = true;
                        } catch (Exception e) {

                        }
                        //继续预览
                        startPreview(Arrays.asList(surfaceHolder.getSurface(), mediaRecordSurface), Arrays.asList(surfaceHolder.getSurface()));
                    }
                });
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            RecordVideoActivity.this.surfaceHolder = holder;
            startPreview(Arrays.asList(surfaceHolder.getSurface(), mediaRecordSurface), Arrays.asList(surfaceHolder.getSurface()));
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            RecordVideoActivity.this.surfaceHolder = holder;

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            RecordVideoActivity.this.surfaceHolder = holder;
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        }
    }

    private class TouchListener implements View.OnTouchListener {

        GestureDetector gestureDetector = new GestureDetector(RecordVideoActivity.this, new GestureListener());

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            gestureDetector.onTouchEvent(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    if (recordDisposable != null && !recordDisposable.isDisposed()) {
                        recordDisposable.dispose();
                    }
                    break;
            }
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            manager().registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(@NonNull String cameraId) {
                    super.onCameraAvailable(cameraId);
                    Log.d("zhang", "available 2: " + cameraId);
                }

                @Override
                public void onCameraUnavailable(@NonNull String cameraId) {
                    super.onCameraUnavailable(cameraId);
                }
            }, nonMainHandler);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            recordVideo();
        }
    }

    private class State extends CameraDevice.StateCallback {

        CameraDevice cameraDevice = null;

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice = camera;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice = camera;
        }
    }
}
