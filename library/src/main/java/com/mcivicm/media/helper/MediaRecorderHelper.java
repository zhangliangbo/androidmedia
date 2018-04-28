package com.mcivicm.media.helper;

import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 媒体助手
 */

public class MediaRecorderHelper {

    private static File tempVideoFile = new File(Environment.getExternalStorageDirectory() + File.separator + "camera1_temp_video.mp4");

    public static void configureVideoRecorder(MediaRecorder mediaRecorder, int width, int height) {
        //音频和视频数据源
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
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
        mediaRecorder.setOutputFile(tempVideoFile.getAbsolutePath());
    }
}
