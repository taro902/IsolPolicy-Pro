package io.github.taro902.isolationpolicy.hook;

import android.net.Uri;
import android.os.Binder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class ObserverCoherenceShield {

    private ObserverCoherenceShield() {
    }

    public static boolean shouldSuppressSettingsUri(String authority, String path, int flags) {
        if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_OBS_COHERENCE)) {
            return false;
        }
        if (!"settings".equals(authority) || path == null || path.isEmpty()) {
            return false;
        }
        int start = 0;
        while (start < path.length() && path.charAt(start) == '/') {
            start++;
        }
        int slash = path.indexOf('/', start);
        if (slash <= start || slash + 1 >= path.length()) {
            return false;
        }
        String table = path.substring(start, slash);
        if (!"global".equals(table) && !"secure".equals(table) && !"system".equals(table)) {
            return false;
        }
        int end = path.indexOf('/', slash + 1);
        String key = end > slash + 1 ? path.substring(slash + 1, end) : path.substring(slash + 1);
        return SettingsShield.isGuardedKeyForFlags(key, flags);
    }

    public static void install(
        XposedInterface module,
        ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub
    ) {
        Class<?> collectorCls = CompatMatrix.findClass(cl, "com.android.server.content.ContentService$ObserverCollector");
        if (collectorCls != null) {
            List<Method> allCollect = CompatMatrix.findMethodsByName(collectorCls, "collect");
            ArrayList<Method> validCollect = new ArrayList<Method>(allCollect.size());
            for (Method m : allCollect) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length >= 4 && pt[1] == int.class && Uri.class.isAssignableFrom(pt[3])) {
                    validCollect.add(m);
                }
            }
            if (!validCollect.isEmpty()) {
                HookGuard guard = hub.createGuard(PolicyFlags.PILLAR_OBS, "OBS_COLLECT");
                int cnt = CompatMatrix.hookMethods(module, validCollect, XposedInterface.PRIORITY_DEFAULT, guard, new HookGuard.Handler() {
                    @Override
                    public Object handle(XposedInterface.Chain chain) throws Throwable {
                        List<Object> args = chain.getArgs();
                        if (args.size() < 4 || !(args.get(1) instanceof Integer) || !(args.get(3) instanceof Uri)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int targetUid = (Integer) args.get(1);
                        int flags = engine.resolvePolicy(targetUid);
                        if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_OBS_COHERENCE)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        Uri uri = (Uri) args.get(3);
                        if (!shouldSuppressSettingsUri(uri.getAuthority(), uri.getPath(), flags)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int effAppId = engine.resolveEffectiveAppId(targetUid);
                        if (PolicyFlags.isAuditOnly(flags)) {
                            hub.record(effAppId, PolicyFlags.PILLAR_OBS, PolicyFlags.VEC_OBS_SETTINGS_PUSH, PolicyFlags.ACT_AUDIT, true, 0);
                            return HookGuard.proceedOriginal(chain);
                        }
                        hub.record(effAppId, PolicyFlags.PILLAR_OBS, PolicyFlags.VEC_OBS_SETTINGS_PUSH, PolicyFlags.ACT_SUPPRESSED, false, 0);
                        return null;
                    }
                });
                if (cnt > 0) {
                    hub.setPillarStatus(PolicyFlags.PILLAR_OBS, TelemetryHub.HEALTH_OK, CompatMatrix.TIER_EXACT);
                    return;
                }
            }
        }

        Class<?> csCls = CompatMatrix.findClass(cl, "com.android.server.content.ContentService");
        if (csCls != null) {
            List<Method> regMethods = CompatMatrix.findMethodsByName(csCls, "registerContentObserver");
            ArrayList<Method> validReg = new ArrayList<Method>(regMethods.size());
            for (Method m : regMethods) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length >= 3 && Uri.class.isAssignableFrom(pt[0])) {
                    validReg.add(m);
                }
            }
            if (!validReg.isEmpty()) {
                HookGuard fbGuard = hub.createGuard(PolicyFlags.PILLAR_OBS, "OBS_REG_FB");
                int cnt = CompatMatrix.hookMethods(module, validReg, XposedInterface.PRIORITY_DEFAULT, fbGuard, new HookGuard.Handler() {
                    @Override
                    public Object handle(XposedInterface.Chain chain) throws Throwable {
                        List<Object> args = chain.getArgs();
                        if (args.isEmpty() || !(args.get(0) instanceof Uri)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int callingUid = Binder.getCallingUid();
                        int flags = engine.resolvePolicy(callingUid);
                        if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_OBS_COHERENCE)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        Uri uri = (Uri) args.get(0);
                        if (!shouldSuppressSettingsUri(uri.getAuthority(), uri.getPath(), flags)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int effAppId = engine.resolveEffectiveAppId(callingUid);
                        if (PolicyFlags.isAuditOnly(flags)) {
                            hub.record(effAppId, PolicyFlags.PILLAR_OBS, PolicyFlags.VEC_OBS_REGISTER_FALLBACK, PolicyFlags.ACT_AUDIT, true, 0);
                            return HookGuard.proceedOriginal(chain);
                        }
                        hub.record(effAppId, PolicyFlags.PILLAR_OBS, PolicyFlags.VEC_OBS_REGISTER_FALLBACK, PolicyFlags.ACT_SUPPRESSED, false, 0);
                        return null;
                    }
                });
                if (cnt > 0) {
                    hub.setPillarStatus(PolicyFlags.PILLAR_OBS, TelemetryHub.HEALTH_FALLBACK, CompatMatrix.TIER_FALLBACK);
                    return;
                }
            }
        }

        hub.setPillarStatus(PolicyFlags.PILLAR_OBS, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
    }
}
