package com.mcivicm.media;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;

import com.mcivicm.media.view.MatrixView;

/**
 * 矩阵活动
 */

public class MatrixActivity extends AppCompatActivity {

    private MatrixView matrixView;
    private AppCompatTextView matrixInfo;
    private final String explain = "[scaleX,skewX,transX][scaleY,skewY,transY][per0,per1,per2]";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matrix);
        matrixView = findViewById(R.id.matrix_view);
        matrixInfo = findViewById(R.id.matrix);
    }

    @Override
    protected void onStart() {
        super.onStart();
        RectF src = new RectF(0, 0, 100, 200);
        matrixView.setSrc(src);
        RectF dst = new RectF(200, 0, 300, 50);
        matrixView.setDst(dst);
        Matrix matrix = new Matrix();
        if (matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL)) {
            matrixInfo.setText(String.valueOf(explain + "\n" + matrix.toShortString()));
        }

    }
}
