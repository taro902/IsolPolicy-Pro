package io.github.taro902.isolationpolicy.hook;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class AppZygoteShield {

    private AppZygoteShield() {
    }

    public static void install(
        XposedInterface module,
        ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub
    ) {
        final Class<?> hostingCls = CompatMatrix.findClass(cl, "com.android.server.am.HostingRecord");
        final Class<?> processListCls = CompatMatrix.findClass(cl, "com.android.server.am.ProcessList");
        final Class<?> processRecordCls = CompatMatrix.findClass(cl, "com.android.server.am.ProcessRecord");

        if (hostingCls == null || processListCls == null) {
            hub.setPillarStatus(PolicyFlags.PILLAR_AZ, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
            return;
        }

        final int appZygoteVal = CompatMatrix.readStaticInt(hostingCls, "APP_ZYGOTE", 2);
        final int regularZygoteVal = CompatMatrix.readStaticInt(hostingCls, "REGULAR_ZYGOTE", 0);
        final Field fHostingZygote = CompatMatrix.findField(hostingCls, "mHostingZygote");
        final Field fDefPkg = CompatMatrix.findField(hostingCls, "mDefiningPackageName");
        final Field fDefUid = CompatMatrix.findField(hostingCls, "mDefiningUid");
        final Field fPrUid = CompatMatrix.findField(processRecordCls, "uid");
        final Field fPrInfo = CompatMatrix.findField(processRecordCls, "info");

        HookGuard guardCreate = hub.createGuard(PolicyFlags.PILLAR_AZ, "AZ_CREATE");
        HookGuard guardUses = hub.createGuard(PolicyFlags.PILLAR_AZ, "AZ_USES");
        HookGuard guardStart = hub.createGuard(PolicyFlags.PILLAR_AZ, "AZ_START");
        HookGuard guardRemove = hub.createGuard(PolicyFlags.PILLAR_AZ, "AZ_REMOVE");

        List<Method> byAzMethods = CompatMatrix.findMethodsByName(hostingCls, "byAppZygote");
        int hCreate = CompatMatrix.hookMethods(module, byAzMethods, XposedInterface.PRIORITY_DEFAULT, guardCreate, new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                List<Object> args = chain.getArgs();
                String defPkg = null;
                int defUid = -1;
                for (int i = 0; i < args.size(); i++) {
                    Object a = args.get(i);
                    if (defPkg == null && a instanceof String) {
                        defPkg = (String) a;
                    } else if (defPkg == null && a instanceof ComponentName) {
                        defPkg = ((ComponentName) a).getPackageName();
                    } else if (defUid < 0 && a instanceof Integer) {
                        int v = (Integer) a;
                        if (v >= PolicyEngine.FIRST_APP_UID) {
                            defUid = v;
                        }
                    }
                }
                int flags = engine.resolvePolicyForPackageOrUid(defPkg, defUid);
                int az = PolicyFlags.azMode(flags);
                Object rec = HookGuard.proceedOriginal(chain);
                if (rec == null || az == PolicyFlags.AZ_OFF) {
                    return rec;
                }
                int appId = defUid >= PolicyEngine.FIRST_APP_UID ? PolicyEngine.toAppId(defUid) : -1;
                if (PolicyFlags.isAuditOnly(flags)) {
                    hub.record(appId, PolicyFlags.PILLAR_AZ, PolicyFlags.VEC_AZ_HOSTING_CREATE, PolicyFlags.ACT_AUDIT, true, az);
                    return rec;
                }
                if ((az == PolicyFlags.AZ_AUTO || az == PolicyFlags.AZ_DOWNGRADE) && fHostingZygote != null) {
                    fHostingZygote.setInt(rec, regularZygoteVal);
                    hub.record(appId, PolicyFlags.PILLAR_AZ, PolicyFlags.VEC_AZ_HOSTING_CREATE, PolicyFlags.ACT_DOWNGRADED, false, az);
                }
                return rec;
            }
        });

        List<Method> usesMethods = CompatMatrix.findMethodsByName(hostingCls, "usesAppZygote");
        CompatMatrix.hookMethods(module, usesMethods, XposedInterface.PRIORITY_DEFAULT, guardUses, new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                Object self = chain.getThisObject();
                if (self == null || fHostingZygote == null) {
                    return HookGuard.proceedOriginal(chain);
                }
                int z = fHostingZygote.getInt(self);
                if (z != appZygoteVal) {
                    return HookGuard.proceedOriginal(chain);
                }
                String defPkg = fDefPkg != null ? (String) fDefPkg.get(self) : null;
                int defUid = fDefUid != null ? fDefUid.getInt(self) : -1;
                int flags = engine.resolvePolicyForPackageOrUid(defPkg, defUid);
                int az = PolicyFlags.azMode(flags);
                if (az == PolicyFlags.AZ_AUTO || az == PolicyFlags.AZ_DOWNGRADE) {
                    int appId = defUid >= PolicyEngine.FIRST_APP_UID ? PolicyEngine.toAppId(defUid) : -1;
                    if (PolicyFlags.isAuditOnly(flags)) {
                        hub.record(appId, PolicyFlags.PILLAR_AZ, PolicyFlags.VEC_AZ_USES_QUERY, PolicyFlags.ACT_AUDIT, true, az);
                        return HookGuard.proceedOriginal(chain);
                    }
                    hub.record(appId, PolicyFlags.PILLAR_AZ, PolicyFlags.VEC_AZ_USES_QUERY, PolicyFlags.ACT_DOWNGRADED, false, az);
                    return Boolean.FALSE;
                }
                return HookGuard.proceedOriginal(chain);
            }
        });

        List<Method> startLockedMethods = CompatMatrix.findMethodsByName(processListCls, "startProcessLocked");
        List<Method> startFallbackMethods = CompatMatrix.findMethodsByName(processListCls, "startProcess");

        HookGuard.Handler startHandler = new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                List<Object> args = chain.getArgs();
                Object hosting = null;
                ApplicationInfo ai = null;
                Object prArg = null;
                int isoUidArg = -1;

                for (int i = 0; i < args.size(); i++) {
                    Object a = args.get(i);
                    if (a == null) {
                        continue;
                    }
                    if (hosting == null && hostingCls.isInstance(a)) {
                        hosting = a;
                    } else if (ai == null && a instanceof ApplicationInfo) {
                        ai = (ApplicationInfo) a;
                    } else if (prArg == null && processRecordCls != null && processRecordCls.isInstance(a)) {
                        prArg = a;
                    } else if (isoUidArg < 0 && a instanceof Integer) {
                        int v = (Integer) a;
                        if (PolicyEngine.isIsolatedUid(v)) {
                            isoUidArg = v;
                        }
                    }
                }

                if (ai == null && prArg != null && fPrInfo != null) {
                    Object infoObj = fPrInfo.get(prArg);
                    if (infoObj instanceof ApplicationInfo) {
                        ai = (ApplicationInfo) infoObj;
                    }
                }

                int ownerUid = ai != null ? ai.uid : -1;
                String pkg = ai != null ? ai.packageName : null;

                if (hosting != null && fHostingZygote != null) {
                    int z = fHostingZygote.getInt(hosting);
                    if (z == appZygoteVal) {
                        if (pkg == null && fDefPkg != null) {
                            pkg = (String) fDefPkg.get(hosting);
                        }
                        if (ownerUid < PolicyEngine.FIRST_APP_UID && fDefUid != null) {
                            ownerUid = fDefUid.getInt(hosting);
                        }
                        int flags = engine.resolvePolicyForPackageOrUid(pkg, ownerUid);
                        int az = PolicyFlags.azMode(flags);
                        if (az != PolicyFlags.AZ_OFF) {
                            int appId = ownerUid >= PolicyEngine.FIRST_APP_UID ? PolicyEngine.toAppId(ownerUid) : -1;
                            if (PolicyFlags.isAuditOnly(flags)) {
                                hub.record(appId, PolicyFlags.PILLAR_AZ, PolicyFlags.VEC_AZ_START_LOCKED, PolicyFlags.ACT_AUDIT, true, az);
                            } else if (az == PolicyFlags.AZ_HARD_DROP) {
                                hub.record(appId, PolicyFlags.PILLAR_AZ, PolicyFlags.VEC_AZ_START_LOCKED, PolicyFlags.ACT_DROPPED, false, az);
                                Object exec = chain.getExecutable();
                                return (exec instanceof Method && ((Method) exec).getReturnType() == boolean.class) ? Boolean.FALSE : null;
                            } else {
                                fHostingZygote.setInt(hosting, regularZygoteVal);
                                hub.record(appId, PolicyFlags.PILLAR_AZ, PolicyFlags.VEC_AZ_START_LOCKED, PolicyFlags.ACT_DOWNGRADED, false, az);
                            }
                        }
                    }
                }

                if (isoUidArg >= 0 && ownerUid >= PolicyEngine.FIRST_APP_UID) {
                    engine.registerIsolatedUid(isoUidArg, ownerUid);
                }

                Object ret = HookGuard.proceedOriginal(chain);

                Object prOut = (ret != null && processRecordCls != null && processRecordCls.isInstance(ret)) ? ret : prArg;
                if (prOut != null && fPrUid != null) {
                    int prUid = fPrUid.getInt(prOut);
                    if (PolicyEngine.isIsolatedUid(prUid)) {
                        int resolvedOwner = ownerUid;
                        if (resolvedOwner < PolicyEngine.FIRST_APP_UID && fPrInfo != null) {
                            Object infoObj = fPrInfo.get(prOut);
                            if (infoObj instanceof ApplicationInfo) {
                                resolvedOwner = ((ApplicationInfo) infoObj).uid;
                            }
                        }
                        if (resolvedOwner < PolicyEngine.FIRST_APP_UID && hosting != null && fDefUid != null) {
                            resolvedOwner = fDefUid.getInt(hosting);
                        }
                        if (resolvedOwner >= PolicyEngine.FIRST_APP_UID) {
                            engine.registerIsolatedUid(prUid, resolvedOwner);
                        }
                    }
                }
                return ret;
            }
        };

        int hStart = CompatMatrix.hookMethods(module, startLockedMethods, XposedInterface.PRIORITY_DEFAULT, guardStart, startHandler);
        int hStartFb = CompatMatrix.hookMethods(module, startFallbackMethods, XposedInterface.PRIORITY_DEFAULT, guardStart, startHandler);

        List<Method> removeMethods = CompatMatrix.findMethodsByName(processListCls, "removeProcessNameLocked");
        CompatMatrix.hookMethods(module, removeMethods, XposedInterface.PRIORITY_DEFAULT, guardRemove, new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                List<Object> args = chain.getArgs();
                for (int i = 0; i < args.size(); i++) {
                    Object a = args.get(i);
                    if (a instanceof Integer) {
                        int v = (Integer) a;
                        if (PolicyEngine.isIsolatedUid(v)) {
                            engine.evictIsolatedUid(v);
                            break;
                        }
                    }
                }
                return HookGuard.proceedOriginal(chain);
            }
        });

        if (hCreate > 0 && (hStart > 0 || hStartFb > 0)) {
            hub.setPillarStatus(PolicyFlags.PILLAR_AZ, TelemetryHub.HEALTH_OK, CompatMatrix.TIER_EXACT);
        } else if (hCreate > 0 || hStart > 0 || hStartFb > 0) {
            hub.setPillarStatus(PolicyFlags.PILLAR_AZ, TelemetryHub.HEALTH_FALLBACK, CompatMatrix.TIER_FALLBACK);
        } else {
            hub.setPillarStatus(PolicyFlags.PILLAR_AZ, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
        }
    }
}
