package io.github.taro902.isolationpolicy.hook;

import android.content.Intent;
import android.os.Binder;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class UsbBroadcastShield {

    private static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final long FUNCTION_ADB = 1L;

    private UsbBroadcastShield() {
    }

    public static void install(
        XposedInterface module,
        ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub
    ) {
        int installed = 0;
        int tier = CompatMatrix.TIER_EXACT;

        CompatMatrix.ResolvedTarget regTarget = CompatMatrix.resolveTarget(
            cl,
            new String[]{"com.android.server.am.ActivityManagerService"},
            "registerReceiverWithFeature",
            null,
            null
        );
        if (!regTarget.isResolved()) {
            Class<?> amsCls = CompatMatrix.findClass(cl, "com.android.server.am.ActivityManagerService");
            List<Method> legacyReg = CompatMatrix.findMethodsByName(amsCls, "registerReceiver");
            if (!legacyReg.isEmpty()) {
                regTarget = new CompatMatrix.ResolvedTarget(amsCls, legacyReg, CompatMatrix.TIER_FALLBACK);
                tier = CompatMatrix.TIER_FALLBACK;
            }
        }

        if (regTarget.isResolved()) {
            HookGuard stickyGuard = hub.createGuard(PolicyFlags.PILLAR_USB, "USB_STICKY");
            installed += CompatMatrix.hookMethods(module, regTarget.methods, XposedInterface.PRIORITY_DEFAULT, stickyGuard, new HookGuard.Handler() {
                @Override
                public Object handle(XposedInterface.Chain chain) throws Throwable {
                    int callingUid = Binder.getCallingUid();
                    int flags = engine.resolvePolicy(callingUid);
                    if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DEV_COHERENT)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    Object ret = HookGuard.proceedOriginal(chain);
                    if (!(ret instanceof Intent)) {
                        return ret;
                    }
                    Intent orig = (Intent) ret;
                    if (!ACTION_USB_STATE.equals(orig.getAction())) {
                        return ret;
                    }
                    boolean hasAdb = orig.getBooleanExtra("adb", false)
                        || orig.getBooleanExtra("adb_enabled", false)
                        || orig.getBooleanExtra("function_adb", false);
                    if (!hasAdb) {
                        return ret;
                    }
                    int effAppId = engine.resolveEffectiveAppId(callingUid);
                    if (PolicyFlags.isAuditOnly(flags)) {
                        hub.record(effAppId, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_STICKY_INTENT, PolicyFlags.ACT_AUDIT, true, 0);
                        return ret;
                    }
                    Intent cloned = new Intent(orig);
                    cloned.putExtra("adb", false);
                    cloned.putExtra("adb_enabled", false);
                    cloned.putExtra("function_adb", false);
                    hub.record(effAppId, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_STICKY_INTENT, PolicyFlags.ACT_SANITIZED, false, 0);
                    return cloned;
                }
            });
        }

        Class<?> usbCls = CompatMatrix.findClass(cl, "com.android.server.usb.UsbService");
        if (usbCls != null) {
            Method mCur = CompatMatrix.findExactMethod(usbCls, "getCurrentFunctions");
            Method mUnl = CompatMatrix.findExactMethod(usbCls, "getScreenUnlockedFunctions");
            Method mIsEn = CompatMatrix.findExactMethod(usbCls, "isFunctionEnabled", String.class);

            HookGuard fnGuard = hub.createGuard(PolicyFlags.PILLAR_USB, "USB_FUNCS");
            if (mCur != null) {
                installed += CompatMatrix.hookMethod(module, mCur, XposedInterface.PRIORITY_DEFAULT, fnGuard, buildFunctionMaskHandler(engine, hub, PolicyFlags.VEC_USB_CUR_FUNCS));
            }
            if (mUnl != null) {
                installed += CompatMatrix.hookMethod(module, mUnl, XposedInterface.PRIORITY_DEFAULT, fnGuard, buildFunctionMaskHandler(engine, hub, PolicyFlags.VEC_USB_UNLOCKED_FUNCS));
            }
            if (mIsEn != null) {
                HookGuard enGuard = hub.createGuard(PolicyFlags.PILLAR_USB, "USB_IS_EN");
                installed += CompatMatrix.hookMethod(module, mIsEn, XposedInterface.PRIORITY_DEFAULT, enGuard, new HookGuard.Handler() {
                    @Override
                    public Object handle(XposedInterface.Chain chain) throws Throwable {
                        int callingUid = Binder.getCallingUid();
                        int flags = engine.resolvePolicy(callingUid);
                        if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DEV_COHERENT)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        List<Object> args = chain.getArgs();
                        if (args.isEmpty() || !"adb".equals(args.get(0))) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        Object ret = HookGuard.proceedOriginal(chain);
                        if (!Boolean.TRUE.equals(ret)) {
                            return ret;
                        }
                        int effAppId = engine.resolveEffectiveAppId(callingUid);
                        if (PolicyFlags.isAuditOnly(flags)) {
                            hub.record(effAppId, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_IS_ENABLED, PolicyFlags.ACT_AUDIT, true, 0);
                            return ret;
                        }
                        hub.record(effAppId, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_IS_ENABLED, PolicyFlags.ACT_SANITIZED, false, 0);
                        return Boolean.FALSE;
                    }
                });
            }
        }

        Class<?> adbCls = CompatMatrix.findClass(cl, "com.android.server.adb.AdbService");
        if (adbCls != null) {
            Method mPort = CompatMatrix.findExactMethod(adbCls, "getAdbWirelessPort");
            if (mPort != null) {
                HookGuard adbGuard = hub.createGuard(PolicyFlags.PILLAR_USB, "ADB_WIFI");
                CompatMatrix.hookMethod(module, mPort, XposedInterface.PRIORITY_DEFAULT, adbGuard, new HookGuard.Handler() {
                    @Override
                    public Object handle(XposedInterface.Chain chain) throws Throwable {
                        int callingUid = Binder.getCallingUid();
                        int flags = engine.resolvePolicy(callingUid);
                        if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DEV_COHERENT)) {
                            return HookGuard.proceedOriginal(chain);
                        }
                        Object ret = HookGuard.proceedOriginal(chain);
                        if (!(ret instanceof Integer) || (Integer) ret <= 0) {
                            return ret;
                        }
                        int effAppId = engine.resolveEffectiveAppId(callingUid);
                        if (PolicyFlags.isAuditOnly(flags)) {
                            hub.record(effAppId, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_ADB_WIFI_PORT, PolicyFlags.ACT_AUDIT, true, (Integer) ret);
                            return ret;
                        }
                        hub.record(effAppId, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_ADB_WIFI_PORT, PolicyFlags.ACT_SANITIZED, false, 0);
                        return Integer.valueOf(0);
                    }
                });
            }
        }

        CompatMatrix.deoptimizeCallers(module, usbCls, "isFunctionEnabled", "getCurrentFunctions", "getScreenUnlockedFunctions");
        hub.setPillarStatus(
            PolicyFlags.PILLAR_USB,
            installed > 0 ? (tier == CompatMatrix.TIER_FALLBACK ? TelemetryHub.HEALTH_FALLBACK : TelemetryHub.HEALTH_OK) : TelemetryHub.HEALTH_OFF,
            installed > 0 ? tier : CompatMatrix.TIER_NONE
        );
    }

    private static HookGuard.Handler buildFunctionMaskHandler(
        final PolicyEngine engine,
        final TelemetryHub hub,
        final int vectorCode
    ) {
        return new HookGuard.Handler() {
            @Override
            public Object handle(XposedInterface.Chain chain) throws Throwable {
                int callingUid = Binder.getCallingUid();
                int flags = engine.resolvePolicy(callingUid);
                if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_DEV_COHERENT)) {
                    return HookGuard.proceedOriginal(chain);
                }
                Object ret = HookGuard.proceedOriginal(chain);
                if (!(ret instanceof Long)) {
                    return ret;
                }
                long fn = (Long) ret;
                if ((fn & FUNCTION_ADB) == 0L) {
                    return ret;
                }
                int effAppId = engine.resolveEffectiveAppId(callingUid);
                if (PolicyFlags.isAuditOnly(flags)) {
                    hub.record(effAppId, PolicyFlags.PILLAR_USB, vectorCode, PolicyFlags.ACT_AUDIT, true, 0);
                    return ret;
                }
                hub.record(effAppId, PolicyFlags.PILLAR_USB, vectorCode, PolicyFlags.ACT_SANITIZED, false, 0);
                return Long.valueOf(fn & ~FUNCTION_ADB);
            }
        };
    }
}
