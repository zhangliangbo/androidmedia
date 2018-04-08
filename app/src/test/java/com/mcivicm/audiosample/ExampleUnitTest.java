package com.mcivicm.audiosample;

import android.media.MediaPlayer;

import org.junit.Test;
import org.reactivestreams.Subscription;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.FlowableSubscriber;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Cancellable;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    private class Once implements FlowableOnSubscribe<Integer> {

        int i = 0;
        boolean disposed = false;

        @Override
        public void subscribe(FlowableEmitter<Integer> emitter) throws Exception {
            try {
                while (i < 10) {
                    emitter.onNext(i++);//这里发送的数据无法同步发送给下游，只能等所有的数据发送完调用OnComplete才发送数据到下游
                    Thread.sleep(1000);
                }
                emitter.onComplete();
            } catch (Exception e) {
                emitter.onError(e);
            }
        }
    }

    @Test
    public void onceTest() throws Exception {
        Flowable.create(new Once(), BackpressureStrategy.MISSING)
                .blockingSubscribe(new FlowableSubscriber<Integer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(10);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        System.out.println("receive: " + integer);
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("error: " + t.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        System.out.println("onComplete");
                    }
                });
    }

    @Test
    public void byteShort() throws Exception {
        short[] o = new short[]{5, 4, 3, 1};
        byte[] bytes = new byte[o.length * 2];
        ByteBuffer.wrap(bytes).asShortBuffer().put(o);
        System.out.println("bytes: " + Arrays.toString(bytes));
        short[] d = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).asShortBuffer().get(d);//转化数据
        System.out.println("shorts: " + Arrays.toString(d));

    }

    @Test
    public void assertTest() throws Exception {

    }


    @Test
    public void mp3() throws Exception {

    }

    @Test
    public void pcm2wav() throws Exception {
        File file = new File("C:\\Users\\bdlm2\\Desktop\\raw.pcm");
        if (file.exists()) {
            Converter.pcm2wav(file.getAbsolutePath(),"C:\\Users\\bdlm2\\Desktop\\raw.wav");
        }
    }
}