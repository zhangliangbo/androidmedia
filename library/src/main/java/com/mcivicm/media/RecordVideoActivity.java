package com.mcivicm.media;

import android.app.Service;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
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
import com.mcivicm.media.camera2.googlevideo.AutoFitTextureView;
import com.mcivicm.media.helper.AudioRecordHelper;
import com.mcivicm.media.helper.CameraOneHelper;
import com.mcivicm.media.helper.CameraTwoHelper;
import com.mcivicm.media.helper.ToastHelper;
import com.mcivicm.media.view.VolumeView;

import java.io.IOException;
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

/**
 * 录制视频
 */

public class RecordVideoActivity extends AppCompatActivity {

    private AutoFitTextureView textureView;//preview
    private MediaRecorder mediaRecorder;//record;

    private VolumeView volumeView;
    private AppCompatTextView recordVideo;

    private int sensorOrientation = 0;
    private Size viewSize;//textureView大小
    private Size previewSize;//预览大小
    private Size videoSize;//视频大小

    private Handler nonMainHandler;
    private HandlerThread nonMainThread;
    private CameraManager cameraManager = null;
    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private Capture nextCapture = null;//下一个捕捉

    private Disposable recordDisposable;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initHandler();
        setContentView(R.layout.activity_record_video);
        textureView = findViewById(R.id.texture_view);
        textureView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                viewSize = new Size(Math.max(textureView.getWidth(), textureView.getHeight()), Math.min(textureView.getWidth(), textureView.getHeight()));
                textureView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        textureView.setSurfaceTextureListener(new SurfaceTextureListener());
        volumeView = findViewById(R.id.record_button_layout);
        recordVideo = findViewById(R.id.record_button);

        recordVideo.setOnTouchListener(new TouchListener());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (textureView.isAvailable()) {
            startPreview();
        } else {
            textureView.setSurfaceTextureListener(new SurfaceTextureListener());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nonMainThread.quitSafely();
    }

    private void log(String s) {
        Log.d("zhang", s);
    }

