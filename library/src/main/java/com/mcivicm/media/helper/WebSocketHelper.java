package com.mcivicm.media.helper;


import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocketListener;

/**
 * 长连接助手
 */

public class WebSocketHelper {

    private static String url = "ws://192.168.2.65/echo";

    public static void newInstance(WebSocketListener listener) {
        OkHttpClient client = new OkHttpClient
                .Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).build();
        client.newWebSocket(request, listener);
    }

}
