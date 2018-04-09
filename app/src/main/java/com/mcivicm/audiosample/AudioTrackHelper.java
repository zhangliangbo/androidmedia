package com.mcivicm.audiosample;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * 音轨助手
 */

public class AudioTrackHelper {
    /**
     * 新建一个播放器实例
     *
     * @return
     */
    public static AudioTrack createInstance(int sampleRate, int channelFormat, int audioFormat) {
        return new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelFormat,
                audioFormat,
                AudioTrack.getMinBufferSize(sampleRate, channelFormat, audioFormat),
                AudioTrack.MODE_STREAM
        );
    }
}
