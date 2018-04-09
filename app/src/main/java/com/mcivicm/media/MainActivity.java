package com.mcivicm.media;

import android.Manifest;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.mcivicm.media.helper.AudioRecordHelper;
import com.mcivicm.media.helper.AudioTrackHelper;
import com.mcivicm.media.helper.MediaPlayerHelper;
import com.mcivicm.media.helper.MediaRecorderHelper;
import com.mcivicm.media.helper.ToastHelper;
import com.mcivicm.media.helper.WebSocketHelper;
import com.tbruyelle.rxpermissions2.RxPermissions;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
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

public class MainActivity extends AppCompatActivity {

    public boolean haveAudioPermission = false;
    public boolean haveStoragePermission = false;

    private Button recordAudio;
    private WebSocket ws = null;
    private AudioTrack audioTrack;

    private Disposable realTimeRecordDisposable = null;
    private PublishSubject<String> publishSubject = PublishSubject.create();

    private RequestType requestType = RequestType.None;//请求类型

    private Recorder omRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recordAudio = findViewById(R.id.record_audio);
        recordAudio.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (haveAudioPermission) {
                            requestType = RequestType.SpeakLock;
                            sendText("speaklock");
                        } else {
                            doIHaveAudioPermission()
                                    .subscribe(new Observer<Boolean>() {
                                        @Override
                                        public void onSubscribe(Disposable d) {

                                        }

                                        @Override
                                        public void onNext(Boolean aBoolean) {
                                            if (aBoolean) {
                                                haveAudioPermission = true;
                                            } else {
                                                haveAudioPermission = false;
                                                new RxPermissions(MainActivity.this)
                                                        .shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                                                        .subscribe(new Observer<Boolean>() {
                                                            @Override
                                                            public void onSubscribe(Disposable d) {

                                                            }

                                                            @Override
                                                            public void onNext(Boolean aBoolean) {
                                                                if (aBoolean) {
                                                                    ToastHelper.toast(MainActivity.this, "您需要授权录音权限才能开始录音");
                                                                } else {
                                                                    ToastHelper.toast(MainActivity.this, "您已拒绝了录音权限并选择不再提醒，如需重新打开录音权限，请在设置->权限里面重新打开");
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
                                        }

                                        @Override
                                        public void onError(Throwable e) {

                                        }

                                        @Override
                                        public void onComplete() {

                                        }
                                    });
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        stopRecordAudioStream();
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
        new RxPermissions(MainActivity.this)
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
        new RxPermissions(MainActivity.this)
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

    private Observable<Boolean> doIHaveAudioPermission() {
        return new RxPermissions(MainActivity.this)
                .request(Manifest.permission.RECORD_AUDIO);
    }

    //开始录制语音文件
    private void startRecordAudioFile() {
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

    //停止录制语音文件
    private void stopRecordAudioFile() {
        MediaRecorderHelper.stop();
        MediaRecorderHelper.reset();
        MediaRecorderHelper.release();
    }

    //播放录制的语音
    private void playAudioFile(File file) throws IOException {
        MediaPlayerHelper.play(
                file.getAbsolutePath(),
                new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        recordAudio.setText("按住录音");
                    }
                },
                new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        recordAudio.setText("按住录音");
                        return false;
                    }
                });
    }

    //上传语音文件
    private boolean uploadAudioFile(File file) {
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
                sendBytes(audioData);
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
            ToastHelper.toast(MainActivity.this, text);
            publishSubject.onNext(text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            super.onMessage(webSocket, bytes);
            playAudioSteamContinuously(bytes.toByteArray());
            ToastHelper.toast(MainActivity.this, "收到：" + bytes.toByteArray().length);
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

    private class MainThreadObserver implements Observer<String> {

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(String s) {
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

    /*
    使用am库录音
     */
    private void startRecordAudioAm() {
        new RxPermissions(MainActivity.this)
                .request(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        if (aBoolean) {
                            File file = new File(Environment.getExternalStorageDirectory(), "om_file.wav");
                            omRecorder = OmRecorder.wav(
                                    new PullTransport.Default(
                                            new PullableSource.Default(
                                                    new AudioRecordConfig.Default(
                                                            MediaRecorder.AudioSource.DEFAULT,
                                                            AudioFormat.ENCODING_PCM_16BIT,
                                                            AudioFormat.CHANNEL_IN_STEREO,
                                                            44100
                                                    )
                                            )
                                    ),
                                    file
                            );
                            recordAudio.setText("正在录音中...");
                            omRecorder.startRecording();
                        } else {
                            ToastHelper.toast(MainActivity.this, "未授权，无法录音");
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

    private void stopRecordAudioAm() {
        if (omRecorder != null) {
            try {
                omRecorder.stopRecording();
            } catch (IOException e) {
                //ignore
            }
        }
    }

    private void startRecordAudioStream() {
        new RxPermissions(MainActivity.this)
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
                .doOnDispose(new Action() {
                    @Override
                    public void run() throws Exception {
                        recordAudio.setText("按住录音");
                    }
                })
                .subscribe(new Observer<byte[]>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        realTimeRecordDisposable = d;
                        recordAudio.setText("正在录音中...");
                    }

                    @Override
                    public void onNext(byte[] bytes) {
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

    private void writePcm(byte[] raw) {
        File file = new File(Environment.getExternalStorageDirectory() + File.separator + "raw16.pcm");
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    return;
                }
            } catch (IOException e) {
                return;
            }
        }
        try {
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write(raw);
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
