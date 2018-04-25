package com.mcivicm.media;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.view.OrientationEventListener;

/**
 * 手机的方位角
 */

public class OrientationEventActivity extends AppCompatActivity {

    private OrientationEventListener listener;
    private AppCompatTextView orientationInfo;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orientation_event);
        orientationInfo = findViewById(R.id.orientation);
        listener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                int screenOrientation = getResources().getConfiguration().orientation;
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                //方位角和屏幕方向没有关系
                if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    orientationInfo.setText(String.valueOf(displayRotation + ":" + orientation));
                } else {
                    orientationInfo.setText(String.valueOf(displayRotation + ":" + orientation));
                }
            }
        };
        if (listener.canDetectOrientation()) {
            orientationInfo.setText(String.valueOf("Can detect orientation"));
            listener.enable();
        } else {
            orientationInfo.setText(String.valueOf("Cannot detect orientation"));
            listener.disable();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) {
            listener.disable();
        }
    }
}
