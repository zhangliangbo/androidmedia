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
    public static AudioTrack newInstance(int audioFormat) {
        return new AudioTrack(
                AudioManager.STREAM_MUSIC,
                AudioRecordHelper.sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                audioFormat,
                AudioRecordHelper.minBufferSize(audioFormat),
                AudioTrack.MODE_STREAM
        );
    }
}
