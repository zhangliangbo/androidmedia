package com.mcivicm.media;

import android.app.Service;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.constraint.Guideline;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;

import com.mcivicm.media.camera2.CameraDeviceAvailabilityObservable;
import com.mcivicm.media.camera2.CameraDeviceSessionCaptureObservable;
import com.mcivicm.media.camera2.CameraDeviceSessionStateObservable;
import com.mcivicm.media.camera2.CameraDeviceStateObservable;
import com.mcivicm.media.camera2.ToastErrorObserver;
import com.mcivicm.media.helper.CameraOneHelper;
import com.mcivicm.media.helper.ToastHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.nineoldandroids.view.ViewPropertyAnimator.animate;

/**
 * Camera2 Api Demo
 */

public class CameraTwoActivity extends AppCompatActivity {

    private View pictureOperationLayout;
    private Guideline bottomLine;
    private GestureDetector gestureDetector;

    private CameraManager cameraManager = null;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;

    //数据接收器
    private SurfaceHolder surfaceHolder;//存放预览数据
    private ImageReader imageReader;//存放图片数据
    //数据发射器
    private PublishSubject<Object> mainSubject = PublishSubject.create();
    private PublishSubject<ImageReader> ioSubject = PublishSubject.create();
    //摄像头操作线程
    private Handler nonMainHandler = null;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initHandler();
        initSubject();
        setContentView(R.layout.activity_camera_two);
        pictureOperationLayout = findViewById(R.id.picture_operation_layout);
        pictureOperationLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                togglePictureOperation(false, 0);
                pictureOperationLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        bottomLine = findViewById(R.id.bottom_line);
        cameraManager = (CameraManager) getSystemService(Service.CAMERA_SERVICE);
        final SurfaceView surfaceView = findViewById(R.id.surface_view);
        surfaceView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                imageReader = newImageReader(surfaceView.getWidth(), surfaceView.getHeight());
                surfaceView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        surfaceView.getHolder().addCallback(new Callback());
        surfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPreview(surfaceHolder.getSurface());
                togglePictureOperation(false, 150);
            }
        });
        gestureDetector = new GestureDetector(this, new GestureListener());
        findViewById(R.id.record_button).setOnTouchListener(new RecordTouchListener());
        findViewById(R.id.picture_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPreview(surfaceHolder.getSurface());
                togglePictureOperation(false, 150);
            }
        });
        findViewById(R.id.picture_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPreview(surfaceHolder.getSurface());
                togglePictureOperation(false, 150);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    private ImageReader newImageReader(int width, int height) {
        ImageReader imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        imageReader.setOnImageAvailableListener(new ImageAvailable(), nonMainHandler);
        return imageReader;
    }

    private void stopRepeating() {
        if (cameraCaptureSession != null) {
            try {
                cameraCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                ToastHelper.toast(CameraTwoActivity.this, e.getMessage());
            }
        }
    }

    //打开摄像头会话
    private Observable<CameraCaptureSession> session() {
        return CameraOneHelper.cameraPermission(CameraTwoActivity.this)
                .flatMap(new Function<Boolean, ObservableSource<String>>() {
                    @Override
                    public ObservableSource<String> apply(Boolean aBoolean) throws Exception {
                        return new CameraDeviceAvailabilityObservable(cameraManager);
                    }
                })
                .flatMap(new Function<String, ObservableSource<CameraDevice>>() {
                    @Override
                    public ObservableSource<CameraDevice> apply(String s) throws Exception {
                        return new CameraDeviceStateObservable(cameraManager, s, nonMainHandler);
                    }
                })
                .flatMap(new Function<CameraDevice, ObservableSource<CameraCaptureSession>>() {
                    @Override
                    public ObservableSource<CameraCaptureSession> apply(CameraDevice cameraDevice) throws Exception {
                        CameraTwoActivity.this.cameraDevice = cameraDevice;
                        return new CameraDeviceSessionStateObservable(cameraDevice, toList(surfaceHolder.getSurface(), imageReader.getSurface()), nonMainHandler);//往会话中加入两个Surface
                    }
                });
    }

    //开始预览
    private void startPreview(final Surface... surfaces) {
        if (this.cameraCaptureSession == null) {
            session()
                    .flatMap(new Function<CameraCaptureSession, ObservableSource<Pair<Integer, ? extends CameraMetadata>>>() {
                        @Override
                        public ObservableSource<Pair<Integer, ? extends CameraMetadata>> apply(CameraCaptureSession cameraCaptureSession) throws Exception {
                            CameraTwoActivity.this.cameraCaptureSession = cameraCaptureSession;
                            return new CameraDeviceSessionCaptureObservable(cameraCaptureSession, CameraDevice.TEMPLATE_PREVIEW, toList(surfaces), nonMainHandler);
                        }
                    })
                    .subscribe(new CaptureObserver());
        } else {
            new CameraDeviceSessionCaptureObservable(cameraCaptureSession, CameraDevice.TEMPLATE_PREVIEW, toList(surfaces), nonMainHandler)
                    .subscribe(new CaptureObserver());
        }
    }

    //开始采集图片
    private void startStillCapture(final Surface... surfaces) {
        if (this.cameraCaptureSession == null) {
            session()
                    .flatMap(new Function<CameraCaptureSession, ObservableSource<Pair<Integer, ? extends CameraMetadata>>>() {
                        @Override
                        public ObservableSource<Pair<Integer, ? extends CameraMetadata>> apply(CameraCaptureSession cameraCaptureSession) throws Exception {
                            CameraTwoActivity.this.cameraCaptureSession = cameraCaptureSession;
                            return new CameraDeviceSessionCaptureObservable(cameraCaptureSession, CameraDevice.TEMPLATE_STILL_CAPTURE, toList(surfaces), nonMainHandler);
                        }
                    })
                    .subscribe(new CaptureObserver());
        } else {
            new CameraDeviceSessionCaptureObservable(cameraCaptureSession, CameraDevice.TEMPLATE_STILL_CAPTURE, toList(surfaces), nonMainHandler)
                    .subscribe(new CaptureObserver());
        }
    }

    //显示和隐藏图片操作栏
    private void togglePictureOperation(boolean show, long duration) {
        if (show) {
            animate(pictureOperationLayout).y(bottomLine.getBottom() - pictureOperationLayout.getHeight()).setDuration(duration).start();
        } else {
            animate(pictureOperationLayout).y(bottomLine.getBottom()).setDuration(duration).start();
        }
    }

    private void log(String s) {
        Log.d("zhang", s);
    }

    private List<Surface> toList(Surface... surfaces) {
        List<Surface> list = new ArrayList<>();
        if (surfaces != null && surfaces.length > 0) {
            Collections.addAll(list, surfaces);
        }
        return list;
    }

    private void initHandler() {
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        nonMainHandler = new Handler(handlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                return false;
            }
        });
    }

    private void initSubject() {
        if (!mainSubject.hasObservers()) {
            mainSubject
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new MainThreadObserver());
        }
        if (!ioSubject.hasObservers()) {
            ioSubject
                    .observeOn(Schedulers.io())
                    .subscribe(new IoThreadObserver());
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            startStillCapture(imageReader.getSurface());
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);
        }
    }


    private class RecordTouchListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            gestureDetector.onTouchEvent(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    break;
            }
            return true;
        }
    }

    private enum Notification {
        HidePictureOperation, ShowPictureOperation
    }

    //Io线程观察者
    private class IoThreadObserver implements Observer<ImageReader> {

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(ImageReader imageReader) {
            Image image = imageReader.acquireNextImage();
            switch (image.getFormat()) {
                case ImageFormat.JPEG:
                    if (image.getPlanes() != null && image.getPlanes().length > 0) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        Bitmap rotate = rotateBitmap(bitmap, 90);
                        try {
                            FileOutputStream fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "camera2_temp_image.jpeg"));
                            rotate.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                            fos.close();
                            rotate.recycle();
                        } catch (IOException e) {
                            ToastHelper.toast(CameraTwoActivity.this, e.getMessage());
                        }
                    }
                    break;
            }
            image.close();//记得close()，为下一次做准备
        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onComplete() {

        }

        Bitmap rotateBitmap(Bitmap bitmap, int degree) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degree);
            Bitmap rotate = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return rotate;
        }
    }

    //主线程观察者
    private class MainThreadObserver implements Observer<Object> {

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(Object o) {
            if (o instanceof Notification) {
                switch ((Notification) o) {
                    case HidePictureOperation:
                        startPreview(surfaceHolder.getSurface());
                        togglePictureOperation(false, 150);
                        break;
                    case ShowPictureOperation:
                        togglePictureOperation(true, 150);
                        break;
                }
            }
        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onComplete() {

        }
    }

    private class CaptureObserver implements Observer<Pair<Integer, ? extends CameraMetadata>> {

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(Pair<Integer, ? extends CameraMetadata> captureRequestPair) {
            int template = captureRequestPair.first;
            switch (template) {
                case CameraDevice.TEMPLATE_PREVIEW:

                    break;
                case CameraDevice.TEMPLATE_STILL_CAPTURE:

                    break;
                case CameraDevice.TEMPLATE_RECORD:

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

    private class ImageAvailable implements ImageReader.OnImageAvailableListener {

        @Override
        public void onImageAvailable(ImageReader reader) {
            stopRepeating();
            mainSubject.onNext(Notification.ShowPictureOperation);
            ioSubject.onNext(reader);
        }

    }

    private class Callback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            surfaceHolder = holder;
            startPreview(holder.getSurface());
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surfaceHolder = holder;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            surfaceHolder = holder;
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
            }
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        }
    }
}
