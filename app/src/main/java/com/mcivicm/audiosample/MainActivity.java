package com.mcivicm.audiosample;

import android.Manifest;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.tbruyelle.rxpermissions2.RxPermissions;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Function;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

public class MainActivity extends AppCompatActivity {

    private Button recordAudio;
    private WebSocket ws = null;
    AudioTrack audioTrack = AudioTrackHelper.newInstance(AudioFormat.ENCODING_PCM_16BIT);
    Disposable disposable = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recordAudio = findViewById(R.id.record_audio);
        recordAudio.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case ACTION_DOWN:
                        new RxPermissions(MainActivity.this)
                                .request(Manifest.permission.RECORD_AUDIO)
                                .flatMap(new Function<Boolean, ObservableSource<short[]>>() {
                                    @Override
                                    public ObservableSource<short[]> apply(Boolean aBoolean) throws Exception {
                                        if (aBoolean) {
                                            return AudioRecordHelper.pcm16BitAudioData();
                                        } else {
                                            throw new Exception("未授权，无法录音");
                                        }
                                    }
                                })
                                .observeOn(AndroidSchedulers.mainThread())
                                .doOnDispose(new Action() {
                                    @Override
                                    public void run() throws Exception {
                                        recordAudio.setText("长按开始录音");
                                    }
                                })
                                .subscribe(new Observer<short[]>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {
                                        disposable = d;
                                        recordAudio.setText("正在录音中...");
                                    }

                                    @Override
                                    public void onNext(short[] shorts) {
                                        byte[] data = new byte[shorts.length * 2];
                                        ByteBuffer.wrap(data).asShortBuffer().put(shorts);//转化并填充数据
                                        if (ws == null) {
                                            WebSocketHelper.newInstance(new WSListener(data));
                                        } else {
                                            ws.send(ByteString.of(data, 0, data.length));
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }

                                    @Override
                                    public void onComplete() {

                                    }
                                });
                        break;
                    case ACTION_UP:
                        if (disposable != null) {
                            disposable.dispose();
                        }
                        break;
                }
                return true;
            }
        });

        findViewById(R.id.record_video)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new RxPermissions(MainActivity.this)
                                .request(Manifest.permission.CAMERA)
                                .subscribe(new Observer<Boolean>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onNext(Boolean aBoolean) {
                                        if (aBoolean) {
                                            startActivity(new Intent(MainActivity.this, CameraOneActivity.class));
                                        } else {
                                            ToastHelper.toast(MainActivity.this, "没有授权，不能录制视频");
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }

                                    @Override
                                    public void onComplete() {

                                    }
                                });
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        WebSocketHelper.newInstance(new WSListener((byte[]) null));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ws != null) {
            ws.cancel();
            ws = null;
        }
    }

    private void startRecord() {
        new RxPermissions(this)
                .request(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        if (aBoolean) {
                            if (MediaRecorderHelper.prepare()) {
                                recordAudio.setText("正在录音中...");
                                MediaRecorderHelper.start();
                            } else {
                                ToastHelper.toast(MainActivity.this, "录音初始化失败");
                            }
                        } else {
                            ToastHelper.toast(MainActivity.this, "未授权，无法录音");
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        ToastHelper.toast(MainActivity.this, e.getMessage());
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void stopRecord() {
        MediaRecorderHelper.stop();
        MediaRecorderHelper.reset();
        MediaRecorderHelper.release();
    }

    private boolean sendAudio(File file) {
        if (file.exists()) {
            try {
                FileInputStream fis = new FileInputStream(file);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[512];
                while (true) {
                    int len = fis.read(buffer);
                    if (len == -1) {
                        break;
                    } else {
                        baos.write(buffer, 0, len);
                    }
                }
                byte[] audioData = baos.toByteArray();
                baos.close();
                fis.close();
                if (ws == null) {
                    WebSocketHelper.newInstance(new WSListener(audioData));
                } else {
                    ws.send(ByteString.of(audioData, 0, audioData.length));
                }
                return true;
            } catch (FileNotFoundException e) {
                return false;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
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
            ToastHelper.toast(MainActivity.this, "长连接打开");
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
            ToastHelper.toast(MainActivity.this, "收到文本信息：" + text);
            switch (text) {
                case "success":
                    startRecord();
                    sendAudio(MediaRecorderHelper.getTempAudioFile());
                    break;
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            super.onMessage(webSocket, bytes);
            playSteamContinuously(bytes.toByteArray(), AudioFormat.ENCODING_PCM_16BIT);
            ToastHelper.toast(MainActivity.this, "收到字节数据：" + bytes.toByteArray().length);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            super.onClosing(webSocket, code, reason);
            ToastHelper.toast(MainActivity.this, "长连接正在关闭");
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            super.onClosed(webSocket, code, reason);
            ws = null;
            ToastHelper.toast(MainActivity.this, "长连接已关闭");
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
            super.onFailure(webSocket, t, response);
            ws = null;
            ToastHelper.toast(MainActivity.this, "发送数据失败:" + t.getMessage());
        }
    }

    //播放数据流
    private void playSteamContinuously(byte[] data, int audioFormat) {
        if (data == null || data.length == 0) {
            return;
        }
        audioTrack.play();
        switch (audioFormat) {
            case AudioFormat.ENCODING_PCM_8BIT:
                audioTrack.write(data, 0, data.length);
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
                short[] shorts = new short[data.length / 2];
                ByteBuffer.wrap(data).asShortBuffer().get(shorts);//转码并复制
                audioTrack.write(shorts, 0, shorts.length);
                break;
        }
        audioTrack.pause();
    }

    private void playAudio(File file) throws IOException {
        MediaPlayerHelper.play(
                file.getAbsolutePath(),
                new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        recordAudio.setText("长按进行录音");
                    }
                },
                new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        recordAudio.setText("长按进行录音");
                        return false;
                    }
                });
    }
}