    private CameraManager manager() {
        if (cameraManager == null) {
            cameraManager = (CameraManager) getSystemService(Service.CAMERA_SERVICE);
        }
        return cameraManager;
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
                                    configureTransform(textureView.getWidth(), textureView.getHeight());
                                }
                                //屏幕的方向
                                int orientation = getResources().getConfiguration().orientation;
                                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                    textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                                } else {
                                    textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
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
                        log(cameraCaptureSessionSessionStatePair.second.name());
                        switch (cameraCaptureSessionSessionStatePair.second) {
                            case Configured:
                                RecordVideoActivity.this.cameraCaptureSession = cameraCaptureSessionSessionStatePair.first;
                                return Observable.just(cameraCaptureSessionSessionStatePair.first);
                            case ConfiguredFailed:
                                RecordVideoActivity.this.cameraCaptureSession = null;
                                return Observable.empty();
                            case Closed:
                                RecordVideoActivity.this.cameraCaptureSession = null;
                                //会话关闭之后看有没有下一个捕获任务，如果有，则立即开始
                                if (nextCapture != null) {
                                    newCapture(nextCapture.template, nextCapture.surfaceList);
                                    if (nextCapture.template == CameraDevice.TEMPLATE_RECORD) {
                                        mediaRecorder.start();//如果是录制视频，则开始录制
                                    }
                                    nextCapture = null;
                                }
                                return Observable.empty();
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

    //用新的会话新建捕捉
    private void newCapture(final int captureTemplate, final List<Surface> surfaceList) {
        newCaptureSession(surfaceList)
                .flatMap(new Function<CameraCaptureSession, ObservableSource<Pair<Integer, ? extends CameraMetadata>>>() {
                    @Override
                    public ObservableSource<Pair<Integer, ? extends CameraMetadata>> apply(CameraCaptureSession cameraCaptureSession) throws Exception {
                        return new SessionCaptureObservable(
                                cameraCaptureSession,
                                captureTemplate,
                                new SessionCaptureObservable.RequestBuilderInitializer() {
                                    @Override
                                    public void onCreateRequestBuilder(CaptureRequest.Builder builder) {
                                        for (Surface surface : surfaceList) {
                                            builder.addTarget(surface);
                                        }
                                    }
                                },
                                nonMainHandler);
                    }
                })
                .subscribe(new Observer<Pair<Integer, ? extends CameraMetadata>>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Pair<Integer, ? extends CameraMetadata> integerPair) {
                        switch (captureTemplate) {
                            case CameraDevice.TEMPLATE_PREVIEW:
//                                Log.d("zhang", "previewing.");
                                break;
                            case CameraDevice.TEMPLATE_RECORD:
//                                Log.d("zhang", "recording.");
                                break;
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

    private void startPreview() {
        if (!textureView.isAvailable() || viewSize == null) {
            return;
        }
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        if (previewSize != null) {
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        } else {
            surfaceTexture.setDefaultBufferSize(viewSize.getWidth(), viewSize.getHeight());
        }
        Surface previewSurface = new Surface(surfaceTexture);
        //还没有开启会话则开启新的会话，如果有会话，则先准备数据，关闭当前会话，等待OnClosed回调在进行下次会话
        if (cameraCaptureSession == null) {
            newCapture(CameraDevice.TEMPLATE_PREVIEW, list(previewSurface));
        } else {
            nextCapture = new Capture(CameraDevice.TEMPLATE_PREVIEW, list(previewSurface));
            //准备好下一次捕获需要的数据之后立即关闭当前会话，在当前会话结束时会调用OnClosed接口，届时再开启下一次会话，保证两次会话是串行的。如果两次会话有交叉的地方，会导致错误。
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();//会触发OnClosed事件
            }
        }
    }

    private void startRecord() {
        if (!textureView.isAvailable() || viewSize == null) {
            return;
        }
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        if (previewSize != null) {
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        } else {
            surfaceTexture.setDefaultBufferSize(viewSize.getWidth(), viewSize.getHeight());
        }
        Surface recordSurface = new Surface(surfaceTexture);
        mediaRecorder = new MediaRecorder();
        //设置视频的大小
        if (videoSize != null) {
            CameraTwoHelper.configureVideoMediaRecorder(mediaRecorder, videoSize.getWidth(), videoSize.getHeight());
        } else {
            if (previewSize != null) {
                CameraTwoHelper.configureVideoMediaRecorder(mediaRecorder, previewSize.getWidth(), previewSize.getHeight());
            } else {
                CameraTwoHelper.configureVideoMediaRecorder(mediaRecorder, viewSize.getWidth(), viewSize.getHeight());
            }
        }
        mediaRecorder.setOrientationHint(CameraTwoHelper.getOrientationHint(sensorOrientation, getWindowManager().getDefaultDisplay().getRotation()));
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            ToastHelper.toast(RecordVideoActivity.this, e.getMessage());
            return;
        }
        if (cameraCaptureSession == null) {
            //a new capture
            newCapture(CameraDevice.TEMPLATE_RECORD, list(recordSurface, mediaRecorder.getSurface()));//must after calling prepare().
            //start recording
            mediaRecorder.start();
        } else {
            nextCapture = new Capture(CameraDevice.TEMPLATE_RECORD, list(recordSurface, mediaRecorder.getSurface()));
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
            }
        }
    }

    private void stopRecord() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                ToastHelper.toast(this, "录制时间太短，录制失败");
            }
            mediaRecorder.reset();
            mediaRecorder.release();
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
                        ToastHelper.toast(RecordVideoActivity.this, "录制完成");
                        //下面两个方法不能互换，因为MediaPlayer的release方法会释放注册到会话的Surface，这会影响摄像头的正常捕获，导致后续会话无法正常关闭。
                        //所以正确的方法应该先关闭会话，再关闭释放MediaPlayer的资源。
                        startPreview();
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
                        startRecord();
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
                        ToastHelper.toast(RecordVideoActivity.this, "录制失败");
                        startPreview();
                        stopRecord();
                    }

                    @Override
                    public void onComplete() {
                        recordDisposable = null;
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        recordVideo.setText("");
                        ToastHelper.toast(RecordVideoActivity.this, "录制完成");
                        startPreview();
                        stopRecord();
                    }
                });
    }


    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == previewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth()
            );
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private class Capture {

        int template;
        List<Surface> surfaceList;

        Capture(int template, List<Surface> surfaceList) {
            this.template = template;
            this.surfaceList = surfaceList;
        }
    }

    //表面纹理监听器
    private class SurfaceTextureListener implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            startPreview();
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
            startRecord();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            recordVideo();
        }
    }


}
