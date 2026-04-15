package com.cjx.airplayjavademo;

import android.app.Application;
import android.content.Context;

public class MyApplication extends Application {

    private static Context appContext;

    public static Context getAppContext() {
        return appContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appContext=this;
        // 必须在初始化任何 Airplay/Netty 相关类之前调用
        System.setProperty("io.netty.noNative", "true");
    }
}
