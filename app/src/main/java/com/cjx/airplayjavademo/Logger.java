package com.cjx.airplayjavademo;

import android.util.Log;

public class Logger {

    public static void d(String tag, String format, Object... objects) {
        Log.d(tag, String.format(format, objects));
    }

    public static void i(String tag, String format, Object... objects) {
        Log.i(tag, String.format(format, objects));
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void w(String tag, String format, Object... objects) {
        Log.w(tag, String.format(format, objects));
    }


    public static void e(String tag, String format, Object... objects) {
        Log.e(tag, String.format(format, objects));
    }

    public static void e(String tag, String msg, Throwable throwable) {
        Log.e(tag, msg, throwable);
    }


}
