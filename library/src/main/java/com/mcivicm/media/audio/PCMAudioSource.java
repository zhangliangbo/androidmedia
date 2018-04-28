package com.mcivicm.media.audio;

import android.media.AudioRecord;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * PCM音频数据源
 */

public class PCMAudioSource extends Observable<byte[]> {

    private AudioRecord audioRecord;
    private Runnable callback;

    public PCMAudioSource(AudioRecord audioRecord, Runnable callback) {
        this.audioRecord = audioRecord;
        this.callback = callback;
    }

    @Override
    protected void subscribeActual(Observer<? super byte[]> observer) {
        observer.onSubscribe(new PollTask(audioRecord, observer, callback));
    }


    private static class PollTask implements Runnable, Disposable {

        ExecutorService service = Executors.newSingleThreadExecutor();
        AudioRecord audioRecord;
        Runnable callback;
        Observer<? super byte[]> observer;

        PollTask(AudioRecord audioRecord, Observer<? super byte[]> observer, Runnable callback) {
            this.audioRecord = audioRecord;
            this.observer = observer;
            this.callback = callback;
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
            //一切都结束后执行的操作
            if (callback != null) {
                callback.run();
            }
        }
    }
}
