package io.github.taro902.isolationpolicy.hook;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.core.Logger;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class AccessibilityShield {

    private static final String TAG = "AccessibilityShield";
    private static final int A11Y_STATE_CLEAR_MASK = 0x01 | 0x02 | 0x08 | 0x10;

    private final Map<IBinder, Integer> clientBinderUidMap = Collections.synchronizedMap(new WeakHashMap<>());
    private volatile Method mParceledEmptyList;
    private volatile Constructor<?> cParceledListCtor;

    public void install(XposedModule module, ClassLoader sysCl, PolicyEngine engine, TelemetryHub telemetry) {
        resolveParceledListSlice(sysCl);

        int coreHooks = 0;
        int pushHooks = 0;

        try {
            Class<?> amsCls = Class.forName("com.android.server.accessibility.AccessibilityManagerService", false, sysCl);
            for (Method m : amsCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("addClient".equals(name) && m.getReturnType() == long.class) {
                    if (hookAddClient(module, m, engine, telemetry)) {
                        coreHooks++;
                    }
                } else if ("getEnabledAccessibilityServiceList".equals(name)) {
                    if (hookServiceList(module, m, PolicyFlags.VEC_A11Y_ENABLED, engine, telemetry)) {
                        coreHooks++;
                    }
                } else if ("getInstalledAccessibilityServiceList".equals(name)) {
                    if (hookServiceList(module, m, PolicyFlags.VEC_A11Y_INSTALLED, engine, telemetry)) {
                        coreHooks++;
                    }
                } else if ("getAccessibilityShortcutTargets".equals(name)) {
                    hookShortcutTargets(module, m, engine);
                }
            }
        } catch (Throwable t) {
            Logger.e(TAG, "load AccessibilityManagerService failed", t);
        }

        try {
            Class<?> proxyCls = Class.forName(
                    "android.view.accessibility.IAccessibilityManagerClient$Stub$Proxy",
                    false,
                    sysCl
            );
            for (Method m : proxyCls.getDeclaredMethods()) {
                if ("setState".equals(m.getName()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
                    if (hookClientSetState(module, m, engine, telemetry)) {
                        pushHooks++;
                    }
                }
            }
        } catch (Throwable t) {
            Logger.e(TAG, "hook IAccessibilityManagerClient proxy failed", t);
        }

        if (coreHooks >= 3 && pushHooks > 0) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_A11Y, TelemetryHub.STATE_OK);
        } else if (coreHooks > 0) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_A11Y, TelemetryHub.STATE_FALLBACK);
        } else {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_A11Y, TelemetryHub.STATE_FAILED);
        }
    }

    private boolean hookAddClient(
            XposedModule module,
            Method m,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        try {
            module.deoptimize(m);
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int callingUid = Binder.getCallingUid();
                        if (callingUid >= 10000) {
                            List<Object> args = chain.getArgs();
                            if (!args.isEmpty() && args.get(0) instanceof IInterface) {
                                IBinder binder = ((IInterface) args.get(0)).asBinder();
                                if (binder != null) {
                                    clientBinderUidMap.put(binder, callingUid);
                                }
                            }
                        }

                        Object res = chain.proceed();
                        if (!(res instanceof Long)) {
                            return res;
                        }
                        if (!engine.hasFlag(callingUid, PolicyFlags.FLAG_A11Y_COHERENT)) {
                            return res;
                        }

                        long packed = (Long) res;
                        int state = (int) (packed >> 32);
                        int events = (int) packed;
                        int cleanState = state & ~A11Y_STATE_CLEAR_MASK;
                        if (cleanState != state) {
                            int appId = engine.resolveEffectiveAppId(callingUid);
                            telemetry.record(appId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_CLIENT, PolicyFlags.ACT_SANITIZED);
                            return (((long) cleanState) << 32) | (events & 0xFFFFFFFFL);
                        }
                        return res;
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook addClient failed", t);
            return false;
        }
    }

    private boolean hookServiceList(
            XposedModule module,
            Method m,
            int vectorCode,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        final boolean returnsParceled = m.getReturnType().getName().endsWith("ParceledListSlice");
        try {
            module.deoptimize(m);
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int callingUid = Binder.getCallingUid();
                        if (!engine.hasFlag(callingUid, PolicyFlags.FLAG_A11Y_COHERENT)) {
                            return chain.proceed();
                        }
                        int appId = engine.resolveEffectiveAppId(callingUid);
                        telemetry.record(appId, PolicyFlags.PILLAR_A11Y, vectorCode, PolicyFlags.ACT_CLEARED);
                        if (returnsParceled) {
                            Object emptySlice = newEmptyParceledSlice();
                            if (emptySlice != null) {
                                return emptySlice;
                            }
                            return chain.proceed();
                        }
                        return Collections.emptyList();
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook service list failed", t);
            return false;
        }
    }

    private void hookShortcutTargets(XposedModule module, Method m, PolicyEngine engine) {
        if (!List.class.isAssignableFrom(m.getReturnType())) {
            return;
        }
        try {
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int callingUid = Binder.getCallingUid();
                        if (engine.hasFlag(callingUid, PolicyFlags.FLAG_A11Y_COHERENT)) {
                            return Collections.emptyList();
                        }
                        return chain.proceed();
                    });
        } catch (Throwable ignored) {
        }
    }

    private boolean hookClientSetState(
            XposedModule module,
            Method m,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        try {
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object receiver = chain.getThisObject();
                        if (!(receiver instanceof IInterface)) {
                            return chain.proceed();
                        }
                        IBinder binder = ((IInterface) receiver).asBinder();
                        Integer clientUid = binder != null ? clientBinderUidMap.get(binder) : null;
                        if (clientUid == null || !engine.hasFlag(clientUid, PolicyFlags.FLAG_A11Y_COHERENT)) {
                            return chain.proceed();
                        }
                        List<Object> args = chain.getArgs();
                        if (!args.isEmpty() && args.get(0) instanceof Integer) {
                            int stateFlags = (Integer) args.get(0);
                            int clean = stateFlags & ~A11Y_STATE_CLEAR_MASK;
                            if (clean != stateFlags) {
                                int appId = engine.resolveEffectiveAppId(clientUid);
                                telemetry.record(appId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_PUSH, PolicyFlags.ACT_SANITIZED);
                                return chain.proceed(new Object[]{clean});
                            }
                        }
                        return chain.proceed();
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook setState failed", t);
            return false;
        }
    }

    private void resolveParceledListSlice(ClassLoader sysCl) {
        try {
            Class<?> sliceCls = Class.forName("android.content.pm.ParceledListSlice", false, sysCl);
            try {
                Method empty = sliceCls.getDeclaredMethod("emptyList");
                empty.setAccessible(true);
                mParceledEmptyList = empty;
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Constructor<?> ctor = sliceCls.getDeclaredConstructor(List.class);
                ctor.setAccessible(true);
                cParceledListCtor = ctor;
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private Object newEmptyParceledSlice() {
        try {
            Method m = mParceledEmptyList;
            if (m != null) {
                return m.invoke(null);
            }
            Constructor<?> c = cParceledListCtor;
            if (c != null) {
                return c.newInstance(Collections.emptyList());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}