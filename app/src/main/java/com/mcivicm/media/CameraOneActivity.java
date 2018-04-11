package com.mcivicm.media;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;

import com.mcivicm.media.helper.CameraOneHelper;
import com.mcivicm.media.helper.ToastHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.nineoldandroids.view.ViewPropertyAnimator.animate;

/**
 * camera1
 */

public class CameraOneActivity extends AppCompatActivity {

    private Button start;
    private SurfaceView surfaceView;
    private TextView information;

    private Camera camera;

    private ConstraintLayout pictureOperation;

    private PublishSubject<Object> publishSubject = PublishSubject.create();//发布

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_one);
        if (!publishSubject.hasObservers()) {
            publishSubject
                    .observeOn(Schedulers.computation())//特别注意，发送到computation线程，避免主线程拥挤
                    .subscribe(new SubjectObserver());
        }
        start = findViewById(R.id.start);
        surfaceView = findViewById(R.id.surface_view);
        information = findViewById(R.id.information);
        pictureOperation = findViewById(R.id.picture_operation_layout);
    }

    @Override
    protected void onStart() {
        super.onStart();
        pictureOperation.findViewById(R.id.picture_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePictureOperation(false, 150);
                camera.startPreview();
            }
        });
        pictureOperation.findViewById(R.id.picture_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePictureOperation(false, 150);
                camera.startPreview();
            }
        });
        pictureOperation.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                togglePictureOperation(false, 0);
                pictureOperation.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });
        surfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePictureOperation(false, 150);
                camera.startPreview();
            }
        });
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraOneHelper.storagePermission(CameraOneActivity.this)
                        .subscribe(new Observer<Boolean>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(Boolean aBoolean) {
                                if (aBoolean) {
                                    camera.takePicture(new ShutterCallback(), null, new PictureCallback());
                                }
                            }

                            @Override
                            public void onError(Throwable e) {
                                ToastHelper.toast(CameraOneActivity.this, e.getMessage());
                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }
        });
        surfaceView.getHolder().addCallback(new Callback());
    }

    private class Callback implements SurfaceHolder.Callback {

        Callback() {

        }

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            openCamera().subscribe(new Observer<Camera>() {
                @Override
                public void onSubscribe(Disposable d) {

                }

                @Override
                public void onNext(Camera camera) {
                    CameraOneActivity.this.camera = camera;

                    holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

                    try {
                        camera.setPreviewCallback(new PreviewCallback());
                        camera.setPreviewDisplay(holder);
                    } catch (Exception e) {
                        ToastHelper.toast(CameraOneActivity.this, "设置图像预览失败:" + e.getMessage());
                    }

                    camera.startPreview();
                }

                @Override
                public void onError(Throwable e) {
                    ToastHelper.toast(CameraOneActivity.this, e.getMessage());
                }

                @Override
                public void onComplete() {

                }
            });
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (holder.getSurface() == null) return;

            camera.stopPreview();

            try {
                camera.setPreviewCallback(new PreviewCallback());
                camera.setPreviewDisplay(holder);
            } catch (Exception e) {
                ToastHelper.toast(CameraOneActivity.this, "设置图像预览失败:" + e.getMessage());
            }

            camera.startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // 当Surface被销毁的时候，该方法被调用
            //在这里需要释放Camera资源
            holder.removeCallback(this);//移除监听，否则holder肯能会持有很多个回调，导致代码反复调用
            camera.stopPreview();
            camera.release();
        }

        private Observable<Camera> openCamera() {
            return CameraOneHelper
                    .cameraPermission(CameraOneActivity.this)
                    .flatMap(new Function<Boolean, ObservableSource<Camera>>() {
                        @Override
                        public ObservableSource<Camera> apply(Boolean aBoolean) throws Exception {
                            if (aBoolean) {
                                int total = CameraOneHelper.cameraNumber();
                                if (total > 0) {
                                    if (total == 1) {
                                        Camera camera = CameraOneHelper.open(0);
                                        int orientation = CameraOneHelper.getDisplayOrientation(CameraOneActivity.this, 0);
                                        CameraOneHelper.setPictureOrientation(camera, orientation);//适配预览和图像角度
                                        CameraOneHelper.maxResolution(camera);//以最大分辨率显示图像
                                        return Observable.just(camera);
                                    } else {
                                        for (int i = 0; i < total; i++) {
                                            if (CameraOneHelper.getInfo(i).facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                                Camera camera = CameraOneHelper.open(i);
                                                int orientation = CameraOneHelper.getDisplayOrientation(CameraOneActivity.this, i);
                                                CameraOneHelper.setPictureOrientation(camera, orientation);
                                                CameraOneHelper.maxResolution(camera);
                                                return Observable.just(camera);
                                            }
                                        }
                                        return Observable.error(new Exception("没有找到摄像头"));
                                    }
                                } else {
                                    return Observable.error(new Exception("没有找到摄像头"));
                                }
                            } else {
                                return Observable.error(new Exception("没有授权摄像头权限"));
                            }
                        }
                    });
        }
    }

    private class ShutterCallback implements android.hardware.Camera.ShutterCallback {

        @Override
        public void onShutter() {
            ToastHelper.toast(CameraOneActivity.this, "this is a sound.");
        }
    }

    private class PictureCallback implements android.hardware.Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
            //data是一个原始的JPEG图像数据，
            //在这里我们可以存储图片，很显然可以采用MediaStore
            //注意保存图片后，再次调用startPreview()回到预览
            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
            try {
                if (imageUri != null) {
                    OutputStream os = getContentResolver().openOutputStream(imageUri);
                    if (os != null) {
                        os.write(data);
                        os.flush();
                        os.close();
                    }
                }
            } catch (Exception e) {
                //ignore
            }
            togglePictureOperation(true, 150);
        }

    }

    private class PreviewCallback implements android.hardware.Camera.PreviewCallback {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            publishSubject.onNext(
                    new PreviewData(
                            camera.getParameters().getPreviewFormat(),
                            data,
                            camera.getParameters().getPreviewSize().width,
                            camera.getParameters().getPreviewSize().height,
                            camera.getParameters().getInt("rotation")
                    )
            );//发送到computation线程处理
        }
    }


    private class PreviewData {

        int format;
        byte[] data;
        int width;
        int height;
        int rotation;

        PreviewData(int format, byte[] data, int width, int height, int rotation) {
            this.format = format;
            this.data = data;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
        }
    }


    private class SubjectObserver implements Observer<Object> {

        Rect rect = new Rect();

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(Object o) {
            if (o instanceof PreviewData) {
                PreviewData previewData = (PreviewData) o;
                if (previewData.data == null || previewData.data.length == 0) return;
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), System.currentTimeMillis() + ".jpeg"));
                    byte[] rotate = CameraOneHelper.rotateYUV420Degree90(previewData.data, previewData.width, previewData.height);
                    YuvImage yuvImage = new YuvImage(rotate, previewData.format, previewData.width, previewData.height, null);
                    rect.set(0, 0, previewData.width, previewData.height);
                    if (yuvImage.compressToJpeg(rect, 100, fos)) {
                        Log.d("preview", "write success");
                    }
                } catch (Exception e) {
                    //ignore
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }
        }

        @Override
        public void onError(Throwable e) {
            ToastHelper.toast(CameraOneActivity.this, e.getMessage());
        }

        @Override
        public void onComplete() {

        }
    }

    private void processPreviewData(PreviewData previewData) {

    }

    private void togglePictureOperation(boolean show, long duration) {
        if (show) {
            animate(pictureOperation).y(0).setDuration(duration).start();
        } else {
            animate(pictureOperation).y(-pictureOperation.getHeight()).setDuration(duration).start();
        }
    }

}
