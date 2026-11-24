package io.github.taro902.isolationpolicy.hook;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class AccessibilityShield {

    private static final int A11Y_STATE_STRIP_MASK = 0x1B;
    private static final int MAX_CLIENT_TRACK = 512;

    private AccessibilityShield() {
    }

    public static void install(
        XposedInterface module,
        final ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub
    ) {
        Class<?> amsCls = CompatMatrix.findClass(cl, "com.android.server.accessibility.AccessibilityManagerService");
        if (amsCls == null) {
            hub.setPillarStatus(PolicyFlags.PILLAR_A11Y, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
            return;
        }

        final ConcurrentHashMap<IBinder, Integer> trackedClients = new ConcurrentHashMap<IBinder, Integer>(64);
        int installed = 0;

        List<Method> addClientMethods = CompatMatrix.findMethodsByName(amsCls, "addClient");
        HookGuard addGuard = hub.createGuard(PolicyFlags.PILLAR_A11Y, "A11Y_ADD");
        installed += CompatMatrix.hookMethods(module, addClientMethods, XposedInterface.PRIORITY_DEFAULT, addGuard, new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                int callingUid = Binder.getCallingUid();
                int flags = engine.resolvePolicy(callingUid);
                if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_A11Y_COHERENT)) {
                    return HookGuard.proceedOriginal(chain);
                }
                int effAppId = engine.resolveEffectiveAppId(callingUid);
                List<Object> args = chain.getArgs();
                if (!args.isEmpty() && args.get(0) instanceof IInterface && effAppId >= PolicyEngine.FIRST_APP_UID) {
                    IBinder binder = ((IInterface) args.get(0)).asBinder();
                    if (binder != null) {
                        if (trackedClients.size() >= MAX_CLIENT_TRACK) {
                            trackedClients.clear();
                        }
                        trackedClients.put(binder, Integer.valueOf(effAppId));
                    }
                }
                Object ret = HookGuard.proceedOriginal(chain);
                if (!(ret instanceof Long)) {
                    return ret;
                }
                long pair = (Long) ret;
                int state = (int) (pair & 0xFFFFFFFFL);
                if ((state & A11Y_STATE_STRIP_MASK) == 0) {
                    return ret;
                }
                if (PolicyFlags.isAuditOnly(flags)) {
                    hub.record(effAppId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_ADD_CLIENT, PolicyFlags.ACT_AUDIT, true, state & A11Y_STATE_STRIP_MASK);
                    return ret;
                }
                int clean = state & ~A11Y_STATE_STRIP_MASK;
                long patched = (pair & 0xFFFFFFFF00000000L) | (clean & 0xFFFFFFFFL);
                hub.record(effAppId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_ADD_CLIENT, PolicyFlags.ACT_SANITIZED, false, state & A11Y_STATE_STRIP_MASK);
                return Long.valueOf(patched);
            }
        });

        List<Method> enMethods = CompatMatrix.findMethodsByName(amsCls, "getEnabledAccessibilityServiceList");
        HookGuard listGuard = hub.createGuard(PolicyFlags.PILLAR_A11Y, "A11Y_LIST");
        installed += CompatMatrix.hookMethods(module, enMethods, XposedInterface.PRIORITY_DEFAULT, listGuard, buildServiceListHandler(cl, engine, hub, PolicyFlags.VEC_A11Y_ENABLED_LIST));

        List<Method> instMethods = CompatMatrix.findMethodsByName(amsCls, "getInstalledAccessibilityServiceList");
        installed += CompatMatrix.hookMethods(module, instMethods, XposedInterface.PRIORITY_DEFAULT, listGuard, buildServiceListHandler(cl, engine, hub, PolicyFlags.VEC_A11Y_INSTALLED_LIST));

        List<Method> shortMethods = CompatMatrix.findMethodsByName(amsCls, "getAccessibilityShortcutTargets");
        HookGuard shortGuard = hub.createGuard(PolicyFlags.PILLAR_A11Y, "A11Y_SHORT");
        CompatMatrix.hookMethods(module, shortMethods, XposedInterface.PRIORITY_DEFAULT, shortGuard, new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                int callingUid = Binder.getCallingUid();
                int flags = engine.resolvePolicy(callingUid);
                if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_A11Y_COHERENT)) {
                    return HookGuard.proceedOriginal(chain);
                }
                int effAppId = engine.resolveEffectiveAppId(callingUid);
                if (PolicyFlags.isAuditOnly(flags)) {
                    Object ret = HookGuard.proceedOriginal(chain);
                    List<?> list = CompatMatrix.extractParceledList(ret);
                    if (list != null && !list.isEmpty()) {
                        hub.record(effAppId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_SHORTCUTS, PolicyFlags.ACT_AUDIT, true, list.size());
                    }
                    return ret;
                }
                hub.record(effAppId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_SHORTCUTS, PolicyFlags.ACT_SANITIZED, false, 0);
                Object exec = chain.getExecutable();
                if (exec instanceof Method && List.class.isAssignableFrom(((Method) exec).getReturnType())) {
                    return Collections.emptyList();
                }
                Object emptySlice = CompatMatrix.wrapParceledList(cl, Collections.emptyList());
                return emptySlice != null ? emptySlice : HookGuard.proceedOriginal(chain);
            }
        });

        Class<?> proxyCls = CompatMatrix.findClass(cl, "android.view.accessibility.IAccessibilityManagerClient$Stub$Proxy");
        if (proxyCls != null) {
            Method setState = CompatMatrix.findExactMethod(proxyCls, "setState", int.class);
            if (setState != null) {
                HookGuard pushGuard = hub.createGuard(PolicyFlags.PILLAR_A11Y, "A11Y_PUSH");
                CompatMatrix.hookMethod(module, setState, XposedInterface.PRIORITY_DEFAULT, pushGuard, new HookGuard.Handler() {
                    @Override
                    public Object handle(XposedInterface.Chain chain) throws Throwable {
                        Object self = chain.getThisObject();
                        if (!(self instanceof IInterface)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        IBinder binder = ((IInterface) self).asBinder();
                        Integer ownerAppId = binder != null ? trackedClients.get(binder) : null;
                        if (ownerAppId == null) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int flags = engine.resolvePolicy(ownerAppId);
                        if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_A11Y_COHERENT)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        List<Object> args = chain.getArgs();
                        if (args.isEmpty() || !(args.get(0) instanceof Integer)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int state = (Integer) args.get(0);
                        if ((state & A11Y_STATE_STRIP_MASK) == 0) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        if (PolicyFlags.isAuditOnly(flags)) {
                            hub.record(ownerAppId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_PUSH_STATE, PolicyFlags.ACT_AUDIT, true, state & A11Y_STATE_STRIP_MASK);
                            return HookGuard.proceedOriginal(chain);
                        }
                        int clean = state & ~A11Y_STATE_STRIP_MASK;
                        hub.record(ownerAppId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_PUSH_STATE, PolicyFlags.ACT_SANITIZED, false, state & A11Y_STATE_STRIP_MASK);
                        return HookGuard.proceedWithArgs(chain, new Object[]{Integer.valueOf(clean)});
                    }
                });
            }
        }

        hub.setPillarStatus(
            PolicyFlags.PILLAR_A11Y,
            installed > 0 ? TelemetryHub.HEALTH_OK : TelemetryHub.HEALTH_OFF,
            installed > 0 ? CompatMatrix.TIER_EXACT : CompatMatrix.TIER_NONE
        );
    }

    private static HookGuard.Handler buildServiceListHandler(
        final ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub,
        final int vectorCode
    ) {
        return new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                int callingUid = Binder.getCallingUid();
                int flags = engine.resolvePolicy(callingUid);
                if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_A11Y_COHERENT)) {
                    return HookGuard.proceedOriginal(chain);
                }
                int effAppId = engine.resolveEffectiveAppId(callingUid);
                if (PolicyFlags.isAuditOnly(flags)) {
                    Object ret = HookGuard.proceedOriginal(chain);
                    List<?> list = CompatMatrix.extractParceledList(ret);
                    if (list != null && !list.isEmpty()) {
                        hub.record(effAppId, PolicyFlags.PILLAR_A11Y, vectorCode, PolicyFlags.ACT_AUDIT, true, list.size());
                    }
                    return ret;
                }
                hub.record(effAppId, PolicyFlags.PILLAR_A11Y, vectorCode, PolicyFlags.ACT_SANITIZED, false, 0);
                Object exec = chain.getExecutable();
                if (exec instanceof Method && List.class.isAssignableFrom(((Method) exec).getReturnType())) {
                    return Collections.emptyList();
                }
                Object emptySlice = CompatMatrix.wrapParceledList(cl, Collections.emptyList());
                return emptySlice != null ? emptySlice : HookGuard.proceedOriginal(chain);
            }
        };
    }
}
