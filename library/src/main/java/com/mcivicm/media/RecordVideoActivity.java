package com.mcivicm.media;

import android.app.Service;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;

import com.mcivicm.media.camera2.SessionCaptureObservable;
import com.mcivicm.media.camera2.SessionState;
import com.mcivicm.media.camera2.SessionStateObservable;
import com.mcivicm.media.camera2.State;
import com.mcivicm.media.camera2.StateObservable;
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

/**
 * 录制视频
 */

public class RecordVideoActivity extends AppCompatActivity {

    private SurfaceView surfaceView;
    private SurfaceView surfaceView2;
    private VolumeView volumeView;
    private AppCompatTextView recordVideo;

    private SurfaceHolder surfaceHolder;
    private SurfaceHolder surfaceHolder2;
    private Surface persistentSurface = MediaCodec.createPersistentInputSurface();
    private final MediaRecorder mediaRecorder = new MediaRecorder();

    private Handler nonMainHandler;
    private Capture nextCapture = null;
    private CameraManager cameraManager = null;
    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;

    private Disposable recordDisposable;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initHandler();
        setContentView(R.layout.activity_record_video);
        surfaceView = findViewById(R.id.surface_view);
        surfaceView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
//                CameraTwoHelper.configureVideoMediaRecorder(mediaRecorder, surfaceView.getWidth(), surfaceView.getHeight());
//                try {
//                    mediaRecorder.prepare();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                surfaceView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        surfaceView2 = findViewById(R.id.surface_view2);
        surfaceView2.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                surfaceView2.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        volumeView = findViewById(R.id.record_button_layout);
        recordVideo = findViewById(R.id.record_button);

        surfaceView.getHolder().addCallback(new SurfaceCallback());
        surfaceView2.getHolder().addCallback(new SurfaceCallback2());
        recordVideo.setOnTouchListener(new TouchListener());
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        return CameraOneHelper.cameraPermission(RecordVideoActivity.this)//申请摄像头权限
                .flatMap(new Function<Boolean, ObservableSource<String>>() {
                    @Override
                    public ObservableSource<String> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            String[] cameraIdList = manager().getCameraIdList();
                            for (String id : cameraIdList) {
                                CameraCharacteristics cc = manager().getCameraCharacteristics(id);
                                int facing = cc.get(CameraCharacteristics.LENS_FACING);
                                if (facing == cameraFacing) {
                                    return Observable.just(id);
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

    //生成一个新的捕捉会话
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
                                //发送捕捉请求
                                if (nextCapture != null) {
                                    Message m = Message.obtain(nonMainHandler);
                                    m.obj = nextCapture;
                                    m.sendToTarget();
                                    nextCapture = null;
                                }
                                return Observable.empty();
                            default://其他事件过滤
                                return Observable.empty();
                        }
                    }
                });
    }

    //打开摄像头会话
    private Observable<CameraCaptureSession> cameraCaptureSession(final List<Surface> list) {
        if (cameraCaptureSession == null) {
            return newCaptureSession(list);
        } else {
            return Observable.just(cameraCaptureSession);
        }
    }


    private void initHandler() {
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        nonMainHandler = new Handler(handlerThread.getLooper(), new CameraHandlerCallback());
    }

    private void capture(final int captureTemplate, final List<Surface> surfaceList) {
        cameraCaptureSession(surfaceList)
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
                                Log.d("zhang", "previewing.");
                                break;
                            case CameraDevice.TEMPLATE_RECORD:
                                Log.d("zhang", "recording.");
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
                                Log.d("zhang", "previewing.");
                                break;
                            case CameraDevice.TEMPLATE_RECORD:
                                Log.d("zhang", "recording.");
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

    private void recordVideo() {
        Observable.intervalRange(0, 10020, 0, 1, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnDispose(new Action() {
                    @Override
                    public void run() throws Exception {
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        ToastHelper.toast(RecordVideoActivity.this, "录制完成");
                        mediaRecorder.stop();
                        newCapture(CameraDevice.TEMPLATE_PREVIEW, list(surfaceHolder.getSurface()));
                    }
                })
                .subscribe(new io.reactivex.Observer<Long>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                        recordDisposable = d;

                        volumeView.showEdge();
                        volumeView.setOrientation(0);

                        //开始录制视频
                        mediaRecorder.start();
                    }

                    @Override
                    public void onNext(Long aLong) {
                        volumeView.setOrientation((int) (360 * aLong.floatValue() / 10000));
                    }

                    @Override
                    public void onError(Throwable e) {
                        recordDisposable = null;
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        ToastHelper.toast(RecordVideoActivity.this, "录制失败");
                        mediaRecorder.stop();
                    }

                    @Override
                    public void onComplete() {
                        recordDisposable = null;
                        volumeView.hideEdge();
                        volumeView.setOrientation(0);
                        ToastHelper.toast(RecordVideoActivity.this, "录制完成");
                        mediaRecorder.stop();
                    }
                });
    }


    private boolean previewQ = true;

    private class SurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            RecordVideoActivity.this.surfaceHolder = holder;
            newCapture(CameraDevice.TEMPLATE_PREVIEW, list(surfaceHolder.getSurface()));
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            RecordVideoActivity.this.surfaceHolder = holder;
            log("surfaceChanged.");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            RecordVideoActivity.this.surfaceHolder = holder;
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        }
    }

    private class SurfaceCallback2 implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            RecordVideoActivity.this.surfaceHolder2 = holder;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            RecordVideoActivity.this.surfaceHolder2 = holder;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            RecordVideoActivity.this.surfaceHolder2 = holder;
            if (cameraDevice != null) {
                cameraDevice.close();
            }
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
            if (previewQ) {
                newCapture(CameraDevice.TEMPLATE_RECORD, list(surfaceHolder.getSurface()));
                previewQ = false;
            } else {
                newCapture(CameraDevice.TEMPLATE_PREVIEW, list(surfaceHolder.getSurface()));
                previewQ = true;
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            recordVideo();
        }
    }

    private class Capture {

        int template;
        List<Surface> surfaceList;

        Capture(int template, List<Surface> surfaceList) {
            this.template = template;
            this.surfaceList = surfaceList;
        }
    }

    private class CameraHandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            Capture capture = (Capture) msg.obj;
            if (capture.surfaceList != null && capture.surfaceList.size() > 0) {
                newCapture(capture.template, capture.surfaceList);
            }
            return true;
        }
    }
}
