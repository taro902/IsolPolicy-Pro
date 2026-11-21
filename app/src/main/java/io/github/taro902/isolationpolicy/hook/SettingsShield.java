package io.github.taro902.isolationpolicy.hook;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.core.Logger;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class SettingsShield {

    private static final String TAG = "SettingsShield";
    private static final String SENTINEL_NULL = "\u0000NULL";

    private static final Map<String, String> GLOBAL_DEV_SPOOF = new HashMap<>();
    private static final Map<String, String> SECURE_DEV_SPOOF = new HashMap<>();
    private static final Map<String, String> SECURE_A11Y_SPOOF = new HashMap<>();

    static {
        GLOBAL_DEV_SPOOF.put("development_settings_enabled", "0");
        GLOBAL_DEV_SPOOF.put("adb_enabled", "0");
        GLOBAL_DEV_SPOOF.put("adb_wifi_enabled", "0");
        GLOBAL_DEV_SPOOF.put("wait_for_debugger", "0");
        GLOBAL_DEV_SPOOF.put("stay_on_while_plugged_in", "0");
        GLOBAL_DEV_SPOOF.put("verifier_verify_adb_installs", "1");
        GLOBAL_DEV_SPOOF.put("debug_app", SENTINEL_NULL);

        SECURE_DEV_SPOOF.put("mock_location", "0");
        SECURE_DEV_SPOOF.put("install_non_market_apps", "0");

        SECURE_A11Y_SPOOF.put("accessibility_enabled", "0");
        SECURE_A11Y_SPOOF.put("enabled_accessibility_services", "");
        SECURE_A11Y_SPOOF.put("touch_exploration_enabled", "0");
        SECURE_A11Y_SPOOF.put("accessibility_display_magnification_enabled", "0");
        SECURE_A11Y_SPOOF.put("accessibility_autoclick_enabled", "0");
    }

    private final AtomicBoolean attached = new AtomicBoolean(false);

    public void attachIfNeeded(
            XposedModule module,
            ClassLoader cl,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        if (attached.get() || cl == null) {
            return;
        }
        Class<?> providerCls;
        try {
            providerCls = Class.forName("com.android.providers.settings.SettingsProvider", false, cl);
        } catch (Throwable ignored) {
            return;
        }

        if (!attached.compareAndSet(false, true)) {
            return;
        }

        int callHooked = 0;
        int queryHooked = 0;

        for (Method m : providerCls.getDeclaredMethods()) {
            String name = m.getName();
            if ("call".equals(name)) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == String.class && p[1] == String.class && p[2] == Bundle.class) {
                    if (hookCall(module, m, 0, 1, 2, engine, telemetry)) {
                        callHooked++;
                    }
                } else if (p.length == 4 && p[0] == String.class && p[1] == String.class && p[2] == String.class && p[3] == Bundle.class) {
                    if (hookCall(module, m, 1, 2, 3, engine, telemetry)) {
                        callHooked++;
                    }
                }
            } else if ("query".equals(name)) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length >= 5 && p[0] == Uri.class && p[1] == String[].class && p[2] == String.class && p[3] == String[].class) {
                    if (hookQuery(module, m, engine, telemetry)) {
                        queryHooked++;
                    }
                }
            }
        }

        if (callHooked == 0) {
            attached.set(false);
            telemetry.markHookState(PolicyFlags.HOOK_BIT_SETTINGS, TelemetryHub.STATE_FAILED);
        } else if (queryHooked > 0) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_SETTINGS, TelemetryHub.STATE_OK);
        } else {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_SETTINGS, TelemetryHub.STATE_FALLBACK);
        }
    }

    private boolean hookCall(
            XposedModule module,
            Method m,
            int idxMethod,
            int idxArg,
            int idxExtras,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        try {
            module.deoptimize(m);
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        String method = (idxMethod < args.size() && args.get(idxMethod) instanceof String)
                                ? (String) args.get(idxMethod)
                                : null;
                        if (method == null) {
                            return chain.proceed();
                        }

                        int callingUid = Binder.getCallingUid();

                        if (PolicyFlags.IPC_METHOD.equals(method)) {
                            if (!engine.isVerifiedSelfUid(callingUid)) {
                                return chain.proceed();
                            }
                            String cmd = (idxArg < args.size() && args.get(idxArg) instanceof String)
                                    ? (String) args.get(idxArg)
                                    : null;
                            if (cmd == null && idxExtras < args.size() && args.get(idxExtras) instanceof Bundle) {
                                cmd = ((Bundle) args.get(idxExtras)).getString(PolicyFlags.IPC_ARG_CMD);
                            }
                            return handleAuthenticatedIpc(cmd, engine, telemetry);
                        }

                        int flags = engine.resolveFlagsForUid(callingUid);
                        if ((flags & (PolicyFlags.FLAG_DEV_COHERENT | PolicyFlags.FLAG_A11Y_COHERENT)) == 0) {
                            return chain.proceed();
                        }

                        String key = (idxArg < args.size() && args.get(idxArg) instanceof String)
                                ? (String) args.get(idxArg)
                                : null;
                        if (key == null) {
                            return chain.proceed();
                        }

                        String spoofed = lookupSpoofValue(method, key, flags);
                        if (spoofed != null) {
                            int appId = engine.resolveEffectiveAppId(callingUid);
                            int vec = "GET_global".equals(method)
                                    ? PolicyFlags.VEC_SET_GLOBAL_CALL
                                    : PolicyFlags.VEC_SET_SECURE_CALL;
                            telemetry.record(appId, PolicyFlags.PILLAR_SETTINGS, vec, PolicyFlags.ACT_SPOOFED);
                            Bundle out = new Bundle(1);
                            out.putString("value", SENTINEL_NULL.equals(spoofed) ? null : spoofed);
                            return out;
                        }
                        return chain.proceed();
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook call failed", t);
            return false;
        }
    }

    private boolean hookQuery(
            XposedModule module,
            Method m,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        try {
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int callingUid = Binder.getCallingUid();
                        int flags = engine.resolveFlagsForUid(callingUid);
                        if ((flags & (PolicyFlags.FLAG_DEV_COHERENT | PolicyFlags.FLAG_A11Y_COHERENT)) == 0) {
                            return chain.proceed();
                        }

                        List<Object> args = chain.getArgs();
                        Uri uri = args.get(0) instanceof Uri ? (Uri) args.get(0) : null;
                        String table = extractTable(uri);
                        if (table == null) {
                            return chain.proceed();
                        }

                        Object res = chain.proceed();
                        if (!(res instanceof Cursor)) {
                            return res;
                        }
                        Cursor cursor = (Cursor) res;
                        if (cursor.getCount() == 0) {
                            return cursor;
                        }

                        String selection = args.get(2) instanceof String ? (String) args.get(2) : null;
                        String[] selectionArgs = args.get(3) instanceof String[] ? (String[]) args.get(3) : null;
                        String inferredKey = inferSingleKey(uri, selection, selectionArgs);

                        Cursor sanitized = sanitizeCursorIfNeeded(cursor, table, inferredKey, flags);
                        if (sanitized != cursor) {
                            int appId = engine.resolveEffectiveAppId(callingUid);
                            telemetry.record(appId, PolicyFlags.PILLAR_SETTINGS, PolicyFlags.VEC_SET_QUERY, PolicyFlags.ACT_SANITIZED);
                            return sanitized;
                        }
                        return cursor;
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook query failed", t);
            return false;
        }
    }

    private Bundle handleAuthenticatedIpc(String cmd, PolicyEngine engine, TelemetryHub telemetry) {
        if (PolicyFlags.IPC_CMD_PURGE.equals(cmd)) {
            telemetry.purge();
            Bundle ok = new Bundle(1);
            ok.putBoolean("ok", true);
            return ok;
        }
        if (PolicyFlags.IPC_CMD_PING.equals(cmd)) {
            Bundle pong = new Bundle(4);
            pong.putBoolean("ok", true);
            pong.putInt("health", telemetry.getHookHealthMask());
            pong.putInt("api", engine.getApiVersion());
            pong.putString("ver", PolicyFlags.VERSION_NAME);
            return pong;
        }
        return telemetry.buildSnapshotBundle(engine);
    }

    private static String lookupSpoofValue(String methodOrTable, String key, int flags) {
        if ("GET_global".equals(methodOrTable) || "global".equals(methodOrTable)) {
            if (PolicyFlags.has(flags, PolicyFlags.FLAG_DEV_COHERENT)) {
                return GLOBAL_DEV_SPOOF.get(key);
            }
            return null;
        }
        if ("GET_secure".equals(methodOrTable) || "secure".equals(methodOrTable)) {
            if (PolicyFlags.has(flags, PolicyFlags.FLAG_A11Y_COHERENT)) {
                String v = SECURE_A11Y_SPOOF.get(key);
                if (v != null) {
                    return v;
                }
            }
            if (PolicyFlags.has(flags, PolicyFlags.FLAG_DEV_COHERENT)) {
                return SECURE_DEV_SPOOF.get(key);
            }
        }
        return null;
    }

    private static String extractTable(Uri uri) {
        if (uri == null) {
            return null;
        }
        List<String> segs = uri.getPathSegments();
        if (segs == null || segs.isEmpty()) {
            return null;
        }
        String first = segs.get(0);
        if ("global".equals(first) || "secure".equals(first)) {
            return first;
        }
        return null;
    }

    private static String inferSingleKey(Uri uri, String selection, String[] selectionArgs) {
        if (uri != null) {
            List<String> segs = uri.getPathSegments();
            if (segs != null && segs.size() >= 2) {
                return segs.get(1);
            }
        }
        if (selection != null && selection.contains("name") && selectionArgs != null && selectionArgs.length == 1) {
            return selectionArgs[0];
        }
        return null;
    }

    private static Cursor sanitizeCursorIfNeeded(Cursor cursor, String table, String inferredKey, int flags) {
        String[] cols = cursor.getColumnNames();
        if (cols == null || cols.length == 0) {
            return cursor;
        }
        int nameIdx = -1;
        int valueIdx = -1;
        for (int i = 0; i < cols.length; i++) {
            if ("name".equals(cols[i])) {
                nameIdx = i;
            } else if ("value".equals(cols[i])) {
                valueIdx = i;
            }
        }
        if (valueIdx < 0) {
            return cursor;
        }
        if (nameIdx < 0 && inferredKey == null) {
            return cursor;
        }

        boolean needsRewrite = false;
        if (cursor.moveToFirst()) {
            do {
                String k = nameIdx >= 0 ? cursor.getString(nameIdx) : inferredKey;
                if (k != null && lookupSpoofValue(table, k, flags) != null) {
                    needsRewrite = true;
                    break;
                }
            } while (cursor.moveToNext());
        }

        if (!needsRewrite) {
            cursor.moveToPosition(-1);
            return cursor;
        }

        MatrixCursor out = new MatrixCursor(cols, cursor.getCount());
        Object[] row = new Object[cols.length];
        if (cursor.moveToFirst()) {
            do {
                String k = nameIdx >= 0 ? cursor.getString(nameIdx) : inferredKey;
                String spoof = k != null ? lookupSpoofValue(table, k, flags) : null;
                for (int i = 0; i < cols.length; i++) {
                    if (i == valueIdx && spoof != null) {
                        row[i] = SENTINEL_NULL.equals(spoof) ? null : spoof;
                    } else {
                        row[i] = readCursorCell(cursor, i);
                    }
                }
                out.addRow(row);
            } while (cursor.moveToNext());
        }
        try {
            cursor.close();
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Object readCursorCell(Cursor c, int idx) {
        switch (c.getType(idx)) {
            case Cursor.FIELD_TYPE_NULL:
                return null;
            case Cursor.FIELD_TYPE_INTEGER:
                return c.getLong(idx);
            case Cursor.FIELD_TYPE_FLOAT:
                return c.getDouble(idx);
            case Cursor.FIELD_TYPE_BLOB:
                return c.getBlob(idx);
            case Cursor.FIELD_TYPE_STRING:
            default:
                return c.getString(idx);
        }
    }
}