package com.mcivicm.audiosample;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;

import com.tbruyelle.rxpermissions2.RxPermissions;

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
     * pcm8bit实时语音数据
     *
     * @return
     */
    public static Observable<byte[]> pcm8BitAudioData() {
        return new PCM8BitAudioSource(newInstance(AudioFormat.ENCODING_PCM_8BIT));
    }

    /**
     * pcm16bit实时语音数据
     *
     * @return
     */
    public static Observable<short[]> pcm16BitAudioData() {
        return new PCM16BitAudioSource(newInstance(AudioFormat.ENCODING_PCM_16BIT));
    }


    //最小的缓存大小
    public static int minBufferSize(int audioFormat) {
        return AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, audioFormat);//8位对应字节输出流，16位对应短整型输出流
    }

    //新建一个实例
    private static AudioRecord newInstance(int audioFormat) {
        AudioRecord audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                sampleRate,//适用于所有设备
                AudioFormat.CHANNEL_IN_STEREO,
                audioFormat,
                2 * minBufferSize(audioFormat)//两倍的缓冲
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

    private static class PCM16BitAudioSource extends Observable<short[]> {

        private AudioRecord audioRecord;

        PCM16BitAudioSource(AudioRecord audioRecord) {
            this.audioRecord = audioRecord;
        }

        @Override
        protected void subscribeActual(Observer<? super short[]> observer) {
            observer.onSubscribe(new Poll16Bit(audioRecord, observer));
        }
    }

    private static class PCM8BitAudioSource extends Observable<byte[]> {

        private AudioRecord audioRecord;

        PCM8BitAudioSource(AudioRecord audioRecord) {
            this.audioRecord = audioRecord;
        }

        @Override
        protected void subscribeActual(Observer<? super byte[]> observer) {
            observer.onSubscribe(new Poll8Bit(audioRecord, observer));
        }
    }

    private static class Poll16Bit implements Runnable, Disposable {

        ExecutorService service = Executors.newSingleThreadExecutor();
        AudioRecord audioRecord;
        Observer<? super short[]> observer;

        Poll16Bit(AudioRecord audioRecord, Observer<? super short[]> observer) {
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
                //不断轮询抓取数据
                while (!disposed.get()) {
                    short[] buffer = new short[minBufferSize(AudioFormat.ENCODING_PCM_16BIT)];
                    int len = audioRecord.read(buffer, 0, buffer.length);
                    if (len > 0) {
                        short[] useful = new short[len];
                        System.arraycopy(buffer, 0, useful, 0, len);
                        //发送出去
                        observer.onNext(useful);
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

    private static class Poll8Bit implements Runnable, Disposable {

        ExecutorService service = Executors.newSingleThreadExecutor();
        AudioRecord audioRecord;
        Observer<? super byte[]> observer;

        Poll8Bit(AudioRecord audioRecord, Observer<? super byte[]> observer) {
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
                //不断轮询抓取数据
                while (!disposed.get()) {
                    byte[] buffer = new byte[minBufferSize(AudioFormat.ENCODING_PCM_8BIT)];
                    int len = audioRecord.read(buffer, 0, buffer.length);
                    if (len > 0) {
                        byte[] useful = new byte[len];
                        System.arraycopy(buffer, 0, useful, 0, len);
                        //发送出去
                        observer.onNext(useful);
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

