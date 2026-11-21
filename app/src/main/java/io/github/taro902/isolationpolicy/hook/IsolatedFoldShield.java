package io.github.taro902.isolationpolicy.hook;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.core.Logger;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class IsolatedFoldShield {

    private static final String TAG = "IsolatedFoldShield";
    private static final int FLAG_ISOLATED = 0x0002;
    private static final int FLAG_EXTERNAL = 0x0004;
    private static final int FLAG_APP_ZYGOTE = 0x0008;

    private static final Set<String> PROTECTED_SANDBOX_PKGS = new HashSet<>();

    static {
        PROTECTED_SANDBOX_PKGS.add("com.google.android.webview");
        PROTECTED_SANDBOX_PKGS.add("com.android.webview");
        PROTECTED_SANDBOX_PKGS.add("com.android.chrome");
        PROTECTED_SANDBOX_PKGS.add("com.chrome.beta");
        PROTECTED_SANDBOX_PKGS.add("com.chrome.dev");
        PROTECTED_SANDBOX_PKGS.add("com.chrome.canary");
        PROTECTED_SANDBOX_PKGS.add("com.brave.browser");
        PROTECTED_SANDBOX_PKGS.add("org.chromium.webview_shell");
        PROTECTED_SANDBOX_PKGS.add("com.google.android.trichromelibrary");
        PROTECTED_SANDBOX_PKGS.add("org.mozilla.firefox");
        PROTECTED_SANDBOX_PKGS.add("com.microsoft.emmx");
        PROTECTED_SANDBOX_PKGS.add("com.sec.android.app.sbrowser");
        PROTECTED_SANDBOX_PKGS.add("com.android.systemui");
        PROTECTED_SANDBOX_PKGS.add("com.google.android.gms");
    }

    private volatile Field fServiceInfo;
    private volatile Field fAppInfo;
    private volatile Field fDefiningUid;
    private volatile Field fIsolatedProc;
    private volatile Field fName;
    private volatile Field fInstanceName;

    public void install(XposedModule module, ClassLoader sysCl, PolicyEngine engine, TelemetryHub telemetry) {
        Class<?> activeServicesCls;
        Class<?> serviceRecordCls;
        try {
            activeServicesCls = Class.forName("com.android.server.am.ActiveServices", false, sysCl);
            serviceRecordCls = Class.forName("com.android.server.am.ServiceRecord", false, sysCl);
        } catch (Throwable t) {
            Logger.e(TAG, "class load failed", t);
            telemetry.markHookState(PolicyFlags.HOOK_BIT_ISO_FOLD, TelemetryHub.STATE_FAILED);
            return;
        }

        fServiceInfo = findField(serviceRecordCls, "serviceInfo");
        fAppInfo = findField(serviceRecordCls, "appInfo");
        fDefiningUid = findField(serviceRecordCls, "definingUid");
        fIsolatedProc = findField(serviceRecordCls, "isolatedProc");
        fName = findField(serviceRecordCls, "name");
        fInstanceName = findField(serviceRecordCls, "instanceName");

        if (fServiceInfo == null || fAppInfo == null) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_ISO_FOLD, TelemetryHub.STATE_FAILED);
            return;
        }

        int hooked = 0;
        for (Method m : activeServicesCls.getDeclaredMethods()) {
            if (!"bringUpServiceLocked".equals(m.getName())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 0 || !serviceRecordCls.isAssignableFrom(params[0])) {
                continue;
            }
            try {
                module.deoptimize(m);
                module.hook(m)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object sr = chain.getArg(0);
                            if (sr != null) {
                                evaluateAndFold(sr, engine, telemetry);
                            }
                            return chain.proceed();
                        });
                hooked++;
            } catch (Throwable t) {
                Logger.e(TAG, "hook bringUpServiceLocked failed", t);
            }
        }

        telemetry.markHookState(
                PolicyFlags.HOOK_BIT_ISO_FOLD,
                hooked > 0 ? TelemetryHub.STATE_OK : TelemetryHub.STATE_FAILED
        );
    }

    private void evaluateAndFold(Object sr, PolicyEngine engine, TelemetryHub telemetry) {
        try {
            Object siObj = fServiceInfo.get(sr);
            Object aiObj = fAppInfo.get(sr);
            if (!(siObj instanceof ServiceInfo) || !(aiObj instanceof ApplicationInfo)) {
                return;
            }
            ServiceInfo sInfo = (ServiceInfo) siObj;
            ApplicationInfo aInfo = (ApplicationInfo) aiObj;

            int sFlags = sInfo.flags;
            if ((sFlags & FLAG_ISOLATED) == 0) {
                return;
            }
            if ((sFlags & FLAG_EXTERNAL) != 0 || (sFlags & FLAG_APP_ZYGOTE) != 0) {
                return;
            }

            if (fIsolatedProc != null && fIsolatedProc.get(sr) != null) {
                return;
            }

            if (aInfo.uid < 10000 || aInfo.packageName == null) {
                return;
            }
            if (PROTECTED_SANDBOX_PKGS.contains(aInfo.packageName)) {
                return;
            }

            if (fDefiningUid != null) {
                int defUid = fDefiningUid.getInt(sr);
                if (defUid > 0 && defUid != aInfo.uid) {
                    return;
                }
            }

            if (fName != null && fInstanceName != null) {
                Object nObj = fName.get(sr);
                Object iObj = fInstanceName.get(sr);
                if (nObj instanceof ComponentName && iObj instanceof ComponentName && !nObj.equals(iObj)) {
                    return;
                }
            }

            if (!engine.hasFlagForUidOrPackage(aInfo.uid, aInfo.packageName, PolicyFlags.FLAG_ISO_FOLD)) {
                return;
            }

            sInfo.flags = sFlags & ~FLAG_ISOLATED;
            telemetry.record(
                    aInfo.uid % 100000,
                    PolicyFlags.PILLAR_ISO_FOLD,
                    PolicyFlags.VEC_ISO_SERVICE,
                    PolicyFlags.ACT_FOLDED
            );
        } catch (Throwable ignored) {
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
}