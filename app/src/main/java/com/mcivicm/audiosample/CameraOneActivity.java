package com.mcivicm.audiosample;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.widget.Button;

/**
 * camera1
 */

public class CameraOneActivity extends AppCompatActivity {

    Button start;
    SurfaceView surfaceView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_one);
        start = findViewById(R.id.start);
        surfaceView = findViewById(R.id.surface_view);
    }
}
