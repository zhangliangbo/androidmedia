package com.mcivicm.media;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

/**
 * 摄像头活动
 */

public class CameraActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(CameraActivity.this, CameraTwoActivity.class));
        } else {
            startActivity(new Intent(CameraActivity.this, CameraOneActivity.class));
        }
        finish();
    }
}
