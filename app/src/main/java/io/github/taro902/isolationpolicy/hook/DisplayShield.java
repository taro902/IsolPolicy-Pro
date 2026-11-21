package io.github.taro902.isolationpolicy.hook;

import android.os.Binder;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.core.Logger;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class DisplayShield {

    private static final String TAG = "DisplayShield";

    public void install(XposedModule module, ClassLoader sysCl, PolicyEngine engine, TelemetryHub telemetry) {
        Class<?> binderCls;
        try {
            binderCls = Class.forName("com.android.server.display.DisplayManagerService$BinderService", false, sysCl);
        } catch (Throwable t) {
            Logger.e(TAG, "load DisplayManagerService$BinderService failed", t);
            telemetry.markHookState(PolicyFlags.HOOK_BIT_DISPLAY, TelemetryHub.STATE_FAILED);
            return;
        }

        int idsHooked = 0;
        int infoHooked = 0;

        for (Method m : binderCls.getDeclaredMethods()) {
            String name = m.getName();
            if ("getDisplayIds".equals(name) && m.getReturnType() == int[].class) {
                if (hookGetDisplayIds(module, m, engine, telemetry)) {
                    idsHooked++;
                }
            } else if ("getDisplayInfo".equals(name) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
                if (hookGetDisplayInfo(module, m, engine, telemetry)) {
                    infoHooked++;
                }
            }
        }

        if (idsHooked > 0 && infoHooked > 0) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_DISPLAY, TelemetryHub.STATE_OK);
        } else if (idsHooked > 0 || infoHooked > 0) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_DISPLAY, TelemetryHub.STATE_FALLBACK);
        } else {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_DISPLAY, TelemetryHub.STATE_FAILED);
        }
    }

    private boolean hookGetDisplayIds(
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
                        if (!engine.hasFlag(callingUid, PolicyFlags.FLAG_DISP_STEALTH)) {
                            return chain.proceed();
                        }
                        Object res = chain.proceed();
                        if (!(res instanceof int[])) {
                            return res;
                        }
                        int[] ids = (int[]) res;
                        if (ids.length == 0 || (ids.length == 1 && ids[0] == 0)) {
                            return ids;
                        }
                        int appId = engine.resolveEffectiveAppId(callingUid);
                        telemetry.record(appId, PolicyFlags.PILLAR_DISPLAY, PolicyFlags.VEC_DISP_IDS, PolicyFlags.ACT_HIDDEN);
                        return new int[]{0};
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook getDisplayIds failed", t);
            return false;
        }
    }

    private boolean hookGetDisplayInfo(
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
                        if (!engine.hasFlag(callingUid, PolicyFlags.FLAG_DISP_STEALTH)) {
                            return chain.proceed();
                        }
                        List<Object> args = chain.getArgs();
                        if (!args.isEmpty() && args.get(0) instanceof Integer) {
                            int displayId = (Integer) args.get(0);
                            if (displayId != 0) {
                                int appId = engine.resolveEffectiveAppId(callingUid);
                                telemetry.record(appId, PolicyFlags.PILLAR_DISPLAY, PolicyFlags.VEC_DISP_INFO, PolicyFlags.ACT_HIDDEN);
                                return null;
                            }
                        }
                        return chain.proceed();
                    });
            return true;
        } catch (Throwable t) {
            Logger.e(TAG, "hook getDisplayInfo failed", t);
            return false;
        }
    }
}