package com.mcivicm.media;

import android.app.Service;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
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

import com.mcivicm.media.camera2.CameraDeviceAvailabilityObservable;
import com.mcivicm.media.camera2.CameraDeviceSessionStateObservable;
import com.mcivicm.media.camera2.CameraDeviceStateObservable;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;

/**
 * Camera2 Api
 */

public class CameraTwoActivity extends AppCompatActivity {

    CameraManager cameraManager = null;
    Handler handler = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initHandler();
        setContentView(R.layout.activity_camera_two);
        cameraManager = (CameraManager) getSystemService(Service.CAMERA_SERVICE);
        SurfaceView surfaceView = findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(new Callback());
    }

    private class Callback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            new CameraDeviceAvailabilityObservable(cameraManager)
                    .flatMap(new Function<String, ObservableSource<CameraDevice>>() {
                        @Override
                        public ObservableSource<CameraDevice> apply(String s) throws Exception {
                            return new CameraDeviceStateObservable(cameraManager, s, handler);
                        }
                    })
                    .flatMap(new Function<CameraDevice, ObservableSource<CameraCaptureSession>>() {
                        @Override
                        public ObservableSource<CameraCaptureSession> apply(CameraDevice cameraDevice) throws Exception {
                            return new CameraDeviceSessionStateObservable(cameraDevice, toList(holder.getSurface()), handler);
                        }
                    });
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    }

    private void log(String s) {
        Log.d("zhang", s);
    }

    private List<Surface> toList(Surface surface) {
        List<Surface> list = new ArrayList<>();
        list.add(surface);
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
