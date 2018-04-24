package com.mcivicm.media.helper;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.util.Size;

import com.mcivicm.media.camera2.googlevideo.Camera2VideoFragment;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
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
//        mediaRecorder.setVideoSize(width, height);
        //设置记录会话的最大持续时间
        mediaRecorder.setMaxDuration(10 * 1000);
        //设置输出文件
        mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory() + File.separator + "camera2_temp_video.mp4");
    }


    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    public static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    public static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
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
