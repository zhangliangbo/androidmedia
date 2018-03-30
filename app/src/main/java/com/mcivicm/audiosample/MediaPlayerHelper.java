package com.mcivicm.audiosample;

import android.media.MediaPlayer;

import java.io.IOException;

/**
 * 媒体播放器
 */

public class MediaPlayerHelper {

    private static MediaPlayer mediaPlayer;
    private static boolean isRelease = false;

    private static MediaPlayer getInstance() {
        if (mediaPlayer == null || isRelease) {
            mediaPlayer = new MediaPlayer();
            isRelease = false;
        }
        return mediaPlayer;
    }

    /**
     * 播放一个音频文件
     *
     * @param path
     * @param completionListener
     * @param onErrorListener
     * @return
     */
    public static boolean play(String path, final MediaPlayer.OnCompletionListener completionListener, final MediaPlayer.OnErrorListener onErrorListener) {
        try {
            setDataSource(path);//第一步
            getInstance().setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stop();
                    reset();
                    release();
                    if (completionListener != null) {
                        completionListener.onCompletion(mp);
                    }
                }
            });
            getInstance().setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    stop();
                    reset();
                    release();
                    return onErrorListener == null || onErrorListener.onError(mp, what, extra);
                }
            });
            getInstance().setVolume(1, 1);
            getInstance().setLooping(false);
            prepare();//第二步
            start();//第三步
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * 设置数据源
     *
     * @param path
     * @return
     */
    public static boolean setDataSource(String path) {
        try {
            getInstance().setDataSource(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    /**
     * 准备播放
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
     * 开始播放
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

    /**
     * 停顿播放
     *
     * @return
     */
    public static boolean pause() {
        try {
            getInstance().pause();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 停止播放
     *
     * @return
     */
    public static boolean stop() {
        try {
            getInstance().stop();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 重置播放器
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
     * 释放播放器
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

}
