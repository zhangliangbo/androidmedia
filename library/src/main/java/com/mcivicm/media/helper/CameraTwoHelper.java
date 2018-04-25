package com.mcivicm.media.helper;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.mcivicm.media.camera2.googlevideo.Camera2VideoFragment;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION;

/**
 * Camera2 Api 助手
 */

public class CameraTwoHelper {
    /**
     * 获取图片的旋转角
     *
     * @param cameraId
     * @param orientation 手机的方位角，竖屏0，横屏90
     * @return
     */
    public static int getPictureRotation(CameraManager cameraManager, String cameraId, int orientation) throws CameraAccessException {
        CameraCharacteristics cc = cameraManager.getCameraCharacteristics(cameraId);
        orientation = (orientation + 45) / 90 * 90;
        int rotation;
        int facing = cc.get(LENS_FACING);
        int previewOrientation = cc.get(SENSOR_ORIENTATION);
        if (facing == CameraMetadata.LENS_FACING_FRONT) {
            rotation = (previewOrientation - orientation + 360) % 360;
        } else {  // back-facing camera
            rotation = (previewOrientation + orientation) % 360;
        }
        return rotation;
    }

    /**
     * 配置录制视频参数
     *
     * @param mediaRecorder
     * @param width
     * @param height
     */
    public static void configureVideoMediaRecorder(MediaRecorder mediaRecorder, int width, int height) {
        //音频和视频数据源
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        //输出格式，必须在编码格式之前设置
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //视频和音频编码格式
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
        //设置帧率
        mediaRecorder.setVideoFrameRate(30);
        //设置码率
        mediaRecorder.setVideoEncodingBitRate(5 * width * height);
        //设置视频大小
        mediaRecorder.setVideoSize(width, height);
        //设置记录会话的最大持续时间
        mediaRecorder.setMaxDuration(10 * 1000);
        //设置输出文件
        mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory() + File.separator + "camera2_temp_video.mp4");
    }


    /**
     * 选最大的吧，占内存，选小了吧，不够清晰
     * 还是参考视图的大小吧
     *
     * @param choices    所有支持的视频大小
     * @param viewWidth  摄像头传感器为0度时（一般是横屏）的宽度
     * @param viewHeight 摄像头传感器为0度时（一般是横屏）的高度
     * @return
     */
    public static Size chooseVideoSize(Size[] choices, int viewWidth, int viewHeight) {
        List<Size> enoughList = new ArrayList<>();
        for (Size size : choices) {
            if (size.getWidth() >= viewWidth && size.getHeight() >= viewHeight) {
                enoughList.add(size);
            }
        }
        if (enoughList.size() == 0) {
            Arrays.sort(choices, new CompareSizesByArea());
            return choices[choices.length - 1];
        } else if (enoughList.size() == 1) {
            return enoughList.get(0);
        } else {
            return Collections.min(enoughList, new CompareSizesByArea());//合格中的最小者
        }
    }

    /**
     * 选最大的吧，只是预览而已
     *
     * @param choices 所有支持的大小
     * @return
     */
    public static Size chooseMaxSize(Size[] choices) {
        if (choices == null || choices.length == 0) {
            throw new IllegalStateException("choices=null or choices.length=0");
        }
        Arrays.sort(choices, new CompareSizesByArea());
        return choices[choices.length - 1];
    }


    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}
