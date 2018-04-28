package com.mcivicm.media;

import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.mcivicm.media.helper.AudioRecordHelper;
import com.mcivicm.media.helper.CameraOneHelper;
import com.mcivicm.media.helper.MediaRecorderHelper;
import com.mcivicm.media.helper.ToastHelper;
import com.mcivicm.media.view.VolumeView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.nineoldandroids.view.ViewPropertyAnimator.animate;

/**
 * camera1
 */

public class CameraOneActivity extends AppCompatActivity {

    private TextureView textureView;
    private MediaRecorder mediaRecorder;

    private VolumeView recordButtonLayout;
    private AppCompatTextView recordButton;
    private AppCompatImageView switchCamera;

    private Camera camera;
    private int cameraId;
    private int cameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
    private SurfaceTexture surfaceTexture;

    private byte[] buffer;
    private Camera.Parameters parameters;

    private Disposable recordDisposable;

    private PublishSubject<Object> ioSubject = PublishSubject.create();//发布

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initSubject();
        setContentView(R.layout.activity_camera_one);
        recordButton = findViewById(R.id.record_button);
        recordButton.setOnTouchListener(new TouchListener());
        recordButtonLayout = findViewById(R.id.record_button_layout);
        textureView = findViewById(R.id.texture_view);
        textureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preview();
            }
        });
        textureView.setSurfaceTextureListener(new SurfaceTextureListener());
        switchCamera = findViewById(R.id.switch_camera);
        haveTwoFacingCamera().subscribe(new Observer<Boolean>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(Boolean aBoolean) {
                if (aBoolean) {
                    switchCamera.setVisibility(View.VISIBLE);
                    switchCamera.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            cameraFacing = cameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
                            if (camera != null) {
                                camera.release();
                                camera = null;
                            }
                            preview();
                            animate(switchCamera).rotationBy(180f).setDuration(300).start();
                        }
                    });
                } else {
                    switchCamera.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            camera.release();
        }
    }

    private void initSubject() {
        if (!ioSubject.hasObservers()) {
            ioSubject
                    .observeOn(Schedulers.io())//特别注意，发送到io线程，避免主线程拥挤
                    .subscribe(new IoObserver());
        }
    }

    private Observable<Boolean> permission() {
        return Observable.zip(
                CameraOneHelper.cameraPermission(this),
                CameraOneHelper.storagePermission(this),
                AudioRecordHelper.recordAudioPermission(this),
                new Function3<Boolean, Boolean, Boolean, Boolean>() {
                    @Override
                    public Boolean apply(Boolean aBoolean, Boolean aBoolean2, Boolean aBoolean3) throws Exception {
                        return aBoolean && aBoolean2 && aBoolean3;
                    }
                }
        );
    }

    //是否有两面摄像头
    private Observable<Boolean> haveTwoFacingCamera() {
        return permission()
                .flatMap(new Function<Boolean, ObservableSource<Boolean>>() {
                    @Override
                    public ObservableSource<Boolean> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            int total = CameraOneHelper.cameraNumber();
                            boolean haveFront = false;
                            boolean haveBack = false;
                            for (int i = 0; i < total; i++) {
                                Camera.CameraInfo info = CameraOneHelper.getInfo(i);
                                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                    haveFront = true;
                                } else {
                                    haveBack = true;
                                }
                            }
                            return Observable.just(haveFront && haveBack);
                        } else {
                            return Observable.empty();
                        }
                    }
                });
    }

    //打开特定朝向的摄像头
    private Observable<Camera> newCameraFacing(final int facing) {
        return permission()//申请权限
                .flatMap(new Function<Boolean, ObservableSource<Integer>>() {
                    @Override
                    public ObservableSource<Integer> apply(Boolean aBoolean) throws Exception {
                        return aBoolean ? Observable.just(facing) : Observable.<Integer>empty();
                    }
                })
                .flatMap(new Function<Integer, ObservableSource<Integer>>() {
                    @Override
                    public ObservableSource<Integer> apply(Integer integer) throws Exception {//查找合适的摄像头Id
                        if (facing != Camera.CameraInfo.CAMERA_FACING_BACK && facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            return Observable.error(new Exception("facing must be back or front."));
                        }
                        int total = CameraOneHelper.cameraNumber();
                        for (int i = 0; i < total; i++) {
                            Camera.CameraInfo info = CameraOneHelper.getInfo(i);
                            if (info.facing == facing) {
                                return Observable.just(i);
                            }
                        }
                        return Observable.error(new Exception("can not find camera which facing " + (facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back") + "."));
                    }
                })
                .doOnNext(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        CameraOneActivity.this.cameraId = integer;
                    }
                })
                .flatMap(new Function<Integer, ObservableSource<Camera>>() {
                    @Override
                    public ObservableSource<Camera> apply(Integer id) throws Exception {
                        Camera camera = CameraOneHelper.open(id);
                        int orientation = CameraOneHelper.getDisplayOrientation(CameraOneActivity.this, id);
                        int rotation = CameraOneHelper.getPictureRotation(id, 0);
                        CameraOneHelper.setPreviewOrientation(camera, orientation);
                        CameraOneHelper.setPictureRotation(camera, rotation);
                        //设置预览和图片分辨率
                        CameraOneHelper.setPreviewAndPictureResolution(camera, Math.max(textureView.getWidth(), textureView.getHeight()), Math.min(textureView.getWidth(), textureView.getHeight()));
                        CameraOneHelper.pictureSetting(camera);
                        return Observable.just(camera);
                    }
                })
                .doOnNext(new Consumer<Camera>() {
                    @Override
                    public void accept(Camera camera) throws Exception {
                        CameraOneActivity.this.camera = camera;//给全局赋值
                        CameraOneActivity.this.parameters = camera.getParameters();
                        CameraOneActivity.this.buffer = new byte[
                                parameters.getPreviewSize().width
                                        * parameters.getPreviewSize().height
                                        * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8
                                ];
                    }
                });
    }

    //当前可用的摄像头
    private Observable<Camera> camera() {
        if (camera == null) {
            return newCameraFacing(cameraFacing);
        } else {
            return Observable.just(camera);
        }
    }

    private void preview() {
        camera()
                .subscribe(new Observer<Camera>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Camera camera) {
                        //预览数据回调
                        camera.addCallbackBuffer(buffer);//给碗才有饭啊
                        camera.setPreviewCallbackWithBuffer(new PreviewCallback());

                        try {
                            camera.setPreviewTexture(surfaceTexture);
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

    private void picture() {
        camera()
                .subscribe(new Observer<Camera>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Camera camera) {
                        camera.takePicture(null, null, new PictureCallback());
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void initMediaRecorder() {
        mediaRecorder = new MediaRecorder();
        //unlock之后获取下面两个参数会挂
        Camera.Size videoSize = CameraOneHelper.findEnoughSize(parameters.getSupportedVideoSizes(), Math.max(textureView.getWidth(), textureView.getHeight()), Math.min(textureView.getWidth(), textureView.getHeight()));
        camera.unlock();
        mediaRecorder.setCamera(camera);//必须在MediaRecorder一初始化就设置，然后再配置MediaRecorder
        MediaRecorderHelper.configureVideoRecorder(mediaRecorder, videoSize.width, videoSize.height);
        mediaRecorder.setOrientationHint(CameraOneHelper.getDisplayOrientation(CameraOneActivity.this, cameraId));
    }

    private void releaseMediaRecorder() {
        try {
            mediaRecorder.stop();
        } catch (Exception e) {
            ToastHelper.toast(CameraOneActivity.this, "录制时间太短，录制失败");
        }
        mediaRecorder.release();
    }

    private void record() {
        Observable.intervalRange(0, 10020, 0, 1, TimeUnit.MILLISECONDS)
                .doOnDispose(new Action() {
                    @Override
                    public void run() throws Exception {
                        //"录制结束"
                        recordButtonLayout.hideEdge();
                        recordButtonLayout.setOrientation(0);
                        releaseMediaRecorder();
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Long>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        recordDisposable = d;
                        initMediaRecorder();
                        //"开始录制"
                        recordButtonLayout.showEdge();
                        try {
                            mediaRecorder.prepare();
                            mediaRecorder.start();
                        } catch (Exception ex) {
                            //ignore
                            ToastHelper.toast(CameraOneActivity.this, ex.getMessage());
                        }
                    }

                    @Override
                    public void onNext(Long aLong) {
                        //"录制中"
                        recordButtonLayout.setOrientation((int) (360 * aLong.floatValue() / 10000));
                    }

                    @Override
                    public void onError(Throwable e) {
                        //"录制出错"
                        recordButtonLayout.hideEdge();
                        recordButtonLayout.setOrientation(0);
                        releaseMediaRecorder();
                    }

                    @Override
                    public void onComplete() {
                        recordDisposable = null;//这里要置空，否则又会执行一遍dispose()
                        //"录制结束"
                        recordButtonLayout.hideEdge();
                        recordButtonLayout.setOrientation(0);
                        releaseMediaRecorder();
                    }
                });
    }

    private class SurfaceTextureListener implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            surfaceTexture = surface;
            preview();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            surfaceTexture = surface;
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            surfaceTexture = surface;
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            surfaceTexture = surface;
        }
    }

    private class PictureData {

        byte[] data;

        PictureData(byte[] data) {
            this.data = data;
        }
    }

    private class PictureCallback implements android.hardware.Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
            ioSubject.onNext(new PictureData(data));
            camera.stopPreview();
        }

    }

    private class PreviewCallback implements android.hardware.Camera.PreviewCallback {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            ioSubject.onNext(new PreviewData(data));//发送到io线程处理
            camera.addCallbackBuffer(buffer);//每次接收数据后会从队列中移除，所以需要重新添加一遍
        }
    }


    private class PreviewData {

        byte[] data;

        PreviewData(byte[] data) {
            this.data = data;
        }
    }


    private class IoObserver implements Observer<Object> {

        Rect rect = new Rect();

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(Object o) {
            if (o instanceof PreviewData) {
                PreviewData previewData = (PreviewData) o;
                if (previewData.data == null || previewData.data.length == 0 || parameters == null)
                    return;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    YuvImage yuvImage = new YuvImage(previewData.data, parameters.getPreviewFormat(), parameters.getPreviewSize().width, parameters.getPreviewSize().height, null);
                    rect.set(0, 0, parameters.getPreviewSize().width, parameters.getPreviewSize().height);
                    if (yuvImage.compressToJpeg(rect, 100, baos)) {
                        //deal with the preview of jpeg format.
                    }
                } catch (Exception e) {
                    //ignore
                } finally {
                    baos.reset();
                    try {
                        baos.close();
                    } catch (IOException e) {
                        //ignore
                    }
                }
            } else if (o instanceof PictureData) {
                PictureData pd = (PictureData) o;
                try {
                    FileOutputStream fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "camera1_temp_image.jpeg"));
                    fos.write(pd.data);
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
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


    private void log(String text) {
        Log.d("zhang", text);
    }


    private class TouchListener implements View.OnTouchListener {

        GestureDetector gestureDetector = new GestureDetector(CameraOneActivity.this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                picture();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                recordButtonLayout.showEdge();
                record();
            }
        });

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
}
