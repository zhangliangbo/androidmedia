package com.mcivicm.media.camera2;

import android.content.Context;

import com.mcivicm.media.helper.ToastHelper;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * 弹出错误吐司
 */

public class ToastErrorObserver<T> implements Observer<T> {

    private Context context;

    public ToastErrorObserver(Context context) {
        this.context = context;
    }

    @Override
    public void onSubscribe(Disposable d) {

    }

    @Override
    public void onNext(T t) {

    }

    @Override
    public void onError(Throwable e) {
        ToastHelper.toast(context, e.getMessage());
    }

    @Override
    public void onComplete() {

    }
}
