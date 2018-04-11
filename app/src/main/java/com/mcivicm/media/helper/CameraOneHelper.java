package com.mcivicm.media.helper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.view.Surface;

import com.mcivicm.media.CameraOneActivity;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.Policy;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Function;

/**
 * 摄像头助手
 */

public class CameraOneHelper {
    /**
     * 存储权限
     *
     * @param activity
     * @return
     */
    public static Observable<Boolean> storagePermission(final Activity activity) {
        return new RxPermissions(activity)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .flatMap(new Function<Boolean, ObservableSource<Boolean>>() {
                    @Override
                    public ObservableSource<Boolean> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            return Observable.just(true);
                        } else {
                            return new RxPermissions(activity)
                                    .shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    .flatMap(new Function<Boolean, ObservableSource<Boolean>>() {
                                        @Override
                                        public ObservableSource<Boolean> apply(Boolean aBoolean) throws Exception {
                                            if (aBoolean) {//仅禁止
                                                return Observable.error(new Exception("亲，您需要授权【读写】权限才能打开摄像头哦"));
                                            } else {//禁止并且不再提醒
                                                return Observable.error(new Exception("亲，您拒绝了【读写】权限并且决定不再提醒，如需重新开启【读写】权限，请到【设置】-【权限管理】中手动授权"));
                                            }
                                        }
                                    });
                        }
                    }
                });
    }

    /**
     * 获取摄像头的权限
     *
     * @param activity
     * @return
     */
    public static Observable<Boolean> cameraPermission(final Activity activity) {
        return new RxPermissions(activity)
                .request(Manifest.permission.CAMERA)
                .flatMap(new Function<Boolean, ObservableSource<Boolean>>() {
                    @Override
                    public ObservableSource<Boolean> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            return Observable.just(true);
                        } else {
                            return new RxPermissions(activity)
                                    .shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                                    .flatMap(new Function<Boolean, ObservableSource<Boolean>>() {
                                        @Override
                                        public ObservableSource<Boolean> apply(Boolean aBoolean) throws Exception {
                                            if (aBoolean) {
                                                return Observable.error(new Exception("您需要授权摄像头权限才能开始录音"));
                                            } else {
                                                return Observable.error(new Exception("您已拒绝了摄像头权限并选择不再提醒，如需重新打开摄像头权限，请在设置->权限里面打开"));
                                            }
                                        }
                                    });
                        }
                    }
                });
    }

    /**
     * @return 摄像头的总数
     */
    public static int cameraNumber() {
        return Camera.getNumberOfCameras();
    }

    /**
     * 获取摄像头的信息
     *
     * @param id
     * @return
     */
    public static Camera.CameraInfo getInfo(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        return info;
    }

    /**
     * 打开编号为id的摄像头
     *
     * @param id
     * @return
     */
    public static Camera open(int id) {
        return Camera.open(id);
    }

    /**
     * 设置拍照参数
     *
     * @param camera
     */
    public static void pictureSetting(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        camera.setParameters(parameters);
    }

    /**
     * 设置录像参数
     *
     * @param camera
     */
    public static void videoSetting(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);//连续聚焦
        parameters.setRecordingHint(true);//可能有风险
        if (parameters.isVideoStabilizationSupported()) {
            parameters.setVideoStabilization(true);
        }
        camera.setParameters(parameters);
    }

    /**
     * 设置图片的旋转角度
     *
     * @param camera
     * @param rotation
     */
    public static void setPictureOrientation(Camera camera, int rotation) {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setRotation(rotation);//图片的旋转角度
        camera.setParameters(parameters);
        camera.setDisplayOrientation(rotation);//预览的旋转角度
    }

    /**
     * 最大分辨率
     *
     * @param camera
     */
    public static void maxResolution(Camera camera) {
        Camera.Parameters rawParameters = camera.getParameters();
        //查找最大预览分辨率并设置
        Camera.Size maxPreview = findMaxSize(rawParameters.getSupportedPreviewSizes());
        if (maxPreview != null) {
            rawParameters.setPreviewSize(maxPreview.width, maxPreview.height);
        }
        //查找最大图片分辨率并设置
        Camera.Size maxPicture = findMaxSize(rawParameters.getSupportedPictureSizes());
        if (maxPicture != null) {
            rawParameters.setPictureSize(maxPicture.width, maxPicture.height);
        }
        //最后一定记得重新作用到照相机
        camera.setParameters(rawParameters);
    }

    /**
     * 适配屏幕和相机
     *
     * @param camera
     * @param width
     * @param height
     */
    public static void bestSize(Camera camera, int width, int height) {
        if (width == 0 || height == 0) {//提出杂质
            return;
        }
        Camera.Parameters rawParameters = camera.getParameters();
        //查找最佳预览分辨率并设置
        Camera.Size bestPreviewSize = findBestSize(
                rawParameters.getSupportedPreviewSizes(),
                width,
                height
        );
        if (bestPreviewSize != null) {
            rawParameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
        }
        //查找最佳图片分辨率并设置
        Camera.Size bestPictureSize = findBestSize(
                rawParameters.getSupportedPictureSizes(),
                width,
                height
        );
        if (bestPictureSize != null) {
            rawParameters.setPictureSize(bestPictureSize.width, bestPictureSize.height);
        }
        //最后一定记得重新作用到照相机
        camera.setParameters(rawParameters);
    }

    /**
     * 设置相机显示方向
     *
     * @param activity
     * @param cameraId
     */
    public static int getDisplayOrientation(Activity activity, int cameraId) {
        Camera.CameraInfo info = getInfo(cameraId);
        int rotation = activity
                .getWindowManager()
                .getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    /**
     * 将图片按照某个角度进行旋转
     *
     * @param bm     需要旋转的图片
     * @param degree 旋转角度
     * @return 旋转后的图片
     */
    public static Bitmap rotateBitmapByDegree(Bitmap bm, int degree) {
        Bitmap returnBm = null;
        // 根据旋转角度，生成旋转矩阵
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        try {
            // 将原始图片按照旋转矩阵进行旋转，并得到新的图片
            returnBm = Bitmap.createBitmap(
                    bm,
                    0,
                    0,
                    bm.getWidth(),
                    bm.getHeight(),
                    matrix,
                    true
            );
        } catch (OutOfMemoryError e) {

        }
        if (returnBm == null) {
            returnBm = bm;
        }
        if (bm != returnBm) {
            bm.recycle();
        }
        return returnBm;
    }

    public static void save(Bitmap bitmap, String filePath) {
        File file = new File(filePath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs(); // 创建文件夹
        }
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos); // 向缓冲区之中压缩图片
            bos.flush();
            bos.close();
        } catch (Exception e) {
            //ignore
        }
    }

    private static Camera.Size findMaxSize(List<Camera.Size> list) {
        if (list != null && list.size() > 0) {
            Camera.Size max = list.get(0);
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).width * list.get(i).height > max.width * max.height) {
                    max = list.get(i);
                }
            }
            return max;
        }
        return null;
    }

    private static Camera.Size findBestSize(List<Camera.Size> list, int width, int height) {
        if (list != null && list.size() > 0) {
            //给定的比例
            float ratio = (float) width / (float) height;
            //最佳大小
            Camera.Size minDiffSize = list.get(0);
            float minDiff = Math.abs((float) minDiffSize.width / (float) minDiffSize.height - ratio);
            for (int i = 1; i < list.size(); i++) {
                float tempDiff = Math.abs((float) list.get(i).width / (float) list.get(i).height - ratio);
                if (tempDiff < minDiff) {
                    minDiff = tempDiff;
                    minDiffSize = list.get(i);
                }
            }
            return minDiffSize;
        }
        return null;
    }


}