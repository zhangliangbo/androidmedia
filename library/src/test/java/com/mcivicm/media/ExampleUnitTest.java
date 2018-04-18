package com.mcivicm.media;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.Callable;

import io.reactivex.Observable;

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
}