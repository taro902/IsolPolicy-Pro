package io.github.taro902.isolationpolicy.hook;

import android.os.Binder;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.core.Logger;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class PackageChangeShield {

    private static final String TAG = "PackageChangeShield";

    private static final String[] PRIMARY_CANDIDATES = {
            "com.android.server.pm.IPackageManagerBase",
            "com.android.server.pm.PackageManagerService$IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService"
    };

    public void install(XposedModule module, ClassLoader sysCl, PolicyEngine engine, TelemetryHub telemetry) {
        int hooked = 0;
        for (String clsName : PRIMARY_CANDIDATES) {
            hooked += hookClassIfPresent(module, sysCl, clsName, engine, telemetry);
        }

        if (hooked == 0) {
            int fallback = hookClassIfPresent(module, sysCl, "com.android.server.pm.ComputerEngine", engine, telemetry);
            if (fallback > 0) {
                telemetry.markHookState(PolicyFlags.HOOK_BIT_PKG_CHANGE, TelemetryHub.STATE_FALLBACK);
                return;
            }
            telemetry.markHookState(PolicyFlags.HOOK_BIT_PKG_CHANGE, TelemetryHub.STATE_FAILED);
            return;
        }

        telemetry.markHookState(PolicyFlags.HOOK_BIT_PKG_CHANGE, TelemetryHub.STATE_OK);
    }

    private int hookClassIfPresent(
            XposedModule module,
            ClassLoader sysCl,
            String className,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        Class<?> cls;
        try {
            cls = Class.forName(className, false, sysCl);
        } catch (Throwable ignored) {
            return 0;
        }

        int count = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!"getChangedPackages".equals(m.getName())) {
                continue;
            }
            if (m.getParameterCount() < 2) {
                continue;
            }
            try {
                module.deoptimize(m);
                module.hook(m)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            int callingUid = Binder.getCallingUid();
                            if (!engine.hasFlag(callingUid, PolicyFlags.FLAG_PKG_CHANGES)) {
                                return chain.proceed();
                            }
                            int appId = engine.resolveEffectiveAppId(callingUid);
                            telemetry.record(appId, PolicyFlags.PILLAR_PKG_CHANGE, PolicyFlags.VEC_PKG_CHANGED, PolicyFlags.ACT_HIDDEN);
                            return null;
                        });
                count++;
            } catch (Throwable t) {
                Logger.e(TAG, "hook getChangedPackages failed on " + className, t);
            }
        }
        return count;
    }
}