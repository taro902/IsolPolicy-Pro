package io.github.taro902.isolationpolicy.hook;

import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class IsolatedFoldShield {

    private IsolatedFoldShield() {
    }

    public static void install(
        XposedInterface module,
        ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub
    ) {
        Class<?> activeServicesCls = CompatMatrix.findClass(cl, "com.android.server.am.ActiveServices");
        final Class<?> serviceRecordCls = CompatMatrix.findClass(cl, "com.android.server.am.ServiceRecord");
        if (activeServicesCls == null || serviceRecordCls == null) {
            hub.setPillarStatus(PolicyFlags.PILLAR_ISO, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
            return;
        }

        final Field fServiceInfo = CompatMatrix.findField(serviceRecordCls, "serviceInfo");
        final Field fAppInfo = CompatMatrix.findField(serviceRecordCls, "appInfo");
        final Field fPkgName = CompatMatrix.findField(serviceRecordCls, "packageName");
        final Field fProcName = CompatMatrix.findField(serviceRecordCls, "processName");
        final Field fInstanceName = CompatMatrix.findField(serviceRecordCls, "instanceName");

        List<Method> all = CompatMatrix.findMethodsByName(activeServicesCls, "bringUpServiceLocked");
        List<Method> filtered = new ArrayList<Method>(all.size());
        for (Method m : all) {
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length > 0 && serviceRecordCls.isAssignableFrom(pt[0])) {
                filtered.add(m);
            }
        }
        if (filtered.isEmpty()) {
            hub.setPillarStatus(PolicyFlags.PILLAR_ISO, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
            return;
        }

        HookGuard guard = hub.createGuard(PolicyFlags.PILLAR_ISO, "ISO_FOLD");
        int count = CompatMatrix.hookMethods(module, filtered, XposedInterface.PRIORITY_DEFAULT, guard, new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                List<Object> args = chain.getArgs();
                if (args.isEmpty() || fServiceInfo == null || fAppInfo == null) {
                    return HookGuard.proceedOriginal(chain);
                }
                Object sr = args.get(0);
                if (sr == null) {
                    return HookGuard.proceedOriginal(chain);
                }
                ServiceInfo si = (ServiceInfo) fServiceInfo.get(sr);
                ApplicationInfo ai = (ApplicationInfo) fAppInfo.get(sr);
                if (si == null || ai == null) {
                    return HookGuard.proceedOriginal(chain);
                }
                if ((si.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) == 0) {
                    return HookGuard.proceedOriginal(chain);
                }
                String pkg = fPkgName != null ? (String) fPkgName.get(sr) : ai.packageName;
                int flags = engine.resolvePolicyForPackageOrUid(pkg, ai.uid);
                if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_ISO_FOLD)) {
                    return HookGuard.proceedOriginal(chain);
                }
                if ((si.flags & ServiceInfo.FLAG_EXTERNAL_SERVICE) != 0) {
                    return HookGuard.proceedOriginal(chain);
                }
                if (isProtectedRenderer(pkg, si.processName, si.name)) {
                    return HookGuard.proceedOriginal(chain);
                }
                if (fInstanceName != null) {
                    Object inst = fInstanceName.get(sr);
                    if (inst instanceof String && !((String) inst).isEmpty()) {
                        return HookGuard.proceedOriginal(chain);
                    }
                }
                if ((si.flags & ServiceInfo.FLAG_USE_APP_ZYGOTE) != 0
                    && PolicyFlags.azMode(flags) == PolicyFlags.AZ_OFF
                ) {
                    return HookGuard.proceedOriginal(chain);
                }

                int appId = PolicyEngine.toAppId(ai.uid);
                if (PolicyFlags.isAuditOnly(flags)) {
                    hub.record(appId, PolicyFlags.PILLAR_ISO, PolicyFlags.VEC_ISO_FOLD_MATCH, PolicyFlags.ACT_AUDIT, true, 0);
                    return HookGuard.proceedOriginal(chain);
                }

                int origFlags = si.flags;
                String origProc = fProcName != null ? (String) fProcName.get(sr) : null;
                si.flags = origFlags & ~(ServiceInfo.FLAG_ISOLATED_PROCESS | ServiceInfo.FLAG_USE_APP_ZYGOTE);
                if (fProcName != null && ai.processName != null && !ai.processName.isEmpty()) {
                    fProcName.set(sr, ai.processName);
                }
                try {
                    Object ret = HookGuard.proceedOriginal(chain);
                    hub.record(appId, PolicyFlags.PILLAR_ISO, PolicyFlags.VEC_ISO_FOLD_SERVICE, PolicyFlags.ACT_FOLDED, false, 0);
                    return ret;
                } catch (Throwable t) {
                    si.flags = origFlags;
                    if (fProcName != null) {
                        fProcName.set(sr, origProc);
                    }
                    throw t;
                }
            }
        });

        hub.setPillarStatus(
            PolicyFlags.PILLAR_ISO,
            count > 0 ? TelemetryHub.HEALTH_OK : TelemetryHub.HEALTH_OFF,
            count > 0 ? CompatMatrix.TIER_OVERLOAD : CompatMatrix.TIER_NONE
        );
    }

    public static boolean isProtectedRenderer(String pkg, String procName, String svcName) {
        return containsRendererToken(pkg) || containsRendererToken(procName) || containsRendererToken(svcName);
    }

    private static boolean containsRendererToken(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        String low = s.toLowerCase(Locale.US);
        return low.contains("webview")
            || low.contains("trichrome")
            || low.contains("chromium")
            || low.contains("chrome")
            || low.contains("sandboxed_process")
            || low.contains("privileged_process");
    }
}
