package com.mcivicm.audiosample;

import android.Manifest;
import android.content.Intent;
import android.media.MediaPlayer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.IOException;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

public class MainActivity extends AppCompatActivity {

    private Button recordAudio;

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
                        startRecord();
                        break;
                    case ACTION_UP:
                        stopRecord();
                        try {
                            recordAudio.setText("正在播放录音");
                            playAudio(MediaRecorderHelper.getTempAudioFile());
                        } catch (IOException e) {
                            e.printStackTrace();
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
                                            toast("没有授权，不能录制视频");
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
                                toast("录音初始化失败");
                            }
                        } else {
                            toast("未授权，无法录音");
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        toast(e.getMessage());
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

    private void toast(String msg) {
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }
}
