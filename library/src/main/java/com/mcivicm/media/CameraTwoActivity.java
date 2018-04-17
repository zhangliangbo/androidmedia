package com.mcivicm.media;

import android.app.Service;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;

import com.mcivicm.media.camera2.CameraDeviceAvailabilityObservable;
import com.mcivicm.media.camera2.CameraDeviceSessionCaptureObservable;
import com.mcivicm.media.camera2.CameraDeviceSessionStateObservable;
import com.mcivicm.media.camera2.CameraDeviceStateObservable;
import com.mcivicm.media.helper.CameraOneHelper;
import com.mcivicm.media.helper.ToastHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;

/**
 * Camera2 Api
 */

public class CameraTwoActivity extends AppCompatActivity {

    private CameraManager cameraManager = null;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private Handler handler = null;
    private Disposable disposable;
    private ImageReader imageReader;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initHandler();
        setContentView(R.layout.activity_camera_two);
        cameraManager = (CameraManager) getSystemService(Service.CAMERA_SERVICE);
        final SurfaceView surfaceView = findViewById(R.id.surface_view);
        surfaceView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (imageReader == null) {//初始化ImageReader
                    imageReader = ImageReader.newInstance(surfaceView.getWidth(), surfaceView.getHeight(), ImageFormat.JPEG, 1);
                    imageReader.setOnImageAvailableListener(new ImageAvailable(), handler);
                }
                surfaceView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        surfaceView.getHolder().addCallback(new Callback());
        findViewById(R.id.record_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    cameraCaptureSession.stopRepeating();
                    new CameraDeviceSessionCaptureObservable(cameraCaptureSession, CameraDevice.TEMPLATE_STILL_CAPTURE, toList(imageReader.getSurface()), handler)
                            .subscribe(new Observer<CameraMetadata>() {
                                @Override
                                public void onSubscribe(Disposable d) {

                                }

                                @Override
                                public void onNext(CameraMetadata cameraMetadata) {

                                }

                                @Override
                                public void onError(Throwable e) {

                                }

                                @Override
                                public void onComplete() {

                                }
                            });
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void startPreview(final Surface surface) {
        CameraOneHelper.cameraPermission(CameraTwoActivity.this)
                .flatMap(new Function<Boolean, ObservableSource<String>>() {
                    @Override
                    public ObservableSource<String> apply(Boolean aBoolean) throws Exception {
                        return new CameraDeviceAvailabilityObservable(cameraManager);
                    }
                })
                .flatMap(new Function<String, ObservableSource<CameraDevice>>() {
                    @Override
                    public ObservableSource<CameraDevice> apply(String s) throws Exception {
                        return new CameraDeviceStateObservable(cameraManager, s, handler);
                    }
                })
                .flatMap(new Function<CameraDevice, ObservableSource<CameraCaptureSession>>() {
                    @Override
                    public ObservableSource<CameraCaptureSession> apply(CameraDevice cameraDevice) throws Exception {
                        CameraTwoActivity.this.cameraDevice = cameraDevice;
                        return new CameraDeviceSessionStateObservable(cameraDevice, toList(surface, imageReader.getSurface()), handler);
                    }
                })
                .flatMap(new Function<CameraCaptureSession, ObservableSource<CameraMetadata>>() {
                    @Override
                    public ObservableSource<CameraMetadata> apply(CameraCaptureSession cameraCaptureSession) throws Exception {
                        CameraTwoActivity.this.cameraCaptureSession = cameraCaptureSession;
                        return new CameraDeviceSessionCaptureObservable(cameraCaptureSession, CameraDevice.TEMPLATE_PREVIEW, toList(surface), handler);
                    }
                })
                .subscribe(new Observer<CameraMetadata>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(CameraMetadata cameraMetadata) {

                    }

                    @Override
                    public void onError(Throwable e) {
                        ToastHelper.toast(CameraTwoActivity.this, e.getMessage());
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private class ImageAvailable implements ImageReader.OnImageAvailableListener {

        @Override
        public void onImageAvailable(ImageReader reader) {
            log("new image: ");
            Image image = reader.acquireNextImage();
        }

    }

    private class Callback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            startPreview(holder.getSurface());
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
            }
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        }
    }

    private void log(String s) {
        Log.d("zhang", s);
    }

    private List<Surface> toList(Surface... surfaces) {
        List<Surface> list = new ArrayList<>();
        if (surfaces != null && surfaces.length > 0) {
            Collections.addAll(list, surfaces);
        }
        return list;
    }

    private void initHandler() {
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                return false;
            }
        });
    }
}
