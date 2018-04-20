package com.mcivicm.media;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.observables.ConnectableObservable;

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

    @Test
    public void error() throws Exception {
        Observable
                .fromCallable(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        throw new Exception("some exception.");
                    }
                })
                .subscribe();
    }

    private void logBuffer(ByteBuffer buffer) {
        System.out.println(buffer.capacity() + "-" + buffer.limit() + "-" + buffer.position());
    }

    @Test
    public void byteBuffer() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        logBuffer(buffer);
        buffer.put((byte) 1);
        logBuffer(buffer);
        buffer.put((byte) 2);
        logBuffer(buffer);
        buffer.flip();
        logBuffer(buffer);
    }

    @Test
    public void publish() throws Exception {
        Observable<Long> integerObservable =
                Observable
                        .intervalRange(0, 5, 0, 1, TimeUnit.SECONDS)
                        .flatMap(new Function<Long, ObservableSource<Long>>() {
                            @Override
                            public ObservableSource<Long> apply(Long aLong) throws Exception {
                                return Observable.just(-aLong);
                            }
                        })
                        .replay()
                        .refCount();
        integerObservable
                .blockingSubscribe(new Observer<Long>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Long integer) {
                        System.out.println("first: " + integer);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
        integerObservable
                .blockingSubscribe(new Observer<Long>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Long integer) {
                        System.out.println("second: " + integer);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }
}