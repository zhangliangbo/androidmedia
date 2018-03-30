package com.mcivicm.audiosample;

import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * camera1
 */

public class CameraOneActivity extends AppCompatActivity {

    Button start;
    SurfaceView surfaceView;
    TextView information;

    Camera camera;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_one);
        start = findViewById(R.id.start);
        surfaceView = findViewById(R.id.surface_view);
        information = findViewById(R.id.information);
    }

    @Override
    protected void onStart() {
        super.onStart();
        information.setText(String.valueOf("摄像头总数：" + CameraHelper.cameraNumber()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        int total = CameraHelper.cameraNumber();
        if (total > 0) {
            if (total == 1) {
                camera = CameraHelper.open(0);
                camera.setDisplayOrientation(90);
                surfaceView.getHolder().addCallback(new Callback(camera));
            } else {
                for (int i = 0; i < total; i++) {
                    if (CameraHelper.getInfo(i).facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        camera = CameraHelper.open(i);
                        camera.setDisplayOrientation(90);
                        surfaceView.getHolder().addCallback(new Callback(camera));
                    }
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private class Callback implements SurfaceHolder.Callback {

        private Camera camera;

        Callback(Camera camera) {
            this.camera = camera;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                camera.setPreviewDisplay(holder);
            } catch (Exception e) {
                Toast
                        .makeText(
                                CameraOneActivity.this,
                                "设置图像预览失败",
                                Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            camera.startPreview();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (holder.getSurface() == null) return;
            try {
                camera.stopPreview();
            } catch (Exception e) {
                //ignore
            }
            try {
                camera.setPreviewDisplay(holder);
            } catch (Exception e) {
                Toast
                        .makeText(
                                CameraOneActivity.this,
                                "设置图像预览失败",
                                Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            camera.startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    }
}
