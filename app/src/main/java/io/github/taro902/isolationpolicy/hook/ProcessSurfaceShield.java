package io.github.taro902.isolationpolicy.hook;

import android.os.Binder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class ProcessSurfaceShield {

    public static final int REASON_SIGNALED = 2;
    public static final int REASON_CRASH_NATIVE = 5;
    public static final int REASON_USER_REQUESTED = 10;
    public static final int REASON_FREEZER = 14;

    private static volatile Method cachedGetReasonMethod;

    private ProcessSurfaceShield() {
    }

    public static boolean isSensitiveExitReason(int reason) {
        return reason == REASON_SIGNALED
            || reason == REASON_CRASH_NATIVE
            || reason == REASON_USER_REQUESTED
            || reason == REASON_FREEZER;
    }

    public static void install(
        XposedInterface module,
        final ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub
    ) {
        Class<?> amsCls = CompatMatrix.findClass(cl, "com.android.server.am.ActivityManagerService");
        if (amsCls == null) {
            hub.setPillarStatus(PolicyFlags.PILLAR_PROC, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
            return;
        }

        int installed = 0;

        Method mExit = CompatMatrix.findExactMethod(
            amsCls,
            "getHistoricalProcessExitReasons",
            String.class,
            int.class,
            int.class,
            int.class
        );
        if (mExit != null) {
            HookGuard exitGuard = hub.createGuard(PolicyFlags.PILLAR_PROC, "PROC_EXIT");
            installed += CompatMatrix.hookMethod(module, mExit, XposedInterface.PRIORITY_DEFAULT, exitGuard, new HookGuard.Handler() {
                @Override
                public Object handle(XposedInterface.Chain chain) throws Throwable {
                    int callingUid = Binder.getCallingUid();
                    int flags = engine.resolvePolicy(callingUid);
                    if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_PROC_SURFACE)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    Object ret = HookGuard.proceedOriginal(chain);
                    List<?> items = CompatMatrix.extractParceledList(ret);
                    if (items == null || items.isEmpty()) {
                        return ret;
                    }
                    ArrayList<Object> kept = new ArrayList<Object>(items.size());
                    for (int i = 0; i < items.size(); i++) {
                        Object item = items.get(i);
                        int reason = readExitReason(item);
                        if (!isSensitiveExitReason(reason)) {
                            kept.add(item);
                        }
                    }
                    int removed = items.size() - kept.size();
                    if (removed <= 0) {
                        return ret;
                    }
                    int effAppId = engine.resolveEffectiveAppId(callingUid);
                    if (PolicyFlags.isAuditOnly(flags)) {
                        hub.record(effAppId, PolicyFlags.PILLAR_PROC, PolicyFlags.VEC_PROC_EXIT_REASONS, PolicyFlags.ACT_AUDIT, true, removed);
                        return ret;
                    }
                    Object exec = chain.getExecutable();
                    if (exec instanceof Method && List.class.isAssignableFrom(((Method) exec).getReturnType())) {
                        hub.record(effAppId, PolicyFlags.PILLAR_PROC, PolicyFlags.VEC_PROC_EXIT_REASONS, PolicyFlags.ACT_SANITIZED, false, removed);
                        return kept;
                    }
                    Object wrapped = CompatMatrix.wrapParceledList(cl, kept);
                    if (wrapped != null) {
                        hub.record(effAppId, PolicyFlags.PILLAR_PROC, PolicyFlags.VEC_PROC_EXIT_REASONS, PolicyFlags.ACT_SANITIZED, false, removed);
                        return wrapped;
                    }
                    return ret;
                }
            });
        }

        Method mMonkey = CompatMatrix.findExactMethod(amsCls, "isUserAMonkey");
        if (mMonkey != null) {
            HookGuard monkeyGuard = hub.createGuard(PolicyFlags.PILLAR_PROC, "PROC_MONKEY");
            installed += CompatMatrix.hookMethod(module, mMonkey, XposedInterface.PRIORITY_DEFAULT, monkeyGuard, new HookGuard.Handler() {
                @Override
                public Object handle(XposedInterface.Chain chain) throws Throwable {
                    int callingUid = Binder.getCallingUid();
                    int flags = engine.resolvePolicy(callingUid);
                    if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_PROC_SURFACE)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    Object ret = HookGuard.proceedOriginal(chain);
                    if (!Boolean.TRUE.equals(ret)) {
                        return ret;
                    }
                    int effAppId = engine.resolveEffectiveAppId(callingUid);
                    if (PolicyFlags.isAuditOnly(flags)) {
                        hub.record(effAppId, PolicyFlags.PILLAR_PROC, PolicyFlags.VEC_PROC_MONKEY, PolicyFlags.ACT_AUDIT, true, 0);
                        return ret;
                    }
                    hub.record(effAppId, PolicyFlags.PILLAR_PROC, PolicyFlags.VEC_PROC_MONKEY, PolicyFlags.ACT_SANITIZED, false, 0);
                    return Boolean.FALSE;
                }
            });
        }

        Method mErr = CompatMatrix.findExactMethod(amsCls, "getProcessesInErrorState");
        if (mErr != null) {
            HookGuard errGuard = hub.createGuard(PolicyFlags.PILLAR_PROC, "PROC_ERR");
            installed += CompatMatrix.hookMethod(module, mErr, XposedInterface.PRIORITY_DEFAULT, errGuard, new HookGuard.Handler() {
                @Override
                public Object handle(XposedInterface.Chain chain) throws Throwable {
                    int callingUid = Binder.getCallingUid();
                    int flags = engine.resolvePolicy(callingUid);
                    if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_PROC_SURFACE)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    Object ret = HookGuard.proceedOriginal(chain);
                    if (!(ret instanceof List) || ((List<?>) ret).isEmpty()) {
                        return ret;
                    }
                    int count = ((List<?>) ret).size();
                    int effAppId = engine.resolveEffectiveAppId(callingUid);
                    if (PolicyFlags.isAuditOnly(flags)) {
                        hub.record(effAppId, PolicyFlags.PILLAR_PROC, PolicyFlags.VEC_PROC_ERROR_STATE, PolicyFlags.ACT_AUDIT, true, count);
                        return ret;
                    }
                    hub.record(effAppId, PolicyFlags.PILLAR_PROC, PolicyFlags.VEC_PROC_ERROR_STATE, PolicyFlags.ACT_SUPPRESSED, false, count);
                    return null;
                }
            });
        }

        hub.setPillarStatus(
            PolicyFlags.PILLAR_PROC,
            installed > 0 ? TelemetryHub.HEALTH_OK : TelemetryHub.HEALTH_OFF,
            installed > 0 ? CompatMatrix.TIER_EXACT : CompatMatrix.TIER_NONE
        );
    }

    private static int readExitReason(Object item) {
        if (item == null) {
            return -1;
        }
        try {
            Method m = cachedGetReasonMethod;
            if (m == null) {
                m = CompatMatrix.findExactMethod(item.getClass(), "getReason");
                if (m != null) {
                    m.setAccessible(true);
                    cachedGetReasonMethod = m;
                }
            }
            if (m != null) {
                Object res = m.invoke(item);
                if (res instanceof Integer) {
                    return (Integer) res;
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }
}
