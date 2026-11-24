package io.github.taro902.isolationpolicy.core;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

public final class Logger {

    public static final String TAG = "IsolPolicy";
    private static volatile XposedInterface moduleRef;

    private Logger() {
    }

    public static void attach(XposedInterface module) {
        moduleRef = module;
    }

    public static void i(String message) {
        if (message == null) {
            return;
        }
        XposedInterface m = moduleRef;
        if (m != null) {
            try {
                m.log(Log.INFO, TAG, message);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
        }
    }

    public static void w(String message) {
        if (message == null) {
            return;
        }
        XposedInterface m = moduleRef;
        if (m != null) {
            try {
                m.log(Log.WARN, TAG, message);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            Log.w(TAG, message);
        } catch (Throwable ignored) {
        }
    }

    public static void e(String message) {
        if (message == null) {
            return;
        }
        XposedInterface m = moduleRef;
        if (m != null) {
            try {
                m.log(Log.ERROR, TAG, message);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            Log.e(TAG, message);
        } catch (Throwable ignored) {
        }
    }

    public static void e(String message, Throwable throwable) {
        String safeMsg = message != null ? message : "error";
        XposedInterface m = moduleRef;
        if (m != null) {
            try {
                m.log(Log.ERROR, TAG, safeMsg, throwable);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            Log.e(TAG, safeMsg, throwable);
        } catch (Throwable ignored) {
        }
    }
}
