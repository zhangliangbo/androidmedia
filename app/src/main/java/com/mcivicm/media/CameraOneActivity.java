package com.mcivicm.media;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.mcivicm.media.helper.CameraHelper;
import com.mcivicm.media.helper.ToastHelper;
import com.tbruyelle.rxpermissions2.RxPermissions;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

import static com.nineoldandroids.view.ViewPropertyAnimator.animate;

/**
 * camera1
 */

public class CameraOneActivity extends AppCompatActivity {

    Button start;
    SurfaceView surfaceView;
    TextView information;

    Camera camera;

    ConstraintLayout pictureOperation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_one);
        start = findViewById(R.id.start);
        surfaceView = findViewById(R.id.surface_view);
        information = findViewById(R.id.information);
        pictureOperation = findViewById(R.id.picture_operation_layout);
    }

    @Override
    protected void onStart() {
        super.onStart();
        information.setText(String.valueOf("摄像头总数：" + CameraHelper.cameraNumber()));
        pictureOperation.findViewById(R.id.picture_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePictureOperation(false, 300);
                camera.startPreview();
            }
        });
        pictureOperation.findViewById(R.id.picture_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePictureOperation(false, 300);
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
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new RxPermissions(CameraOneActivity.this)
                        .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        .subscribe(new Observer<Boolean>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(Boolean aBoolean) {
                                if (aBoolean) {
                                    camera.takePicture(new ShutterCallback(), null, new PictureCallback());
                                } else {
                                    new RxPermissions(CameraOneActivity.this)
                                            .shouldShowRequestPermissionRationale(CameraOneActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                            .subscribe(new Observer<Boolean>() {
                                                @Override
                                                public void onSubscribe(Disposable d) {

                                                }

                                                @Override
                                                public void onNext(Boolean aBoolean) {
                                                    if (aBoolean) {//仅禁止
                                                        ToastHelper.toast(CameraOneActivity.this, "亲，您需要授权【读写】权限才能打开摄像头哦");
                                                    } else {//禁止并且不再提醒
                                                        ToastHelper.toast(CameraOneActivity.this, "亲，您拒绝了【读写】权限并且决定不再提醒，如需重新开启【读写】权限，请到【设置】-【权限管理】中手动授权");
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
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        int total = CameraHelper.cameraNumber();
        if (total > 0) {
            if (total == 1) {
                camera = CameraHelper.open(0);
                int orientation = CameraHelper.getDisplayOrientation(CameraOneActivity.this, 0);
                camera.getParameters().set("orientation", orientation);//后续使用
                camera.setDisplayOrientation(orientation);
                surfaceView.getHolder().addCallback(new Callback(camera));
            } else {
                for (int i = 0; i < total; i++) {
                    if (CameraHelper.getInfo(i).facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        camera = CameraHelper.open(i);
                        int orientation = CameraHelper.getDisplayOrientation(CameraOneActivity.this, i);
                        camera.getParameters().set("orientation", orientation);
                        camera.setDisplayOrientation(orientation);
                        surfaceView.getHolder().addCallback(new Callback(camera));
                    }
                }
            }
            //设置基本参数
            CameraHelper.maxSize(camera);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera.stopPreview();
    }

    private class Callback implements SurfaceHolder.Callback {

        private Camera camera;

        Callback(Camera camera) {
            this.camera = camera;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                camera.setPreviewDisplay(holder);
            } catch (Exception e) {
                Toast
                        .makeText(
                                CameraOneActivity.this,
                                "设置图像预览失败:" + e.getMessage(),
                                Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            camera.startPreview();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (holder.getSurface() == null) return;
            try {
                camera.stopPreview();
            } catch (Exception e) {
                //ignore
            }
            try {
                camera.setPreviewDisplay(holder);
            } catch (Exception e) {
                Toast
                        .makeText(
                                CameraOneActivity.this,
                                "设置图像预览失败",
                                Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            camera.startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // 当Surface被销毁的时候，该方法被调用
            //在这里需要释放Camera资源
            try {
                holder.removeCallback(this);//移除监听，否则holder肯能会持有很多个回调，导致代码反复回调
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {
                //ignore
            }
        }
    }

    private class ShutterCallback implements Camera.ShutterCallback {

        @Override
        public void onShutter() {
            Toast.makeText(CameraOneActivity.this, "this is a sound", Toast.LENGTH_SHORT).show();
        }
    }

    private class PictureCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            //data是一个原始的JPEG图像数据，
            //在这里我们可以存储图片，很显然可以采用MediaStore
            //注意保存图片后，再次调用startPreview()回到预览
//            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
//            try {
//                if (imageUri != null) {
//                    OutputStream os = getContentResolver().openOutputStream(imageUri);
//                    if (os != null) {
//                        os.write(data);
//                        os.flush();
//                        os.close();
//                    }
//                }
//            } catch (Exception e) {
//                //ignore
//            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            Bitmap rotation = CameraHelper.rotateBitmapByDegree(bitmap, camera.getParameters().getInt("orientation"));

            togglePictureOperation(true, 300);
        }
    }

    private void togglePictureOperation(boolean show, long duration) {
        if (show) {
            animate(pictureOperation).y(0).setDuration(duration).start();
        } else {
            animate(pictureOperation).y(-pictureOperation.getHeight()).setDuration(duration).start();
        }
    }
}
