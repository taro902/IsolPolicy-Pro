package io.github.mhmrdd.isolationpolicy;

import android.util.Log;

public final class Logger {

    private Logger() {}

    public static void i(String msg) {
        if (BuildConfig.DEBUG) {
            Log.i(Constants.TAG, msg);
        }
    }

    public static void w(String msg) {
        if (BuildConfig.DEBUG) {
            Log.w(Constants.TAG, msg);
        }
    }

    public static void e(String msg, Throwable t) {
        if (BuildConfig.DEBUG) {
            Log.e(Constants.TAG, msg, t);
        }
    }
}