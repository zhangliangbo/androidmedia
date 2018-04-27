package com.mcivicm.media;

import android.app.Service;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;

import com.mcivicm.media.camera2.SessionCaptureObservable;
import com.mcivicm.media.camera2.SessionState;
import com.mcivicm.media.camera2.SessionStateObservable;
import com.mcivicm.media.camera2.State;
import com.mcivicm.media.camera2.StateObservable;
import com.mcivicm.media.helper.AudioRecordHelper;
import com.mcivicm.media.helper.CameraOneHelper;
import com.mcivicm.media.helper.CameraTwoHelper;
import com.mcivicm.media.helper.ToastHelper;
import com.mcivicm.media.view.VolumeView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

/**
 * 录制视频
 */

public class RecordVideoActivity extends AppCompatActivity {

    private TextureView textureView;//preview
    private Surface previewSurface;//for previewing
    private ImageReader imageReader;//preview
    private MediaRecorder mediaRecorder;//record;

    private PublishSubject<Object> mainSubject = PublishSubject.create();//main thread.
    private PublishSubject<ImageReader> ioSubject = PublishSubject.create();//io thread.

    private VolumeView volumeView;
    private AppCompatTextView recordVideo;
    private AppCompatImageView switchCamera;

    private int sensorOrientation = 0;
    private Size viewSize;//textureView大小
    private Size previewSize;//预览大小
    private Size videoSize;//视频大小
    private Size bufferSize;//TextureView缓冲大小

    private Handler nonMainHandler;
    private HandlerThread nonMainThread;
    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private int lastTemplate = CameraDevice.TEMPLATE_PREVIEW;

