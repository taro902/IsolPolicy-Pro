package io.github.taro902.isolationpolicy.hook;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class SettingsShield {

    public static final String UNMODIFIED = new String("ISOL_UNMOD");
    public static final int MAX_BULK_CURSOR_ROWS = 512;

    private static final String KEY_RESULT_LIST = "result_settings_list";
    private static final String KEY_VALUE = "value";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final ThreadLocal<Boolean> IN_CALL = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> IN_QUERY = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> IN_HOST_PROBE = new ThreadLocal<Boolean>();

    private SettingsShield() {
    }

    public static String resolveSpoofedValue(String key, int flags) {
        if (key == null || key.isEmpty()) {
            return UNMODIFIED;
        }
        if (PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DEV_COHERENT)) {
            switch (key) {
                case "development_settings_enabled":
                case "adb_enabled":
                case "adb_wifi_enabled":
                case "stay_on_while_plugged_in":
                case "wait_for_debugger":
                case "usb_mass_storage_enabled":
                case "allow_mock_location":
                case "mock_location":
                case "install_non_market_apps":
                    return "0";
                case "debug_app":
                    return null;
                case "verifier_verify_adb_installs":
                case "package_verifier_enable":
                    return "1";
                default:
                    break;
            }
        }
        if (PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_A11Y_COHERENT)) {
            switch (key) {
                case "accessibility_enabled":
                case "touch_exploration_enabled":
                case "accessibility_display_magnification_enabled":
                case "accessibility_autoclick_enabled":
                    return "0";
                case "enabled_accessibility_services":
                    return "";
                default:
                    break;
            }
        }
        return UNMODIFIED;
    }

    public static boolean isGuardedKeyForFlags(String key, int flags) {
        return resolveSpoofedValue(key, flags) != UNMODIFIED;
    }

    public static boolean isA11ySettingKey(String key) {
        if (key == null) {
            return false;
        }
        return "accessibility_enabled".equals(key)
            || "enabled_accessibility_services".equals(key)
            || "touch_exploration_enabled".equals(key)
            || "accessibility_display_magnification_enabled".equals(key)
            || "accessibility_autoclick_enabled".equals(key);
    }

    public static ArrayList<String> sanitizeSettingsList(List<String> source, int flags, int[] outChanged) {
        if (outChanged != null && outChanged.length > 0) {
            outChanged[0] = 0;
        }
        if (source == null || source.isEmpty()) {
            return null;
        }
        int changed = 0;
        ArrayList<String> rebuilt = null;
        int n = source.size();
        for (int i = 0; i < n; i++) {
            String item = source.get(i);
            if (item == null) {
                if (rebuilt != null) {
                    rebuilt.add(null);
                }
                continue;
            }
            int eq = item.indexOf('=');
            String key = eq > 0 ? item.substring(0, eq) : item;
            String spoofed = resolveSpoofedValue(key, flags);
            if (spoofed != UNMODIFIED) {
                String currentVal = eq > 0 ? item.substring(eq + 1) : "";
                boolean differs = spoofed == null ? !currentVal.isEmpty() : !spoofed.equals(currentVal);
                if (differs || spoofed == null) {
                    if (rebuilt == null) {
                        rebuilt = new ArrayList<String>(n);
                        for (int k = 0; k < i; k++) {
                            rebuilt.add(source.get(k));
                        }
                    }
                    changed++;
                    if (spoofed != null) {
                        rebuilt.add(key + "=" + spoofed);
                    }
                    continue;
                }
            }
            if (rebuilt != null) {
                rebuilt.add(item);
            }
        }
        if (outChanged != null && outChanged.length > 0) {
            outChanged[0] = changed;
        }
        return changed > 0 ? rebuilt : null;
    }

    public static boolean installIfPresent(
        XposedInterface module,
        ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub,
        final String moduleVersion
    ) {
        if (INSTALLED.get()) {
            return true;
        }
        Class<?> providerCls = CompatMatrix.findClass(cl, "com.android.providers.settings.SettingsProvider");
        if (providerCls == null) {
            return false;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            return true;
        }

        List<Method> callMethods = CompatMatrix.findMethodsByName(providerCls, "call");
        List<Method> queryMethods = CompatMatrix.findMethodsByName(providerCls, "query");

        final Method probeMethod3 = CompatMatrix.findExactMethod(providerCls, "call", String.class, String.class, Bundle.class);
        final Method probeMethod4 = CompatMatrix.findExactMethod(providerCls, "call", String.class, String.class, String.class, Bundle.class);
        if (probeMethod3 != null) {
            probeMethod3.setAccessible(true);
        }
        if (probeMethod4 != null) {
            probeMethod4.setAccessible(true);
        }

        HookGuard callGuard = hub.createGuard(PolicyFlags.PILLAR_SET, "SET_CALL");
        HookGuard queryGuard = hub.createGuard(PolicyFlags.PILLAR_SET, "SET_QUERY");

        int hCall = CompatMatrix.hookMethods(module, callMethods, XposedInterface.PRIORITY_DEFAULT, callGuard, new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                if (Boolean.TRUE.equals(IN_HOST_PROBE.get()) || Boolean.TRUE.equals(IN_CALL.get())) {
                    return HookGuard.proceedOriginal(chain);
                }
                List<Object> args = chain.getArgs();
                if (args.size() < 3) {
                    return HookGuard.proceedOriginal(chain);
                }
                int base = args.size() >= 4 ? 1 : 0;
                String method = args.get(base) instanceof String ? (String) args.get(base) : null;
                String arg = args.get(base + 1) instanceof String ? (String) args.get(base + 1) : null;
                Bundle extras = args.get(base + 2) instanceof Bundle ? (Bundle) args.get(base + 2) : null;
                if (method == null) {
                    return HookGuard.proceedOriginal(chain);
                }

                if (PolicyFlags.IPC_METHOD.equals(method) || PolicyFlags.IPC_METHOD_LEGACY_V21.equals(method)) {
                    int callingUid = Binder.getCallingUid();
                    int callerAppId = PolicyEngine.toAppId(callingUid);
                    int selfId = engine.getVerifiedSelfAppId();
                    if (callerAppId < PolicyEngine.FIRST_APP_UID || selfId < PolicyEngine.FIRST_APP_UID || callerAppId != selfId) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    String cmd = arg;
                    if ((cmd == null || cmd.isEmpty()) && extras != null) {
                        cmd = extras.getString(PolicyFlags.IPC_ARG_CMD, PolicyFlags.IPC_CMD_SNAP);
                    }
                    if (PolicyFlags.IPC_CMD_PURGE.equals(cmd)) {
                        hub.purgeCounters();
                        Bundle ok = new Bundle();
                        ok.putBoolean("ok", true);
                        return ok;
                    }
                    if (PolicyFlags.IPC_CMD_PING.equals(cmd)) {
                        Bundle pong = new Bundle();
                        pong.putString(TelemetryHub.KEY_VERSION, moduleVersion);
                        pong.putInt(TelemetryHub.KEY_API_VERSION, hub.getFrameworkApiVersion());
                        pong.putInt(TelemetryHub.KEY_SELF_APP_ID, selfId);
                        pong.putInt(TelemetryHub.KEY_HEALTH_MASK, hub.computeHealthMask());
                        return pong;
                    }
                    Object provider = chain.getThisObject();
                    int hostDev = probeHostSetting(provider, probeMethod3, probeMethod4, "GET_global", "development_settings_enabled");
                    int hostAdb = probeHostSetting(provider, probeMethod3, probeMethod4, "GET_global", "adb_enabled");
                    int hostWifi = probeHostSetting(provider, probeMethod3, probeMethod4, "GET_global", "adb_wifi_enabled");
                    int hostA11y = probeHostSetting(provider, probeMethod3, probeMethod4, "GET_secure", "accessibility_enabled");
                    return hub.exportBundle(engine, moduleVersion, hostDev, hostAdb, hostWifi, hostA11y);
                }

                int callingUid = Binder.getCallingUid();
                int flags = engine.resolvePolicy(callingUid);
                if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DEV_COHERENT)
                    && !PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_A11Y_COHERENT)
                ) {
                    return HookGuard.proceedOriginal(chain);
                }

                IN_CALL.set(Boolean.TRUE);
                try {
                    int getVec = vectorForGetMethod(method);
                    if (getVec != 0) {
                        String spoofed = resolveSpoofedValue(arg, flags);
                        if (spoofed == UNMODIFIED) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        Object ret = HookGuard.proceedOriginal(chain);
                        Bundle orig = ret instanceof Bundle ? (Bundle) ret : null;
                        String curVal = orig != null ? orig.getString(KEY_VALUE) : null;
                        boolean differs = spoofed == null ? curVal != null : !spoofed.equals(curVal);
                        if (!differs) {
                            return ret;
                        }
                        int effAppId = engine.resolveEffectiveAppId(callingUid);
                        int vec = isA11ySettingKey(arg) ? PolicyFlags.VEC_SET_A11Y_COHERENT : getVec;
                        if (PolicyFlags.isAuditOnly(flags)) {
                            hub.record(effAppId, PolicyFlags.PILLAR_SET, vec, PolicyFlags.ACT_AUDIT, true, 0);
                            return ret;
                        }
                        Bundle patched = new Bundle();
                        patched.putString(KEY_VALUE, spoofed);
                        hub.record(effAppId, PolicyFlags.PILLAR_SET, vec, PolicyFlags.ACT_SANITIZED, false, 0);
                        return patched;
                    }

                    int listVec = vectorForListMethod(method);
                    if (listVec != 0) {
                        Object ret = HookGuard.proceedOriginal(chain);
                        if (!(ret instanceof Bundle)) {
                            return ret;
                        }
                        Bundle orig = (Bundle) ret;
                        ArrayList<String> list = orig.getStringArrayList(KEY_RESULT_LIST);
                        if (list == null || list.isEmpty()) {
                            return ret;
                        }
                        int[] changed = new int[1];
                        ArrayList<String> sanitized = sanitizeSettingsList(list, flags, changed);
                        if (sanitized == null || changed[0] <= 0) {
                            return ret;
                        }
                        int effAppId = engine.resolveEffectiveAppId(callingUid);
                        if (PolicyFlags.isAuditOnly(flags)) {
                            hub.record(effAppId, PolicyFlags.PILLAR_SET, listVec, PolicyFlags.ACT_AUDIT, true, changed[0]);
                            return ret;
                        }
                        Bundle patched = new Bundle(orig);
                        patched.putStringArrayList(KEY_RESULT_LIST, sanitized);
                        hub.record(effAppId, PolicyFlags.PILLAR_SET, listVec, PolicyFlags.ACT_SANITIZED, false, changed[0]);
                        return patched;
                    }

                    return HookGuard.proceedOriginal(chain);
                } finally {
                    IN_CALL.remove();
                }
            }
        });

        int hQuery = CompatMatrix.hookMethods(module, queryMethods, XposedInterface.PRIORITY_DEFAULT, queryGuard, new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                if (Boolean.TRUE.equals(IN_HOST_PROBE.get()) || Boolean.TRUE.equals(IN_QUERY.get())) {
                    return HookGuard.proceedOriginal(chain);
                }
                List<Object> args = chain.getArgs();
                if (args.isEmpty() || !(args.get(0) instanceof Uri)) {
                    return HookGuard.proceedOriginal(chain);
                }
                Uri uri = (Uri) args.get(0);
                if (!"settings".equals(uri.getAuthority())) {
                    return HookGuard.proceedOriginal(chain);
                }
                int callingUid = Binder.getCallingUid();
                int flags = engine.resolvePolicy(callingUid);
                if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DEV_COHERENT)
                    && !PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_A11Y_COHERENT)
                ) {
                    return HookGuard.proceedOriginal(chain);
                }

                IN_QUERY.set(Boolean.TRUE);
                try {
                    List<String> segs = uri.getPathSegments();
                    String singleKey = null;
                    if (segs != null && segs.size() >= 2) {
                        singleKey = segs.get(1);
                    } else if (args.size() >= 4 && args.get(2) instanceof String && args.get(3) instanceof String[]) {
                        String sel = (String) args.get(2);
                        String[] selArgs = (String[]) args.get(3);
                        if (sel != null && sel.contains("name") && selArgs != null && selArgs.length == 1) {
                            singleKey = selArgs[0];
                        }
                    }

                    if (singleKey != null) {
                        String spoofed = resolveSpoofedValue(singleKey, flags);
                        if (spoofed == UNMODIFIED) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        Object ret = HookGuard.proceedOriginal(chain);
                        if (!(ret instanceof Cursor)) {
                            return ret;
                        }
                        Cursor orig = (Cursor) ret;
                        int valIdx = orig.getColumnIndex(KEY_VALUE);
                        if (valIdx < 0 || !orig.moveToFirst()) {
                            orig.moveToPosition(-1);
                            return orig;
                        }
                        String curVal = orig.getString(valIdx);
                        boolean differs = spoofed == null ? curVal != null : !spoofed.equals(curVal);
                        if (!differs) {
                            orig.moveToPosition(-1);
                            return orig;
                        }
                        int effAppId = engine.resolveEffectiveAppId(callingUid);
                        if (PolicyFlags.isAuditOnly(flags)) {
                            orig.moveToPosition(-1);
                            hub.record(effAppId, PolicyFlags.PILLAR_SET, PolicyFlags.VEC_SET_QUERY_SINGLE, PolicyFlags.ACT_AUDIT, true, 0);
                            return orig;
                        }
                        String[] cols = orig.getColumnNames();
                        MatrixCursor mc = new MatrixCursor(cols, 1);
                        Object[] row = copyRowWithOverride(orig, cols.length, valIdx, spoofed);
                        mc.addRow(row);
                        orig.close();
                        hub.record(effAppId, PolicyFlags.PILLAR_SET, PolicyFlags.VEC_SET_QUERY_SINGLE, PolicyFlags.ACT_SANITIZED, false, 0);
                        return mc;
                    }

                    if (segs != null && segs.size() == 1) {
                        Object ret = HookGuard.proceedOriginal(chain);
                        if (!(ret instanceof Cursor)) {
                            return ret;
                        }
                        Cursor orig = (Cursor) ret;
                        int nameIdx = orig.getColumnIndex("name");
                        int valIdx = orig.getColumnIndex(KEY_VALUE);
                        if (nameIdx < 0 || valIdx < 0) {
                            return orig;
                        }
                        int changed = 0;
                        int scanned = 0;
                        while (scanned < MAX_BULK_CURSOR_ROWS && orig.moveToNext()) {
                            scanned++;
                            String k = orig.getString(nameIdx);
                            String sp = resolveSpoofedValue(k, flags);
                            if (sp != UNMODIFIED) {
                                String cv = orig.getString(valIdx);
                                boolean differs = sp == null ? cv != null : !sp.equals(cv);
                                if (differs) {
                                    changed++;
                                }
                            }
                        }
                        orig.moveToPosition(-1);
                        if (changed == 0) {
                            return orig;
                        }
                        int effAppId = engine.resolveEffectiveAppId(callingUid);
                        if (PolicyFlags.isAuditOnly(flags)) {
                            hub.record(effAppId, PolicyFlags.PILLAR_SET, PolicyFlags.VEC_SET_QUERY_BULK, PolicyFlags.ACT_AUDIT, true, changed);
                            return orig;
                        }
                        String[] cols = orig.getColumnNames();
                        MatrixCursor mc = new MatrixCursor(cols, Math.min(orig.getCount(), MAX_BULK_CURSOR_ROWS));
                        int copied = 0;
                        while (copied < MAX_BULK_CURSOR_ROWS && orig.moveToNext()) {
                            copied++;
                            String k = orig.getString(nameIdx);
                            String sp = resolveSpoofedValue(k, flags);
                            if (sp != UNMODIFIED) {
                                mc.addRow(copyRowWithOverride(orig, cols.length, valIdx, sp));
                            } else {
                                mc.addRow(copyRowDirect(orig, cols.length));
                            }
                        }
                        orig.close();
                        hub.record(effAppId, PolicyFlags.PILLAR_SET, PolicyFlags.VEC_SET_QUERY_BULK, PolicyFlags.ACT_SANITIZED, false, changed);
                        return mc;
                    }

                    return HookGuard.proceedOriginal(chain);
                } finally {
                    IN_QUERY.remove();
                }
            }
        });

        if (hCall > 0) {
            hub.markIpcReady();
            hub.markSettingsReady();
            hub.setPillarStatus(
                PolicyFlags.PILLAR_SET,
                hQuery > 0 ? TelemetryHub.HEALTH_OK : TelemetryHub.HEALTH_FALLBACK,
                CompatMatrix.TIER_EXACT
            );
            return true;
        }
        hub.setPillarStatus(PolicyFlags.PILLAR_SET, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
        return false;
    }

    private static int vectorForGetMethod(String method) {
        if ("GET_global".equals(method)) {
            return PolicyFlags.VEC_SET_CALL_GLOBAL;
        }
        if ("GET_secure".equals(method)) {
            return PolicyFlags.VEC_SET_CALL_SECURE;
        }
        if ("GET_system".equals(method)) {
            return PolicyFlags.VEC_SET_CALL_SYSTEM;
        }
        return 0;
    }

    private static int vectorForListMethod(String method) {
        if ("LIST_global".equals(method)) {
            return PolicyFlags.VEC_SET_LIST_GLOBAL;
        }
        if ("LIST_secure".equals(method)) {
            return PolicyFlags.VEC_SET_LIST_SECURE;
        }
        if ("LIST_system".equals(method)) {
            return PolicyFlags.VEC_SET_LIST_SYSTEM;
        }
        return 0;
    }

    private static Object[] copyRowDirect(Cursor c, int colCount) {
        Object[] row = new Object[colCount];
        for (int i = 0; i < colCount; i++) {
            row[i] = readColumn(c, i);
        }
        return row;
    }

    private static Object[] copyRowWithOverride(Cursor c, int colCount, int valIdx, String spoofed) {
        Object[] row = new Object[colCount];
        for (int i = 0; i < colCount; i++) {
            row[i] = (i == valIdx) ? spoofed : readColumn(c, i);
        }
        return row;
    }

    private static Object readColumn(Cursor c, int idx) {
        switch (c.getType(idx)) {
            case Cursor.FIELD_TYPE_NULL:
                return null;
            case Cursor.FIELD_TYPE_INTEGER:
                return Long.valueOf(c.getLong(idx));
            case Cursor.FIELD_TYPE_FLOAT:
                return Double.valueOf(c.getDouble(idx));
            case Cursor.FIELD_TYPE_BLOB:
                return c.getBlob(idx);
            default:
                return c.getString(idx);
        }
    }

    private static int probeHostSetting(
        Object provider,
        Method m3,
        Method m4,
        String getMethod,
        String key
    ) {
        if (provider == null) {
            return -1;
        }
        long token = Binder.clearCallingIdentity();
        IN_HOST_PROBE.set(Boolean.TRUE);
        try {
            Object res = null;
            if (m4 != null) {
                res = m4.invoke(provider, "settings", getMethod, key, new Bundle());
            } else if (m3 != null) {
                res = m3.invoke(provider, getMethod, key, new Bundle());
            }
            if (res instanceof Bundle) {
                String v = ((Bundle) res).getString(KEY_VALUE);
                if (v != null && !v.isEmpty()) {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException ignored) {
                        return 1;
                    }
                }
                return 0;
            }
            return -1;
        } catch (Throwable ignored) {
            return -1;
        } finally {
            IN_HOST_PROBE.remove();
            Binder.restoreCallingIdentity(token);
        }
    }
}
