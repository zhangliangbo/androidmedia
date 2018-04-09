package com.mcivicm.audiosample;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 语音录制助手
 */

public class AudioRecordHelper {

    public static final int sampleRate = 44100;//采样率

    /**
     * pcm实时语音数据
     *
     * @return
     */
    public static Observable<byte[]> pcmAudioData(int sampleRate, int channelFormat, int audioFormat) {
        return new PCMAudioSource(createInstance(sampleRate, channelFormat, audioFormat));
    }

    /**
     * 新建一个实例
     *
     * @param sampleRate
     * @param channelFormat
     * @param audioFormat
     * @return
     */
    public static AudioRecord createInstance(int sampleRate, int channelFormat, int audioFormat) {
        AudioRecord audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                sampleRate,//适用于所有设备
                channelFormat,
                audioFormat,
                AudioRecord.getMinBufferSize(sampleRate, channelFormat, audioFormat)//两倍的缓冲
        );
        //噪声抑制
        NoiseSuppressor noiseSuppressor = NoiseSuppressor.create(audioRecord.getAudioSessionId());
        if (noiseSuppressor != null) {
            if (!noiseSuppressor.getEnabled()) {
                noiseSuppressor.setEnabled(true);
            }
        }
        //回声消除
        AcousticEchoCanceler acousticEchoCanceler = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
        if (acousticEchoCanceler != null) {
            if (!acousticEchoCanceler.getEnabled()) {
                acousticEchoCanceler.setEnabled(true);
            }
        }
        return audioRecord;
    }

    private static class PCMAudioSource extends Observable<byte[]> {

        private AudioRecord audioRecord;

        PCMAudioSource(AudioRecord audioRecord) {
            this.audioRecord = audioRecord;
        }

        @Override
        protected void subscribeActual(Observer<? super byte[]> observer) {
            observer.onSubscribe(new PollTask(audioRecord, observer));
        }
    }

    private static class PollTask implements Runnable, Disposable {

        ExecutorService service = Executors.newSingleThreadExecutor();
        AudioRecord audioRecord;
        Observer<? super byte[]> observer;

        PollTask(AudioRecord audioRecord, Observer<? super byte[]> observer) {
            this.audioRecord = audioRecord;
            this.observer = observer;
            service.execute(this);//开启抓取数据线程
        }

        AtomicBoolean disposed = new AtomicBoolean(false);

        @Override
        public void dispose() {
            disposed.set(true);
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }

        @Override
        public void run() {
            try {
                audioRecord.startRecording();
                byte[] buffer = new byte[AudioRecord.getMinBufferSize(audioRecord.getSampleRate(), audioRecord.getChannelConfiguration(), audioRecord.getAudioFormat())];
                int len;
                //不断轮询抓取数据
                while (!disposed.get()) {
                    len = audioRecord.read(buffer, 0, buffer.length);
                    if (len > 0) {
                        System.arraycopy(buffer, 0, buffer, 0, len);
                        //发送出去
                        observer.onNext(buffer);
                    }
                }
//                observer.onComplete();//无限流数据，没有结束，只能取消
                audioRecord.stop();
                audioRecord.release();
                service.shutdown();
            } catch (Exception e) {
                observer.onError(e);
                audioRecord.stop();
                audioRecord.release();
                service.shutdown();
            }
        }
    }
}

