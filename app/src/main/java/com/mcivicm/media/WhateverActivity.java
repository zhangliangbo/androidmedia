package com.mcivicm.media;

import android.Manifest;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.mcivicm.media.helper.ToastHelper;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by bdlm2 on 2018/4/12.
 */

public class WhateverActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whatever);
        findViewById(R.id.delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteFileEndWith("m4a");
            }
        });
    }

    private void deleteFileEndWith(final String suffix) {
        new RxPermissions(WhateverActivity.this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .flatMap(new Function<Boolean, ObservableSource<Boolean>>() {
                    @Override
                    public ObservableSource<Boolean> apply(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            return Observable.fromCallable(new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    File root = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
                                    if (root.isDirectory()) {
                                        for (File one : root.listFiles()) {
                                            if (one.getAbsolutePath().endsWith(suffix)) {
                                                if (!one.delete()) {
                                                    return false;
                                                }
                                            }
                                        }
                                    }
                                    return true;
                                }
                            }).subscribeOn(Schedulers.computation());//computation线程删除要快些
                        } else {
                            return Observable.error(new Exception("您尚未授权，无法删除图片"));
                        }
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        ((Button) findViewById(R.id.delete)).setText("删除开始，请稍候");
                    }

                    @Override
                    public void onNext(Boolean aBoolean) {
                        ToastHelper.toast(WhateverActivity.this, "删除结果：" + aBoolean);
                    }

                    @Override
                    public void onError(Throwable e) {
                        ToastHelper.toast(WhateverActivity.this, "错误：" + e.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        ((Button) findViewById(R.id.delete)).setText("删除完毕");
                    }
                });
    }
}
