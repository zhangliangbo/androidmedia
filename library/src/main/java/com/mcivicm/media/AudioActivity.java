package com.mcivicm.media;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import com.mcivicm.media.helper.AudioRecordHelper;
import com.mcivicm.media.helper.AudioTrackHelper;
import com.mcivicm.media.helper.MediaRecorderHelper;
import com.mcivicm.media.helper.ToastHelper;
import com.mcivicm.media.helper.WebSocketHelper;
import com.mcivicm.media.view.VolumeView;
import com.tbruyelle.rxpermissions2.RxPermissions;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.subjects.PublishSubject;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import omrecorder.AudioRecordConfig;
import omrecorder.OmRecorder;
import omrecorder.PullTransport;
import omrecorder.PullableSource;
import omrecorder.Recorder;

public class AudioActivity extends AppCompatActivity {

    public boolean haveAudioPermission = false;
    public boolean haveStoragePermission = false;

    private WebSocket ws = null;
    private AudioTrack audioTrack;

    private Disposable realTimeRecordDisposable = null;
    private PublishSubject<Object> publishSubject = PublishSubject.create();

    private RequestType requestType = RequestType.None;//请求类型

    private VolumeView volumeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        volumeView = findViewById(R.id.volume_view);
        volumeView.setEdgeColor(getResources().getColor(R.color.audio_edge_color));
        findViewById(R.id.record_audio).setOnTouchListener(new VolumeViewChildTouchListener());
        requestPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        audioTrack = AudioTrackHelper.createInstance(AudioRecordHelper.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        WebSocketHelper.newInstance(new WSListener((byte[]) null));//长连接服务器
        if (!publishSubject.hasObservers()) {
            publishSubject
                    .observeOn(AndroidSchedulers.mainThread())//传到主线程上
                    .subscribe(new MainThreadObserver());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ws != null) {
            ws.cancel();
            ws = null;
        }
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
        }
    }


    private void requestPermission() {
        new RxPermissions(AudioActivity.this)
                .request(Manifest.permission.RECORD_AUDIO)
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        haveAudioPermission = aBoolean;
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
        new RxPermissions(AudioActivity.this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        haveStoragePermission = aBoolean;
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private class WSListener extends WebSocketListener {

        private byte[] data = null;
        private String text = null;

        WSListener(byte[] data) {
            this.data = data;
        }

        WSListener(String text) {
            this.text = text;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);
            ws = webSocket;//全局赋值
            if (data != null && data.length > 0) {
                webSocket.send(ByteString.of(data, 0, data.length));
            }
            if (text != null && text.length() > 0) {
                webSocket.send(text);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            //这里非主线程，用PublishSubject传到主线程
            ToastHelper.toast(AudioActivity.this, text);
            publishSubject.onNext(text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            super.onMessage(webSocket, bytes);
            playAudioSteamContinuously(bytes.toByteArray());
            ToastHelper.toast(AudioActivity.this, "收到：" + bytes.toByteArray().length);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            super.onClosing(webSocket, code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            super.onClosed(webSocket, code, reason);
            ws = null;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
            super.onFailure(webSocket, t, response);
            ws = null;
        }
    }

    private enum RequestType {
        None, SpeakLock, SpeakEnd
    }

    private class MainThreadObserver implements Observer<Object> {

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(Object o) {
            if (o instanceof String) {
                String s = (String) o;
                switch (s) {
                    case "success":
                        switch (requestType) {
                            case SpeakLock:
                                startRecordAudioStream();
                                break;
                            case SpeakEnd:
                                break;
                            case None:
                                break;
                        }
                        requestType = RequestType.None;//一次请求后复位
                        break;
                }
            } else if (o instanceof Double) {
                Double d = (Double) o;
                volumeView.addWidth(3 * d.intValue());
            }
        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onComplete() {

        }

    }

    //播放数据流
    private void playAudioSteamContinuously(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        audioTrack.play();
        audioTrack.write(data, 0, data.length);
        audioTrack.pause();
    }

    private void startRecordAudioStream() {
        new RxPermissions(AudioActivity.this)
                .request(Manifest.permission.RECORD_AUDIO)
                .flatMap(new Function<Boolean, ObservableSource<byte[]>>() {
                    @Override
                    public ObservableSource<byte[]> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            return AudioRecordHelper.pcmAudioData(
                                    AudioRecordHelper.sampleRate,
                                    AudioFormat.CHANNEL_IN_MONO,
                                    AudioFormat.ENCODING_PCM_16BIT,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            //结束语音
                                            sendText("speakend");
                                        }
                                    }
                            );
                        } else {
                            throw new Exception("您未授权录音权限，无法录音");
                        }
                    }
                })
                .subscribe(new Observer<byte[]>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        realTimeRecordDisposable = d;
                    }

                    @Override
                    public void onNext(byte[] bytes) {
                        publishSubject.onNext(AudioRecordHelper.calculateVolume(bytes));
                        sendBytes(bytes);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void stopRecordAudioStream() {
        if (realTimeRecordDisposable != null && !realTimeRecordDisposable.isDisposed()) {
            realTimeRecordDisposable.dispose();
        }
    }

    private void sendText(String text) {
        if (text == null || text.length() == 0) return;
        if (ws == null) {
            WebSocketHelper.newInstance(new WSListener(text));
        } else {
            ws.send(text);
        }
    }

    private void sendBytes(byte[] data) {
        if (data == null || data.length == 0) return;
        if (ws == null) {
            WebSocketHelper.newInstance(new WSListener(data));
        } else {
            ws.send(ByteString.of(data, 0, data.length));
        }
    }

    private class VolumeViewChildTouchListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (haveAudioPermission) {
                        volumeView.showEdge();
                        requestType = RequestType.SpeakLock;
                        sendText("speaklock");
                    } else {
                        AudioRecordHelper.recordAudioPermission(AudioActivity.this)
                                .subscribe(new Observer<Boolean>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onNext(Boolean aBoolean) {
                                        haveAudioPermission = aBoolean;
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        ToastHelper.toast(AudioActivity.this, e.getMessage());
                                    }

                                    @Override
                                    public void onComplete() {

                                    }
                                });
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    stopRecordAudioStream();
                    volumeView.hideEdge();
                    break;
            }
            return true;
        }
    }
}