    private Disposable recordDisposable;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initHandler();
        initSubject();
        setContentView(R.layout.activity_record_video);
        textureView = findViewById(R.id.texture_view);
        textureView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                viewSize = new Size(Math.max(textureView.getWidth(), textureView.getHeight()), Math.min(textureView.getWidth(), textureView.getHeight()));
                textureView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        textureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastTemplate == CameraDevice.TEMPLATE_RECORD) {
                    newPreview();
                } else {
                    preview();
                }
            }
        });
        textureView.setSurfaceTextureListener(new SurfaceTextureListener());
        volumeView = findViewById(R.id.record_button_layout);
        recordVideo = findViewById(R.id.record_button);
        recordVideo.setOnTouchListener(new TouchListener());
        switchCamera = findViewById(R.id.switch_camera);
        switchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraFacing = CameraCharacteristics.LENS_FACING_FRONT;
                } else {
                    cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
                }
                //关闭原有的摄像头并置空
                releaseSession();
                releaseCamera();
                cameraDevice = null;
                newPreview();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (textureView.isAvailable()) {
            newPreview();
        } else {
            textureView.setSurfaceTextureListener(new SurfaceTextureListener());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseSession();
        releaseCamera();
        releaseHandler();
        releaseSubject();
    }

    private void log(String s) {
        Log.d("zhang", s);
    }

    private CameraManager manager() {
        return (CameraManager) getSystemService(Service.CAMERA_SERVICE);
    }

    //生成一个新的设备
    private Observable<CameraDevice> newCameraDevice() {
        return permission()//申请权限
                .flatMap(new Function<Boolean, ObservableSource<String>>() {
                    @Override
                    public ObservableSource<String> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            String[] cameraIdList = manager().getCameraIdList();
                            for (String id : cameraIdList) {
                                CameraCharacteristics cc = manager().getCameraCharacteristics(id);
                                //图片摆正需要顺时针旋转的方位角
                                Integer so = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                                if (so != null) {
                                    sensorOrientation = so;
                                }
                                //大小
                                StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                                if (map != null) {
                                    videoSize = CameraTwoHelper.chooseVideoSize(
                                            map.getOutputSizes(MediaRecorder.class),
                                            Math.max(textureView.getWidth(), textureView.getHeight()),
                                            Math.min(textureView.getWidth(), textureView.getHeight())
                                    );
                                    previewSize = CameraTwoHelper.choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
                                }
                                //摄像头的朝向
                                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                                if (facing != null) {
                                    if (facing == cameraFacing) {
                                        return Observable.just(id);
                                    }
                                }
                            }
                            return Observable.error(new Exception(cameraFacing == CameraCharacteristics.LENS_FACING_BACK ? "未找到后置摄像头" : "未找到前置摄像头"));
                        } else {
                            return Observable.empty();
                        }
                    }
                })
                .flatMap(new Function<String, ObservableSource<Pair<CameraDevice, State>>>() {
                    @Override
                    public ObservableSource<Pair<CameraDevice, State>> apply(String id) throws Exception {
                        return new StateObservable(manager(), id, nonMainHandler);//一旦打开摄像头，马上会收到摄像头被占用的通知
                    }
                })
                .flatMap(new Function<Pair<CameraDevice, State>, ObservableSource<CameraDevice>>() {
                    @Override
                    public ObservableSource<CameraDevice> apply(Pair<CameraDevice, State> cameraDeviceStatePair) throws Exception {
                        switch (cameraDeviceStatePair.second) {
                            case Open:
                                RecordVideoActivity.this.cameraDevice = cameraDeviceStatePair.first;//给类范围变量赋个值
                                return Observable.just(cameraDeviceStatePair.first);
                            default:
                                RecordVideoActivity.this.cameraDevice = null;
                                return Observable.empty();//通道关闭或失联，仅赋值，不传给下游
                        }
                    }
                });
    }

    private void releaseSession() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
        }
    }

    //释放摄像头
    private void releaseCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
        }
    }

    //打开摄像头
    private Observable<CameraDevice> cameraDevice() {
        if (cameraDevice == null) {
            return newCameraDevice();
        } else {
            return Observable.just(cameraDevice);
        }
    }

    private List<Surface> list(Surface... surfaces) {
        List<Surface> list = new ArrayList<>();
        Collections.addAll(list, surfaces);
        return list;
    }


    //生成一个新的捕捉会话，新建捕捉会话会关闭原来的会话，因为摄像头一次只能开启一个会话
    private Observable<CameraCaptureSession> newCaptureSession(final List<Surface> list) {
        return cameraDevice()
                .flatMap(new Function<CameraDevice, ObservableSource<Pair<CameraCaptureSession, SessionState>>>() {
                    @Override
                    public ObservableSource<Pair<CameraCaptureSession, SessionState>> apply(CameraDevice cameraDevice) throws Exception {
                        return new SessionStateObservable(cameraDevice, list, nonMainHandler);
                    }
                })
                .flatMap(new Function<Pair<CameraCaptureSession, SessionState>, ObservableSource<CameraCaptureSession>>() {
                    @Override
                    public ObservableSource<CameraCaptureSession> apply(Pair<CameraCaptureSession, SessionState> cameraCaptureSessionSessionStatePair) throws Exception {
                        switch (cameraCaptureSessionSessionStatePair.second) {
                            case Configured:
                                RecordVideoActivity.this.cameraCaptureSession = cameraCaptureSessionSessionStatePair.first;
                                return Observable.just(cameraCaptureSessionSessionStatePair.first);
                            case ConfiguredFailed:
                                RecordVideoActivity.this.cameraCaptureSession = null;
                                return Observable.error(new Exception("打开会话失败"));
                            default://其他事件过滤
                                return Observable.empty();
                        }
                    }
                });
    }


    private void initHandler() {
        nonMainThread = new HandlerThread("Camera2");
        nonMainThread.start();
        nonMainHandler = new Handler(nonMainThread.getLooper());
    }

    private void releaseHandler() {
        nonMainThread.quitSafely();
    }

    private void initSubject() {
        if (!mainSubject.hasObservers()) {
            mainSubject
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new MainObserver());
        }
        if (!ioSubject.hasObservers()) {
            ioSubject
                    .observeOn(Schedulers.io())
                    .subscribe(new IoObserver());
        }
    }

    private void stopRepeating() {
        if (cameraCaptureSession != null) {
            try {
                cameraCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                //ignore.
            }
        }
    }

    private void releaseSubject() {
        mainSubject.onComplete();
        ioSubject.onComplete();
    }

    //用新的会话新建捕捉
    private void newCapture(final int captureTemplate, final List<Surface> surfaceList, final SessionCaptureObservable.RequestBuilderInitializer initializer) {
        newCaptureSession(surfaceList)
                .flatMap(new Function<CameraCaptureSession, ObservableSource<Pair<Integer, ? extends CameraMetadata>>>() {
                    @Override
                    public ObservableSource<Pair<Integer, ? extends CameraMetadata>> apply(CameraCaptureSession cameraCaptureSession) throws Exception {
                        return new SessionCaptureObservable(
                                cameraCaptureSession,
                                captureTemplate,
                                initializer,
                                nonMainHandler);
                    }
                })
                .subscribe(new CaptureResultObserver(captureTemplate));
    }

    //使用现有会话捕捉图像
    private void capture(final int captureTemplate, final SessionCaptureObservable.RequestBuilderInitializer initializer) {
        if (cameraCaptureSession == null) {
            return;
        }
        Observable.just(cameraCaptureSession)
                .flatMap(new Function<CameraCaptureSession, ObservableSource<Pair<Integer, ? extends CameraMetadata>>>() {
                    @Override
                    public ObservableSource<Pair<Integer, ? extends CameraMetadata>> apply(CameraCaptureSession cameraCaptureSession) throws Exception {
                        return new SessionCaptureObservable(
                                cameraCaptureSession,
                                captureTemplate,
                                initializer,
                                nonMainHandler);
                    }
                })
                .subscribe(new CaptureResultObserver(captureTemplate));
    }

    //请求权限
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

    private Surface newTextureSurface() {
        if (!textureView.isAvailable() || viewSize == null) {
            return null;
        }
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        if (previewSize != null) {
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            bufferSize = new Size(previewSize.getWidth(), previewSize.getHeight());
        } else {//恐摄像头没有支持的预览大小
            surfaceTexture.setDefaultBufferSize(viewSize.getWidth(), viewSize.getHeight());
            bufferSize = new Size(viewSize.getWidth(), viewSize.getHeight());
        }
        configureTransform(textureView.getWidth(), textureView.getHeight());
        return new Surface(surfaceTexture);
    }

    private void newPreview() {
        //每次都需要新的Surface，因为开启新的会话会关闭原有会话，关闭原有会话会销毁Surface，所以Surface没有办法复用。
        previewSurface = newTextureSurface();
        if (previewSurface == null || bufferSize == null)
            return;
        //释放已有资源
        if (imageReader != null) {
            imageReader.close();
        }
        imageReader = ImageReader.newInstance(bufferSize.getWidth(), bufferSize.getHeight(), ImageFormat.JPEG, 1);
        imageReader.setOnImageAvailableListener(new ImageAvailable(), nonMainHandler);
        newCapture(
                CameraDevice.TEMPLATE_PREVIEW,
                list(previewSurface, imageReader.getSurface()),//注册两个Surface.
                new SessionCaptureObservable.RequestBuilderInitializer() {
                    @Override
                    public void onCreateRequestBuilder(CaptureRequest.Builder builder) {
                        builder.addTarget(previewSurface);//预览的时候只用一个Surface.
                    }
                });
    }

    private void preview() {
        //使用现有Session预览
        capture(CameraDevice.TEMPLATE_PREVIEW, new SessionCaptureObservable.RequestBuilderInitializer() {
            @Override
            public void onCreateRequestBuilder(CaptureRequest.Builder builder) {
                builder.addTarget(previewSurface);//使用现有Surface预览（没有主动关闭或者新建Session，Surface一般是存在的）
            }
        });
    }

    private void stillCapture() {
        //使用现有的Session捕获图片
        capture(CameraDevice.TEMPLATE_STILL_CAPTURE, new SessionCaptureObservable.RequestBuilderInitializer() {
            @Override
            public void onCreateRequestBuilder(CaptureRequest.Builder builder) {
                builder.addTarget(imageReader.getSurface());
                builder.set(CaptureRequest.JPEG_ORIENTATION, CameraTwoHelper.getOrientationHint(sensorOrientation, getWindowManager().getDefaultDisplay().getRotation()));
            }
        });
    }

    private void newRecord() {
        //每次都需要新的Surface，因为开启新的会话会关闭原有会话，关闭原有会话会销毁Surface，所以Surface没有办法复用。
        final Surface recordSurface = newTextureSurface();
        if (recordSurface == null)
            return;
        mediaRecorder = new MediaRecorder();
        //设置视频的大小
        if (videoSize != null) {
            CameraTwoHelper.configureVideoMediaRecorder(mediaRecorder, videoSize.getWidth(), videoSize.getHeight());
        } else {
            CameraTwoHelper.configureVideoMediaRecorder(mediaRecorder, 640, 480);//就选个最小的吧
        }
        mediaRecorder.setOrientationHint(CameraTwoHelper.getOrientationHint(sensorOrientation, getWindowManager().getDefaultDisplay().getRotation()));
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            ToastHelper.toast(RecordVideoActivity.this, e.getMessage());
            return;
        }
        //a new capture
        newCapture(
                CameraDevice.TEMPLATE_RECORD,
                list(recordSurface, mediaRecorder.getSurface()),
                new SessionCaptureObservable.RequestBuilderInitializer() {
                    @Override
                    public void onCreateRequestBuilder(CaptureRequest.Builder builder) {
                        builder.addTarget(recordSurface);
                        builder.addTarget(mediaRecorder.getSurface());
                    }
                });//must after calling prepare().
        //start recording
        mediaRecorder.start();
    }

    private void stopRecord() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                ToastHelper.toast(this, "录制时间太短，录制失败");
                return;
            }
            mediaRecorder.reset();
            mediaRecorder.release();
            ToastHelper.toast(RecordVideoActivity.this, "录制完成");
        }
    }

    private void recordVideo() {
        Observable.intervalRange(0, 10020, 0, 1, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnDispose(new Action() {
                    @Override
                    public void run() throws Exception {
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        recordVideo.setText("");
                        //下面两个方法不能互换，因为MediaPlayer的release方法会释放注册到会话的Surface，这会影响摄像头的正常捕获，导致后续会话无法正常关闭。
                        //所以正确的方法应该先关闭会话，再关闭释放MediaPlayer的资源。
                        newPreview();
                        stopRecord();
                    }
                })
                .subscribe(new io.reactivex.Observer<Long>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                        recordDisposable = d;

                        volumeView.showEdge();
                        volumeView.setOrientation(0);
                        recordVideo.setText("");

                        //开始录制视频
                        newRecord();
                    }

                    @Override
                    public void onNext(Long aLong) {
                        volumeView.setOrientation((int) (360 * aLong.floatValue() / 10000));
                        recordVideo.setText(String.valueOf(aLong.intValue() / 1000));
                    }

                    @Override
                    public void onError(Throwable e) {
                        recordDisposable = null;
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        recordVideo.setText("");
                        newPreview();
                        stopRecord();
                    }

                    @Override
                    public void onComplete() {
                        recordDisposable = null;
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        recordVideo.setText("");
                        newPreview();
                        stopRecord();
                    }
                });
    }


    /**
     * 横竖屏切换时，需要将原始数据变换后显示在TextureView上
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == bufferSize) {
            return;
        }
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);//TextureView的大小
        RectF bufferRect = new RectF(0, 0, bufferSize.getHeight(), bufferSize.getWidth());//视频缓冲的大小
        bufferRect.offset(viewRect.centerX() - bufferRect.centerX(), viewRect.centerY() - bufferRect.centerY());//使两个矩形的中心重合
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);//这里并不需要保持原矩形（也就是视图矩形）的长宽比，而是应该保证视图矩形的长宽比，所以选择Fill模式，具体参考https://blog.csdn.net/cquwentao/article/details/51445269
        float scale = Math.max(viewRect.height() / bufferRect.height(), viewRect.width() / bufferRect.width());//选大的能铺满屏幕
        matrix.postScale(scale, scale, viewRect.centerX(), viewRect.centerY());//以视图中心为参考点放大到铺满屏幕
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (Surface.ROTATION_0 != rotation) {
            matrix.postRotate(360f - 90f * rotation, viewRect.centerX(), viewRect.centerY());
        }
        textureView.setTransform(matrix);
    }

    //表面纹理监听器
    private class SurfaceTextureListener implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            newPreview();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    }

    private class ImageAvailable implements ImageReader.OnImageAvailableListener {

        @Override
        public void onImageAvailable(ImageReader reader) {
            log("available");
            stopRepeating();
            ioSubject.onNext(reader);
        }
    }

    private class MainObserver implements Observer<Object> {

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(Object o) {

        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onComplete() {

        }
    }

    private class IoObserver implements Observer<ImageReader> {

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(ImageReader imageReader) {
            log("new image");
            Image image = imageReader.acquireNextImage();
//            saveImage(image);
            image.close();
        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onComplete() {

        }
    }

    private class CaptureResultObserver implements Observer<Pair<Integer, ? extends CameraMetadata>> {

        private int template = 0;

        CaptureResultObserver(int template) {
            this.template = template;
        }

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(Pair<Integer, ? extends CameraMetadata> integerPair) {
            lastTemplate = template;
            switch (template) {
                case CameraDevice.TEMPLATE_PREVIEW:
                    Log.d("zhang", "previewing.");
                    break;
                case CameraDevice.TEMPLATE_RECORD:
                    Log.d("zhang", "recording.");
                    break;
                case CameraDevice.TEMPLATE_STILL_CAPTURE:
                    Log.d("zhang", "still capture");
                    break;
            }
        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onComplete() {

        }
    }

    private void saveImage(Image image) {
        //处理数据
        switch (image.getFormat()) {
            case ImageFormat.JPEG:
                if (image.getPlanes() != null && image.getPlanes().length > 0) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    try {
                        FileOutputStream fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "camera2_image.jpeg"));
                        fos.getChannel().write(buffer);
                        fos.close();
                    } catch (IOException e) {
                        ToastHelper.toast(RecordVideoActivity.this, e.getMessage());
                    }
                }
                break;
        }
    }

    private class TouchListener implements View.OnTouchListener {

        GestureDetector gestureDetector = new GestureDetector(RecordVideoActivity.this, new GestureListener());

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

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            stillCapture();//点击拍照
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            recordVideo();
        }
    }


}
