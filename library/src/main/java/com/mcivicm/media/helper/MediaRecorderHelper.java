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

    private static MediaRecorder mediaRecorder = null;
    private static File tempAudioFile = new File(Environment.getExternalStorageDirectory() + File.separator + "temp_audio.aac");//why .m4a? M4A是MPEG-4 音频标准的文件的扩展名。;
    private static File tempVideoFile = new File(Environment.getExternalStorageDirectory() + File.separator + "temp_video.mp4");
    private static boolean isRelease = false;

    private static MediaRecorder getInstance() throws IOException {
        if (mediaRecorder == null || isRelease) {
            mediaRecorder = new MediaRecorder();
            isRelease = false;
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);//输入
            mediaRecorder.setAudioSamplingRate(44100);//采样率
            mediaRecorder.setAudioEncodingBitRate(96000);//编码比特率
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//输出格式
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//编码器
            if (tempAudioFile.exists() || tempAudioFile.createNewFile()) {
                mediaRecorder.setOutputFile(tempAudioFile.getAbsolutePath());//输出文件
            }
        }
        return mediaRecorder;
    }

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

    /**
     * 准备录音
     *
     * @return
     */
    public static boolean prepare() {
        try {
            getInstance().prepare();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Recording is now started
     *
     * @return
     */
    public static boolean start() {
        try {
            getInstance().start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean resume() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                getInstance().resume();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean pause() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                getInstance().pause();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean stop() {
        try {
            getInstance().stop();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * You can reuse the object by going back to setAudioSource() step
     *
     * @return
     */
    public static boolean reset() {
        try {
            getInstance().reset();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Now the object cannot be reused
     *
     * @return
     */
    public static boolean release() {
        try {
            getInstance().release();
            isRelease = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static File getTempAudioFile() {
        return tempAudioFile;
    }

    /**
     * 发送语音数据
     *
     * @param data
     * @return
     */
    public static File saveAudioData(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        File file = new File(Environment.getExternalStorageDirectory() + File.separator + "receive_audio.aac");
        try {
            if (!file.exists()) {//没有文件
                if (!file.createNewFile()) {//创建文件
                    return null;
                }
            }
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data, 0, data.length);
            fos.close();
            return file;
        } catch (IOException e) {
            return null;
        }
    }
}
