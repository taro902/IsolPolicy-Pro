package io.github.taro902.isolationpolicy.hook;

import android.os.Binder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class DisplayShield {

    private static final ThreadLocal<Boolean> IN_GET_IDS = new ThreadLocal<Boolean>();

    private DisplayShield() {
    }

    public static void install(
        XposedInterface module,
        ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub
    ) {
        Class<?> binderCls = CompatMatrix.findClass(cl, "com.android.server.display.DisplayManagerService$BinderService");
        if (binderCls == null) {
            hub.setPillarStatus(PolicyFlags.PILLAR_DISP, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
            return;
        }

        int installed = 0;

        List<Method> idMethods = CompatMatrix.findMethodsByName(binderCls, "getDisplayIds");
        HookGuard idsGuard = hub.createGuard(PolicyFlags.PILLAR_DISP, "DISP_IDS");
        installed += CompatMatrix.hookMethods(module, idMethods, XposedInterface.PRIORITY_DEFAULT, idsGuard, new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                if (Boolean.TRUE.equals(IN_GET_IDS.get())) {
                    return HookGuard.proceedOriginal(chain);
                }
                int callingUid = Binder.getCallingUid();
                int flags = engine.resolvePolicy(callingUid);
                if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DISP_STEALTH)) {
                    return HookGuard.proceedOriginal(chain);
                }
                IN_GET_IDS.set(Boolean.TRUE);
                try {
                    Object ret = HookGuard.proceedOriginal(chain);
                    if (!(ret instanceof int[])) {
                        return ret;
                    }
                    int[] ids = (int[]) ret;
                    boolean hasSecondary = ids.length > 1 || (ids.length == 1 && ids[0] != 0);
                    if (!hasSecondary) {
                        return ret;
                    }
                    int effAppId = engine.resolveEffectiveAppId(callingUid);
                    if (PolicyFlags.isAuditOnly(flags)) {
                        hub.record(effAppId, PolicyFlags.PILLAR_DISP, PolicyFlags.VEC_DISP_GET_IDS, PolicyFlags.ACT_AUDIT, true, ids.length);
                        return ret;
                    }
                    hub.record(effAppId, PolicyFlags.PILLAR_DISP, PolicyFlags.VEC_DISP_GET_IDS, PolicyFlags.ACT_SANITIZED, false, ids.length);
                    return new int[]{0};
                } finally {
                    IN_GET_IDS.remove();
                }
            }
        });

        Method infoMethod = CompatMatrix.findExactMethod(binderCls, "getDisplayInfo", int.class);
        if (infoMethod != null) {
            HookGuard infoGuard = hub.createGuard(PolicyFlags.PILLAR_DISP, "DISP_INFO");
            installed += CompatMatrix.hookMethod(module, infoMethod, XposedInterface.PRIORITY_DEFAULT, infoGuard, new HookGuard.Handler() {
                @Override
                public Object handle(XposedInterface.Chain chain) throws Throwable {
                    List<Object> args = chain.getArgs();
                    if (args.isEmpty() || !(args.get(0) instanceof Integer)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    int displayId = (Integer) args.get(0);
                    if (displayId == 0) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    int callingUid = Binder.getCallingUid();
                    int flags = engine.resolvePolicy(callingUid);
                    if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DISP_STEALTH)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    int effAppId = engine.resolveEffectiveAppId(callingUid);
                    if (PolicyFlags.isAuditOnly(flags)) {
                        Object ret = HookGuard.proceedOriginal(chain);
                        if (ret != null) {
                            hub.record(effAppId, PolicyFlags.PILLAR_DISP, PolicyFlags.VEC_DISP_GET_INFO, PolicyFlags.ACT_AUDIT, true, displayId);
                        }
                        return ret;
                    }
                    hub.record(effAppId, PolicyFlags.PILLAR_DISP, PolicyFlags.VEC_DISP_GET_INFO, PolicyFlags.ACT_SUPPRESSED, false, displayId);
                    return null;
                }
            });
        }

        Class<?> cbCls = CompatMatrix.findClass(cl, "com.android.server.display.DisplayManagerService$CallbackRecord");
        if (cbCls != null) {
            final Field fUid = CompatMatrix.findField(cbCls, "mUid");
            Method pushMethod = CompatMatrix.findExactMethod(cbCls, "notifyDisplayEventAsync", int.class, int.class);
            if (pushMethod != null && fUid != null) {
                final boolean returnsBoolean = pushMethod.getReturnType() == boolean.class;
                HookGuard pushGuard = hub.createGuard(PolicyFlags.PILLAR_DISP, "DISP_PUSH");
                CompatMatrix.hookMethod(module, pushMethod, XposedInterface.PRIORITY_DEFAULT, pushGuard, new HookGuard.Handler() {
                    @Override
                    public Object handle(XposedInterface.Chain chain) throws Throwable {
                        List<Object> args = chain.getArgs();
                        if (args.isEmpty() || !(args.get(0) instanceof Integer)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int displayId = (Integer) args.get(0);
                        if (displayId == 0) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        Object self = chain.getThisObject();
                        if (self == null) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int uid = fUid.getInt(self);
                        int flags = engine.resolvePolicy(uid);
                        if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DISP_STEALTH)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int effAppId = engine.resolveEffectiveAppId(uid);
                        if (PolicyFlags.isAuditOnly(flags)) {
                            hub.record(effAppId, PolicyFlags.PILLAR_DISP, PolicyFlags.VEC_DISP_PUSH_EVENT, PolicyFlags.ACT_AUDIT, true, displayId);
                            return HookGuard.proceedOriginal(chain);
                        }
                        hub.record(effAppId, PolicyFlags.PILLAR_DISP, PolicyFlags.VEC_DISP_PUSH_EVENT, PolicyFlags.ACT_SUPPRESSED, false, displayId);
                        return returnsBoolean ? Boolean.TRUE : null;
                    }
                });
            }
        }

        hub.setPillarStatus(
            PolicyFlags.PILLAR_DISP,
            installed > 0 ? TelemetryHub.HEALTH_OK : TelemetryHub.HEALTH_OFF,
            installed > 0 ? CompatMatrix.TIER_EXACT : CompatMatrix.TIER_NONE
        );
    }
}
