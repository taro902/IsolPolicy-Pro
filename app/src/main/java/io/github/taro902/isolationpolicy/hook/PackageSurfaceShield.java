package io.github.taro902.isolationpolicy.hook;

import android.content.pm.PackageInstaller;
import android.os.Binder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class PackageSurfaceShield {

    private static final ThreadLocal<Boolean> IN_ALL_SESSIONS = new ThreadLocal<Boolean>();

    private PackageSurfaceShield() {
    }

    public static void install(
        XposedInterface module,
        final ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub
    ) {
        int installed = 0;
        int resolvedTier = CompatMatrix.TIER_NONE;

        CompatMatrix.ResolvedTarget changedTarget = CompatMatrix.resolveTarget(
            cl,
            new String[]{
                "com.android.server.pm.IPackageManagerBase",
                "com.android.server.pm.PackageManagerService$IPackageManagerImpl",
                "com.android.server.pm.PackageManagerService"
            },
            "getChangedPackages",
            new Class<?>[]{int.class, int.class},
            new String[]{"com.android.server.pm.ComputerEngine"}
        );

        if (changedTarget.isResolved()) {
            HookGuard changedGuard = hub.createGuard(PolicyFlags.PILLAR_PKG, "PKG_CHANGED");
            int cnt = CompatMatrix.hookMethods(module, changedTarget.methods, XposedInterface.PRIORITY_DEFAULT, changedGuard, new HookGuard.Handler() {
                @Override
                public Object handle(XposedInterface.Chain chain) throws Throwable {
                    int callingUid = Binder.getCallingUid();
                    int flags = engine.resolvePolicy(callingUid);
                    if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_PKG_CHANGES)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    int effAppId = engine.resolveEffectiveAppId(callingUid);
                    if (PolicyFlags.isAuditOnly(flags)) {
                        Object ret = HookGuard.proceedOriginal(chain);
                        if (ret != null) {
                            hub.record(effAppId, PolicyFlags.PILLAR_PKG, PolicyFlags.VEC_PKG_CHANGED, PolicyFlags.ACT_AUDIT, true, 0);
                        }
                        return ret;
                    }
                    hub.record(effAppId, PolicyFlags.PILLAR_PKG, PolicyFlags.VEC_PKG_CHANGED, PolicyFlags.ACT_SUPPRESSED, false, 0);
                    return null;
                }
            });
            installed += cnt;
            if (cnt > 0) {
                resolvedTier = changedTarget.tier;
            }
        }

        Class<?> installerCls = CompatMatrix.findClass(cl, "com.android.server.pm.PackageInstallerService");
        if (installerCls != null) {
            List<Method> sessionMethods = CompatMatrix.findMethodsByName(installerCls, "getAllSessions");
            if (!sessionMethods.isEmpty()) {
                final Field fInstallerUid = CompatMatrix.findField(PackageInstaller.SessionInfo.class, "installerUid");
                final Field fInstallerPkg = CompatMatrix.findField(PackageInstaller.SessionInfo.class, "installerPackageName");
                final Field fAppPkg = CompatMatrix.findField(PackageInstaller.SessionInfo.class, "appPackageName");
                HookGuard sessGuard = hub.createGuard(PolicyFlags.PILLAR_PKG, "PKG_SESSIONS");
                int sCnt = CompatMatrix.hookMethods(module, sessionMethods, XposedInterface.PRIORITY_DEFAULT, sessGuard, new HookGuard.Handler() {
                    @Override
                    public Object handle(XposedInterface.Chain chain) throws Throwable {
                        if (Boolean.TRUE.equals(IN_ALL_SESSIONS.get())) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        int callingUid = Binder.getCallingUid();
                        List<Object> args = chain.getArgs();
                        if (callingUid < PolicyEngine.FIRST_APP_UID && args.size() >= 2 && args.get(1) instanceof Integer) {
                            int argUid = (Integer) args.get(1);
                            if (argUid >= PolicyEngine.FIRST_APP_UID) {
                                callingUid = argUid;
                            }
                        }
                        int flags = engine.resolvePolicy(callingUid);
                        if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_PKG_CHANGES)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        IN_ALL_SESSIONS.set(Boolean.TRUE);
                        try {
                            Object ret = HookGuard.proceedOriginal(chain);
                            List<?> items = CompatMatrix.extractParceledList(ret);
                            if (items == null || items.isEmpty()) {
                                return ret;
                            }
                            int effAppId = engine.resolveEffectiveAppId(callingUid);
                            String callerPkg = engine.getSnapshot().packageForAppId(effAppId);
                            ArrayList<Object> kept = new ArrayList<Object>(items.size());
                            for (int i = 0; i < items.size(); i++) {
                                Object item = items.get(i);
                                if (isOwnedSession(item, effAppId, callerPkg, fInstallerUid, fInstallerPkg, fAppPkg)) {
                                    kept.add(item);
                                }
                            }
                            int removed = items.size() - kept.size();
                            if (removed <= 0) {
                                return ret;
                            }
                            if (PolicyFlags.isAuditOnly(flags)) {
                                hub.record(effAppId, PolicyFlags.PILLAR_PKG, PolicyFlags.VEC_PKG_INSTALL_SESSIONS, PolicyFlags.ACT_AUDIT, true, removed);
                                return ret;
                            }
                            Object exec = chain.getExecutable();
                            if (exec instanceof Method && List.class.isAssignableFrom(((Method) exec).getReturnType())) {
                                hub.record(effAppId, PolicyFlags.PILLAR_PKG, PolicyFlags.VEC_PKG_INSTALL_SESSIONS, PolicyFlags.ACT_SANITIZED, false, removed);
                                return kept;
                            }
                            Object wrapped = CompatMatrix.wrapParceledList(cl, kept);
                            if (wrapped != null) {
                                hub.record(effAppId, PolicyFlags.PILLAR_PKG, PolicyFlags.VEC_PKG_INSTALL_SESSIONS, PolicyFlags.ACT_SANITIZED, false, removed);
                                return wrapped;
                            }
                            return ret;
                        } finally {
                            IN_ALL_SESSIONS.remove();
                        }
                    }
                });
                installed += sCnt;
                if (sCnt > 0 && resolvedTier == CompatMatrix.TIER_NONE) {
                    resolvedTier = CompatMatrix.TIER_OVERLOAD;
                }
            }
        }

        if (installed > 0) {
            int health = resolvedTier == CompatMatrix.TIER_FALLBACK ? TelemetryHub.HEALTH_FALLBACK : TelemetryHub.HEALTH_OK;
            hub.setPillarStatus(PolicyFlags.PILLAR_PKG, health, resolvedTier);
        } else {
            hub.setPillarStatus(PolicyFlags.PILLAR_PKG, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
        }
    }

    private static boolean isOwnedSession(
        Object item,
        int effAppId,
        String callerPkg,
        Field fInstallerUid,
        Field fInstallerPkg,
        Field fAppPkg
    ) {
        if (item == null) {
            return false;
        }
        try {
            if (fInstallerUid != null) {
                int iUid = fInstallerUid.getInt(item);
                if (iUid >= PolicyEngine.FIRST_APP_UID && PolicyEngine.toAppId(iUid) == effAppId) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        if (callerPkg != null && !callerPkg.isEmpty()) {
            try {
                if (item instanceof PackageInstaller.SessionInfo) {
                    PackageInstaller.SessionInfo si = (PackageInstaller.SessionInfo) item;
                    if (callerPkg.equals(si.getInstallerPackageName()) || callerPkg.equals(si.getAppPackageName())) {
                        return true;
                    }
                } else {
                    if (fInstallerPkg != null && callerPkg.equals(fInstallerPkg.get(item))) {
                        return true;
                    }
                    if (fAppPkg != null && callerPkg.equals(fAppPkg.get(item))) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
