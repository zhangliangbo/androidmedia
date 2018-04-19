package com.mcivicm.media.helper;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.MediaRecorder;
import android.os.Environment;

import org.apache.commons.io.FileUtils;

import java.io.File;

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
}
