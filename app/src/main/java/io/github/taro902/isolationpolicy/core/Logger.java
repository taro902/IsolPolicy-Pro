package io.github.taro902.isolationpolicy.core;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.BuildConfig;

public final class Logger {

    private static final String PREFIX = "IsolPolicy/";
    private static volatile XposedInterface sXposed;

    private Logger() {
    }

    public static void bind(XposedInterface xposed) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        sXposed = xposed;
    }

    public static void d(String tag, String msg) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        XposedInterface x = sXposed;
        if (x != null) {
            x.log(Log.DEBUG, PREFIX + tag, msg);
        } else {
            Log.d(PREFIX + tag, msg);
        }
    }

    public static void w(String tag, String msg) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        XposedInterface x = sXposed;
        if (x != null) {
            x.log(Log.WARN, PREFIX + tag, msg);
        } else {
            Log.w(PREFIX + tag, msg);
        }
    }

    public static void e(String tag, String msg, Throwable t) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        XposedInterface x = sXposed;
        if (x != null) {
            if (t != null) {
                x.log(Log.ERROR, PREFIX + tag, msg, t);
            } else {
                x.log(Log.ERROR, PREFIX + tag, msg);
            }
        } else {
            Log.e(PREFIX + tag, msg, t);
        }
    }
}