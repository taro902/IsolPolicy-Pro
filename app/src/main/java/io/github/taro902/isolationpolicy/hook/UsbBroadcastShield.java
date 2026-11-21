package io.github.taro902.isolationpolicy.hook;

import android.content.Intent;
import android.os.Binder;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.core.Logger;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class UsbBroadcastShield {

    private static final String TAG = "UsbBroadcastShield";
    private static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final long FUNCTION_ADB = 1L;

    public void install(XposedModule module, ClassLoader sysCl, PolicyEngine engine, TelemetryHub telemetry) {
        int amsHooked = hookActivityManagerStickyUsb(module, sysCl, engine, telemetry);
        int usbHooked = hookUsbService(module, sysCl, engine, telemetry);

        if (amsHooked > 0 && usbHooked > 0) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_USB, TelemetryHub.STATE_OK);
        } else if (amsHooked > 0 || usbHooked > 0) {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_USB, TelemetryHub.STATE_FALLBACK);
        } else {
            telemetry.markHookState(PolicyFlags.HOOK_BIT_USB, TelemetryHub.STATE_FAILED);
        }
    }

    private int hookActivityManagerStickyUsb(
            XposedModule module,
            ClassLoader sysCl,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        int hooked = 0;
        try {
            Class<?> amsCls = Class.forName("com.android.server.am.ActivityManagerService", false, sysCl);
            for (Method m : amsCls.getDeclaredMethods()) {
                String name = m.getName();
                if (!"registerReceiverWithFeature".equals(name) && !"registerReceiver".equals(name)) {
                    continue;
                }
                if (m.getReturnType() != Intent.class) {
                    continue;
                }
                try {
                    module.hook(m)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                int callingUid = Binder.getCallingUid();
                                if (!engine.hasFlag(callingUid, PolicyFlags.FLAG_DEV_COHERENT)) {
                                    return chain.proceed();
                                }
                                Object res = chain.proceed();
                                if (!(res instanceof Intent)) {
                                    return res;
                                }
                                Intent sticky = (Intent) res;
                                if (!ACTION_USB_STATE.equals(sticky.getAction())) {
                                    return sticky;
                                }
                                boolean hasAdb = sticky.getBooleanExtra("adb", false);
                                boolean hasFuncAdb = sticky.getBooleanExtra("function_adb", false);
                                if (!hasAdb && !hasFuncAdb) {
                                    return sticky;
                                }
                                Intent clone = new Intent(sticky);
                                if (hasAdb) {
                                    clone.putExtra("adb", false);
                                }
                                if (hasFuncAdb) {
                                    clone.putExtra("function_adb", false);
                                }
                                int appId = engine.resolveEffectiveAppId(callingUid);
                                telemetry.record(appId, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_STICKY, PolicyFlags.ACT_SANITIZED);
                                return clone;
                            });
                    hooked++;
                } catch (Throwable t) {
                    Logger.e(TAG, "hook registerReceiver failed", t);
                }
            }
        } catch (Throwable t) {
            Logger.e(TAG, "load ActivityManagerService failed", t);
        }
        return hooked;
    }

    private int hookUsbService(
            XposedModule module,
            ClassLoader sysCl,
            PolicyEngine engine,
            TelemetryHub telemetry
    ) {
        int hooked = 0;
        try {
            Class<?> usbCls = Class.forName("com.android.server.usb.UsbService", false, sysCl);
            for (Method m : usbCls.getDeclaredMethods()) {
                String name = m.getName();
                if ("getCurrentFunctions".equals(name) && m.getParameterCount() == 0 && m.getReturnType() == long.class) {
                    try {
                        module.hook(m)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(chain -> {
                                    int callingUid = Binder.getCallingUid();
                                    if (!engine.hasFlag(callingUid, PolicyFlags.FLAG_DEV_COHERENT)) {
                                        return chain.proceed();
                                    }
                                    Object res = chain.proceed();
                                    if (res instanceof Long) {
                                        long mask = (Long) res;
                                        if ((mask & FUNCTION_ADB) != 0L) {
                                            int appId = engine.resolveEffectiveAppId(callingUid);
                                            telemetry.record(appId, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_FUNCS, PolicyFlags.ACT_CLEARED);
                                            return mask & ~FUNCTION_ADB;
                                        }
                                    }
                                    return res;
                                });
                        hooked++;
                    } catch (Throwable t) {
                        Logger.e(TAG, "hook getCurrentFunctions failed", t);
                    }
                } else if ("isFunctionEnabled".equals(name) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                    try {
                        module.hook(m)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(chain -> {
                                    int callingUid = Binder.getCallingUid();
                                    if (!engine.hasFlag(callingUid, PolicyFlags.FLAG_DEV_COHERENT)) {
                                        return chain.proceed();
                                    }
                                    List<Object> args = chain.getArgs();
                                    if (!args.isEmpty() && "adb".equals(args.get(0))) {
                                        int appId = engine.resolveEffectiveAppId(callingUid);
                                        telemetry.record(appId, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_FUNCS, PolicyFlags.ACT_CLEARED);
                                        return Boolean.FALSE;
                                    }
                                    return chain.proceed();
                                });
                        hooked++;
                    } catch (Throwable t) {
                        Logger.e(TAG, "hook isFunctionEnabled failed", t);
                    }
                }
            }
        } catch (Throwable t) {
            Logger.e(TAG, "load UsbService failed", t);
        }
        return hooked;
    }
}