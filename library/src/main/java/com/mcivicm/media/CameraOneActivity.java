package com.mcivicm.media;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.mcivicm.media.helper.AudioRecordHelper;
import com.mcivicm.media.helper.CameraOneHelper;
import com.mcivicm.media.helper.MediaRecorderHelper;
import com.mcivicm.media.helper.ToastHelper;
import com.mcivicm.media.view.VolumeView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.nineoldandroids.view.ViewPropertyAnimator.animate;

/**
 * camera1
 */

public class CameraOneActivity extends AppCompatActivity {

    private VolumeView recordButtonLayout;
    private AppCompatTextView recordButton;
    private SurfaceView surfaceView;
    private AppCompatTextView switchCamera;

    private Camera camera;
    private int cameraId = -1;
    private SurfaceHolder surfaceHolder;
    private byte[] buffer;


    private PublishSubject<Object> ioSubject = PublishSubject.create();//发布

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_one);
        if (!ioSubject.hasObservers()) {
            ioSubject
                    .observeOn(Schedulers.io())//特别注意，发送到computation线程，避免主线程拥挤
                    .subscribe(new IoObserver());
        }
        recordButton = findViewById(R.id.record_button);
        recordButton.setOnTouchListener(new TouchListener());
        recordButtonLayout = findViewById(R.id.record_button_layout);
        surfaceView = findViewById(R.id.surface_view);
        surfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera.startPreview();
            }
        });
        surfaceView.getHolder().addCallback(new Callback());
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
                            animate(switchCamera).rotationBy(180f).setDuration(300).start();
                            if (cameraId != -1) {
                                Camera.CameraInfo info = CameraOneHelper.getInfo(cameraId);
                                openCameraFacing(info.facing == Camera.CameraInfo.CAMERA_FACING_BACK ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
                            }
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

    //是否有两面摄像头
    private Observable<Boolean> haveTwoFacingCamera() {
        return CameraOneHelper.cameraPermission(CameraOneActivity.this)
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


    private class Callback implements SurfaceHolder.Callback {

        Callback() {

        }

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            surfaceHolder = holder;
            openCameraFacing(Camera.CameraInfo.CAMERA_FACING_BACK);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surfaceHolder = holder;
            if (holder.getSurface() == null) return;
            if (camera != null) {
                camera.stopPreview();//这里会移除buffer，下面再加一遍

                camera.addCallbackBuffer(buffer);//给碗才有饭啊
                camera.setPreviewCallbackWithBuffer(new PreviewCallback());

                try {
                    camera.setPreviewDisplay(holder);
                } catch (Exception e) {
                    ToastHelper.toast(CameraOneActivity.this, "设置图像预览失败:" + e.getMessage());
                }

                camera.startPreview();

            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            surfaceHolder = holder;
            // 当Surface被销毁的时候，该方法被调用
            //在这里需要释放Camera资源
            holder.removeCallback(this);//移除监听，否则holder肯能会持有很多个回调，导致代码反复调用
            camera.stopPreview();
            camera.release();
        }
    }

    //恐有多个摄像头
    private Observable<Integer> findFirstCameraIdWithFacing(final int facing) {
        return permission()
                .flatMap(new Function<Boolean, ObservableSource<Integer>>() {
                    @Override
                    public ObservableSource<Integer> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            return Observable.just(facing);
                        } else {
                            return Observable.empty();
                        }
                    }
                })
                .flatMap(new Function<Integer, ObservableSource<Integer>>() {
                    @Override
                    public ObservableSource<Integer> apply(Integer integer) throws Exception {
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
                });
    }

    private Observable<Camera> openCamera(final int id) {
        return CameraOneHelper
                .cameraPermission(CameraOneActivity.this)
                .flatMap(new Function<Boolean, ObservableSource<Camera>>() {
                    @Override
                    public ObservableSource<Camera> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            cameraId = id;
                            Camera camera = CameraOneHelper.open(cameraId);
                            int orientation = CameraOneHelper.getDisplayOrientation(CameraOneActivity.this, cameraId);
                            int rotation = CameraOneHelper.getPictureRotation(cameraId, 0);
                            CameraOneHelper.setPreviewOrientation(camera, orientation);
                            CameraOneHelper.setPictureRotation(camera, rotation);
                            CameraOneHelper.bestResolution(camera, surfaceView.getWidth(), surfaceView.getHeight());
                            CameraOneHelper.pictureSetting(camera);
                            return Observable.just(camera);
                        } else {
                            return Observable.empty();
                        }
                    }
                });
    }

    private void openCameraFacing(int facing) {
        findFirstCameraIdWithFacing(facing)
                .flatMap(new Function<Integer, ObservableSource<Camera>>() {
                    @Override
                    public ObservableSource<Camera> apply(Integer integer) throws Exception {
                        return openCamera(integer);
                    }
                })
                .subscribe(new Observer<Camera>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Camera camera) {
                        if (CameraOneActivity.this.camera != null) {
                            CameraOneActivity.this.camera.stopPreview();
                            CameraOneActivity.this.camera.release();
                        }

                        CameraOneActivity.this.camera = camera;

                        if (buffer == null) {
                            buffer = new byte[
                                    camera.getParameters().getPreviewSize().width
                                            * camera.getParameters().getPreviewSize().height
                                            * ImageFormat.getBitsPerPixel(camera.getParameters().getPreviewFormat()) / 8
                                    ];
                        }

                        //预览数据回调
                        camera.addCallbackBuffer(buffer);//给碗才有饭啊
                        camera.setPreviewCallbackWithBuffer(new PreviewCallback());

                        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

                        try {
                            camera.setPreviewDisplay(surfaceHolder);
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

    private class ShutterCallback implements android.hardware.Camera.ShutterCallback {

        @Override
        public void onShutter() {
            ToastHelper.toast(CameraOneActivity.this, "this is a sound.");
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
            ioSubject.onNext(
                    new PreviewData(
                            camera.getParameters().getPreviewFormat(),
                            data,
                            camera.getParameters().getPreviewSize().width,
                            camera.getParameters().getPreviewSize().height,
                            camera.getParameters().getInt("rotation")
                    )
            );//发送到io线程处理
            camera.addCallbackBuffer(buffer);//每次接收数据后会从队列中移除，所以需要重新添加一遍
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


    private class IoObserver implements Observer<Object> {

        Rect rect = new Rect();

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(Object o) {
            if (o instanceof PreviewData) {
                PreviewData previewData = (PreviewData) o;
                if (previewData.data == null || previewData.data.length == 0) return;
//                FileOutputStream fos = null;
//                try {
//                    fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), System.currentTimeMillis() + ".jpeg"));
//                    byte[] rotate = CameraOneHelper.rotateYUV420Degree90(previewData.data, previewData.width, previewData.height);
//                    YuvImage yuvImage = new YuvImage(rotate, previewData.format, previewData.width, previewData.height, null);
//                    rect.set(0, 0, previewData.width, previewData.height);
//                    if (yuvImage.compressToJpeg(rect, 100, fos)) {
//                        Log.d("preview", "write success");
//                    }
//                } catch (Exception e) {
//                    //ignore
//                } finally {
//                    if (fos != null) {
//                        try {
//                            fos.close();
//                        } catch (IOException e1) {
//                            e1.printStackTrace();
//                        }
//                    }
//                }
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

        private Disposable disposable;

        private GestureDetector gestureDetector = new GestureDetector(CameraOneActivity.this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                takePicture();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                recordButtonLayout.showEdge();
                final MediaRecorder mediaRecorder = new MediaRecorder();
                //unlock之后获取下面两个参数会挂
                int width = camera.getParameters().getPictureSize().width;
                int height = camera.getParameters().getPictureSize().height;
                camera.lock();
                camera.unlock();
                mediaRecorder.setCamera(camera);//必须在MediaRecorder一初始化就设置，然后再配置MediaRecorder
                MediaRecorderHelper.configureVideoRecorder(mediaRecorder, width, height);
                mediaRecorder.setOrientationHint(CameraOneHelper.getDisplayOrientation(CameraOneActivity.this, cameraId));
                Observable.intervalRange(0, 10010, 0, 1, TimeUnit.MILLISECONDS)
                        .doOnDispose(new Action() {
                            @Override
                            public void run() throws Exception {
                                //"录制结束"
                                recordButtonLayout.hideEdge();
                                recordButtonLayout.setOrientation(0);
                                try {
                                    mediaRecorder.stop();
                                    mediaRecorder.release();
                                    ToastHelper.toast(CameraOneActivity.this, "录制完成");
                                } catch (Exception e) {
                                    ToastHelper.toast(CameraOneActivity.this, "录制时间太短，录制失败");
                                }
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<Long>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                                disposable = d;
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
                                int o = (int) (360 * aLong.floatValue() / 10000);
                                log(String.valueOf("long:" + aLong + ", o:" + o));
                                recordButtonLayout.setOrientation(o);
                            }

                            @Override
                            public void onError(Throwable e) {
                                //"录制出错"
                                recordButtonLayout.hideEdge();
                            }

                            @Override
                            public void onComplete() {
                                disposable = null;//这里要置空，否则又会执行一遍dispose()
                                //"录制结束"
                                recordButtonLayout.hideEdge();
                                recordButtonLayout.setOrientation(0);
                                try {
                                    mediaRecorder.stop();
                                    mediaRecorder.release();
                                    ToastHelper.toast(CameraOneActivity.this, "录制完成");
                                } catch (Exception e) {
                                    ToastHelper.toast(CameraOneActivity.this, "录制时间太短，录制失败");
                                }
                            }
                        });
            }
        });

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            gestureDetector.onTouchEvent(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    if (disposable != null && !disposable.isDisposed()) {
                        disposable.dispose();
                    }
                    break;
            }
            return true;
        }
    }

    //拍照
    private void takePicture() {
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

}
