package io.github.taro902.isolationpolicy.hook;

import android.content.pm.ApplicationInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.core.Logger;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class AppZygoteShield {

    private static final String TAG = "AppZygoteShield";
    private static final int REGULAR_ZYGOTE = 0;

    private final Map<Object, String> recordPkgMap = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, Integer> recordUidMap = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, Boolean> downgradedRecords = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, Boolean> hardDropRecords = Collections.synchronizedMap(new WeakHashMap<>());

    private volatile Field fHostingZygote;
    private volatile Field fProcUid;
    private volatile Field fProcInfo;

    public void install(XposedModule module, ClassLoader sysCl, PolicyEngine engine, TelemetryHub telemetry) {
        int installedCount = 0;
        Class<?> hostingRecordCls = null;
        Class<?> processListCls = null;
        Class<?> processRecordCls = null;

        try {
            hostingRecordCls = Class.forName("com.android.server.am.HostingRecord", false, sysCl);
            processListCls = Class.forName("com.android.server.am.ProcessList", false, sysCl);
            processRecordCls = Class.forName("com.android.server.am.ProcessRecord", false, sysCl);
        } catch (Throwable t) {
            Logger.e(TAG, "class load failed", t);
            telemetry.markHookState(PolicyFlags.HOOK_BIT_APP_ZYGOTE, TelemetryHub.STATE_FAILED);
            return;
        }

        fHostingZygote = findField(hostingRecordCls, "mHostingZygote");
        fProcUid = findField(processRecordCls, "uid");
        fProcInfo = findField(processRecordCls, "info");

        for (Method m : hostingRecordCls.getDeclaredMethods()) {
            String name = m.getName();
            if ("byAppZygote".equals(name)) {
                if (hookByAppZygote(module, m, engine, telemetry)) {
                    installedCount++;
                }
            } else if ("usesAppZygote".equals(name) && m.getParameterCount() == 0) {
                if (hookUsesAppZygote(module, m)) {
                    installedCount++;
                }
            }
        }

        for (Method m : processListCls.getDeclaredMethods()) {
            String name = m.getName();
            if ("startProcessLocked".equals(name)) {
                if (hookStartProcessLocked(module, m, hostingRecordCls, processRecordCls, engine, telemetry)) {
                    installedCount++;
                }
            } else if ("startProcess".equals(name)) {
                if (hookStartProcess(module, m, hostingRecordCls, processRecordCls, engine, telemetry)) {
                    installedCount++;
                }
            } else if ("removeProcessNameLocked".equals(name)) {
                hookRemoveProcessNameLocked(module, m, engine);
            }
        }

        if (installedCount >= 4) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_APP_ZYGOTE, TelemetryHub.STATE_OK);
        } else if (installedCount > 0) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_APP_ZYGOTE, TelemetryHub.STATE_FALLBACK);
        } else {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_APP_ZYGOTE, TelemetryHub.STATE_FAILED);
        }
    }

    private boolean hookByAppZygote(
            XposedModule module,
            Method m,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        Class<?>[] pTypes = m.getParameterTypes();
        int idxPkg = -1;
        int idxUid = -1;
        for (int i = 0; i < pTypes.length; i++) {
            if (idxPkg < 0 && pTypes[i] == String.class) {
                idxPkg = i;
            } else if (idxUid < 0 && pTypes[i] == int.class) {
                idxUid = i;
            }
        }
        final int pkgIndex = idxPkg;
        final int uidIndex = idxUid;
        try {
            module.deoptimize(m);
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object record = chain.proceed();
                        if (record == null) {
                            return null;
                        }
                        List<Object> args = chain.getArgs();
                        String definingPkg = (pkgIndex >= 0 && pkgIndex < args.size() && args.get(pkgIndex) instanceof String)
                                ? (String) args.get(pkgIndex)
                                : null;
                        int definingUid = (uidIndex >= 0 && uidIndex < args.size() && args.get(uidIndex) instanceof Integer)
                                ? (Integer) args.get(uidIndex)
                                : -1;

                        if (definingPkg != null) {
                            recordPkgMap.put(record, definingPkg);
                        }
                        if (definingUid >= 10000) {
                            recordUidMap.put(record, definingUid);
                        }

                        int azMode = engine.getAzModeForPackageOrUid(definingPkg, definingUid);
                        if (azMode == PolicyFlags.AZ_DOWNGRADE || azMode == PolicyFlags.AZ_AUTO) {
                            downgradeRecord(record);
                            int appId = definingUid >= 10000 ? (definingUid % 100000) : 0;
                            telemetry.record(appId, PolicyFlags.PILLAR_APP_ZYGOTE, PolicyFlags.VEC_AZ_BY_RECORD, PolicyFlags.ACT_DOWNGRADED);
                        } else if (azMode == PolicyFlags.AZ_HARD_DROP) {
                            hardDropRecords.put(record, Boolean.TRUE);
                        }
                        return record;
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook byAppZygote failed", t);
            return false;
        }
    }

    private boolean hookUsesAppZygote(XposedModule module, Method m) {
        try {
            module.deoptimize(m);
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object self = chain.getThisObject();
                        if (self != null && downgradedRecords.containsKey(self)) {
                            return Boolean.FALSE;
                        }
                        return chain.proceed();
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook usesAppZygote failed", t);
            return false;
        }
    }

    private boolean hookStartProcessLocked(
            XposedModule module,
            Method m,
            Class<?> hostingRecordCls,
            Class<?> processRecordCls,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        OverloadMeta meta = analyzeOverload(m, hostingRecordCls, processRecordCls);
        if (meta.idxHosting < 0 && meta.idxProcRecord < 0 && meta.idxAppInfo < 0) {
            return false;
        }
        final boolean returnsBoolean = m.getReturnType() == boolean.class;
        try {
            module.deoptimize(m);
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        int procUid = -1;
                        int ownerUid = -1;
                        String pkgName = null;

                        if (meta.idxAppInfo >= 0 && meta.idxAppInfo < args.size()) {
                            Object o = args.get(meta.idxAppInfo);
                            if (o instanceof ApplicationInfo) {
                                ApplicationInfo ai = (ApplicationInfo) o;
                                ownerUid = ai.uid;
                                pkgName = ai.packageName;
                                procUid = ai.uid;
                            }
                        }

                        if (meta.idxProcRecord >= 0 && meta.idxProcRecord < args.size()) {
                            Object pr = args.get(meta.idxProcRecord);
                            if (pr != null) {
                                int u = readProcUid(pr);
                                if (u >= 10000) {
                                    procUid = u;
                                }
                                ApplicationInfo ai = readProcInfo(pr);
                                if (ai != null) {
                                    ownerUid = ai.uid;
                                    pkgName = ai.packageName;
                                    if (procUid < 10000) {
                                        procUid = ai.uid;
                                    }
                                }
                            }
                        }

                        if (meta.idxExplicitUid >= 0 && meta.idxExplicitUid < args.size()) {
                            Object uObj = args.get(meta.idxExplicitUid);
                            if (uObj instanceof Integer && (Integer) uObj >= 10000) {
                                procUid = (Integer) uObj;
                            }
                        }

                        if (ownerUid >= 10000 && pkgName != null) {
                            engine.registerLiveProcess(procUid >= 10000 ? procUid : ownerUid, pkgName, ownerUid);
                        }

                        if (meta.idxHosting >= 0 && meta.idxHosting < args.size()) {
                            Object hr = args.get(meta.idxHosting);
                            if (hr != null) {
                                if (hardDropRecords.containsKey(hr)) {
                                    int appId = ownerUid >= 10000 ? (ownerUid % 100000) : resolveRecordAppId(hr);
                                    telemetry.record(appId, PolicyFlags.PILLAR_APP_ZYGOTE, PolicyFlags.VEC_AZ_START_LOCKED, PolicyFlags.ACT_HARD_DROPPED);
                                    return returnsBoolean ? Boolean.FALSE : null;
                                }
                                if (downgradedRecords.containsKey(hr)) {
                                    forceRegularZygote(hr);
                                } else {
                                    String hrPkg = pkgName != null ? pkgName : recordPkgMap.get(hr);
                                    int hrUid = ownerUid >= 10000 ? ownerUid : resolveRecordUid(hr);
                                    int azMode = engine.getAzModeForPackageOrUid(hrPkg, hrUid);
                                    if (isCurrentlyAppZygote(hr)) {
                                        if (azMode == PolicyFlags.AZ_HARD_DROP) {
                                            hardDropRecords.put(hr, Boolean.TRUE);
                                            int appId = hrUid >= 10000 ? (hrUid % 100000) : 0;
                                            telemetry.record(appId, PolicyFlags.PILLAR_APP_ZYGOTE, PolicyFlags.VEC_AZ_START_LOCKED, PolicyFlags.ACT_HARD_DROPPED);
                                            return returnsBoolean ? Boolean.FALSE : null;
                                        }
                                        if (azMode == PolicyFlags.AZ_DOWNGRADE || azMode == PolicyFlags.AZ_AUTO) {
                                            downgradeRecord(hr);
                                            int appId = hrUid >= 10000 ? (hrUid % 100000) : 0;
                                            telemetry.record(appId, PolicyFlags.PILLAR_APP_ZYGOTE, PolicyFlags.VEC_AZ_START_LOCKED, PolicyFlags.ACT_DOWNGRADED);
                                        }
                                    }
                                }
                            }
                        }
                        return chain.proceed();
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook startProcessLocked failed", t);
            return false;
        }
    }

    private boolean hookStartProcess(
            XposedModule module,
            Method m,
            Class<?> hostingRecordCls,
            Class<?> processRecordCls,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        OverloadMeta meta = analyzeOverload(m, hostingRecordCls, processRecordCls);
        if (meta.idxHosting < 0) {
            return false;
        }
        try {
            module.deoptimize(m);
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        Object hr = meta.idxHosting < args.size() ? args.get(meta.idxHosting) : null;
                        if (hr != null) {
                            if (hardDropRecords.containsKey(hr)) {
                                telemetry.record(resolveRecordAppId(hr), PolicyFlags.PILLAR_APP_ZYGOTE, PolicyFlags.VEC_AZ_START_PROC, PolicyFlags.ACT_HARD_DROPPED);
                                return null;
                            }
                            if (downgradedRecords.containsKey(hr)) {
                                forceRegularZygote(hr);
                            } else if (isCurrentlyAppZygote(hr)) {
                                String hrPkg = recordPkgMap.get(hr);
                                int hrUid = resolveRecordUid(hr);
                                if (hrPkg == null && meta.idxProcRecord >= 0 && meta.idxProcRecord < args.size()) {
                                    ApplicationInfo ai = readProcInfo(args.get(meta.idxProcRecord));
                                    if (ai != null) {
                                        hrPkg = ai.packageName;
                                        hrUid = ai.uid;
                                    }
                                }
                                int azMode = engine.getAzModeForPackageOrUid(hrPkg, hrUid);
                                if (azMode == PolicyFlags.AZ_HARD_DROP) {
                                    hardDropRecords.put(hr, Boolean.TRUE);
                                    int appId = hrUid >= 10000 ? (hrUid % 100000) : 0;
                                    telemetry.record(appId, PolicyFlags.PILLAR_APP_ZYGOTE, PolicyFlags.VEC_AZ_START_PROC, PolicyFlags.ACT_HARD_DROPPED);
                                    return null;
                                }
                                if (azMode == PolicyFlags.AZ_DOWNGRADE || azMode == PolicyFlags.AZ_AUTO) {
                                    downgradeRecord(hr);
                                    int appId = hrUid >= 10000 ? (hrUid % 100000) : 0;
                                    telemetry.record(appId, PolicyFlags.PILLAR_APP_ZYGOTE, PolicyFlags.VEC_AZ_START_PROC, PolicyFlags.ACT_DOWNGRADED);
                                }
                            }
                        }
                        return chain.proceed();
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook startProcess failed", t);
            return false;
        }
    }

    private void hookRemoveProcessNameLocked(XposedModule module, Method m, PolicyEngine engine) {
        Class<?>[] pTypes = m.getParameterTypes();
        int uidIdx = -1;
        for (int i = 0; i < pTypes.length; i++) {
            if (pTypes[i] == int.class) {
                uidIdx = i;
                break;
            }
        }
        if (uidIdx < 0) {
            return;
        }
        final int targetIdx = uidIdx;
        try {
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        if (targetIdx < args.size() && args.get(targetIdx) instanceof Integer) {
                            int uid = (Integer) args.get(targetIdx);
                            if (uid >= 90000) {
                                engine.evictIsolatedUid(uid);
                            }
                        }
                        return chain.proceed();
                    });
        } catch (Throwable ignored) {
        }
    }

    private void downgradeRecord(Object record) {
        downgradedRecords.put(record, Boolean.TRUE);
        forceRegularZygote(record);
    }

    private void forceRegularZygote(Object record) {
        Field f = fHostingZygote;
        if (f != null) {
            try {
                if (f.getInt(record) != REGULAR_ZYGOTE) {
                    f.setInt(record, REGULAR_ZYGOTE);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean isCurrentlyAppZygote(Object record) {
        Field f = fHostingZygote;
        if (f != null) {
            try {
                return f.getInt(record) == 2;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private int resolveRecordUid(Object hr) {
        Integer u = recordUidMap.get(hr);
        return u != null ? u : -1;
    }

    private int resolveRecordAppId(Object hr) {
        int u = resolveRecordUid(hr);
        return u >= 10000 ? (u % 100000) : 0;
    }

    private int readProcUid(Object pr) {
        Field f = fProcUid;
        if (f == null || pr == null) {
            return -1;
        }
        try {
            return f.getInt(pr);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private ApplicationInfo readProcInfo(Object pr) {
        Field f = fProcInfo;
        if (f == null || pr == null) {
            return null;
        }
        try {
            Object v = f.get(pr);
            return v instanceof ApplicationInfo ? (ApplicationInfo) v : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private static OverloadMeta analyzeOverload(Method m, Class<?> hostingRecordCls, Class<?> processRecordCls) {
        Class<?>[] p = m.getParameterTypes();
        int idxHosting = -1;
        int idxProc = -1;
        int idxAppInfo = -1;
        int idxExplicitUid = -1;
        for (int i = 0; i < p.length; i++) {
            if (idxHosting < 0 && hostingRecordCls.isAssignableFrom(p[i])) {
                idxHosting = i;
            } else if (idxProc < 0 && processRecordCls.isAssignableFrom(p[i])) {
                idxProc = i;
            } else if (idxAppInfo < 0 && p[i] == ApplicationInfo.class) {
                idxAppInfo = i;
            } else if (idxExplicitUid < 0 && idxProc >= 0 && p[i] == int.class && i == idxProc + 1) {
                idxExplicitUid = i;
            }
        }
        return new OverloadMeta(idxHosting, idxProc, idxAppInfo, idxExplicitUid);
    }

    private static final class OverloadMeta {
        final int idxHosting;
        final int idxProcRecord;
        final int idxAppInfo;
        final int idxExplicitUid;

        OverloadMeta(int idxHosting, int idxProcRecord, int idxAppInfo, int idxExplicitUid) {
            this.idxHosting = idxHosting;
            this.idxProcRecord = idxProcRecord;
            this.idxAppInfo = idxAppInfo;
            this.idxExplicitUid = idxExplicitUid;
        }
    }
}