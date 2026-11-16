package io.github.mhmrdd.isolationpolicy;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class BindHook {

    private static final String HOSTING_RECORD = "com.android.server.am.HostingRecord";
    private static final String PROCESS_RECORD = "com.android.server.am.ProcessRecord";
    private static final String PROCESS_LIST = "com.android.server.am.ProcessList";

    private static final int ZYGOTE_REGULAR = 0;
    private static final int ZYGOTE_APP = 2;

    private static final AtomicReference<Set<String>> sDeniedPackages =
            new AtomicReference<>(Collections.singleton(Constants.APPLICATION_ID));
    private static final AtomicBoolean sDropMode = new AtomicBoolean(false);
    private static final ThreadLocal<Boolean> sForceRegularZygote =
            new ThreadLocal<Boolean>() {
                @Override
                protected Boolean initialValue() {
                    return Boolean.FALSE;
                }
            };

    private static volatile SharedPreferences sRemotePrefs;
    private static volatile SharedPreferences.OnSharedPreferenceChangeListener sPrefListener;

    private static volatile Field sHostingZygoteField;
    private static volatile Method sUsesAppZygoteMethod;
    private static volatile Field sProcessRecordInfoField;

    private BindHook() {}

    public static void install(final XposedModule module, final ClassLoader cl) {
        initRemotePrefs(module);
        initReflectionCache(cl);
        hookHostingRecord(module, cl);
        hookProcessList(module, cl);
    }

    private static void initRemotePrefs(final XposedModule module) {
        try {
            final SharedPreferences prefs = module.getRemotePreferences(Constants.PREFS_NAME);
            if (prefs == null) return;
            sRemotePrefs = prefs;
            syncPrefs(prefs);
            SharedPreferences.OnSharedPreferenceChangeListener listener =
                    new SharedPreferences.OnSharedPreferenceChangeListener() {
                        @Override
                        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                            syncPrefs(sp);
                        }
                    };
            sPrefListener = listener;
            prefs.registerOnSharedPreferenceChangeListener(listener);
        } catch (Throwable t) {
            Logger.e("initRemotePrefs failed", t);
        }
    }

    private static void syncPrefs(SharedPreferences prefs) {
        try {
            Set<String> raw = prefs.getStringSet(Constants.KEY_DENIED, Collections.<String>emptySet());
            Set<String> next = new HashSet<>();
            if (raw != null) {
                next.addAll(raw);
            }
            next.add(Constants.APPLICATION_ID);
            sDeniedPackages.set(Collections.unmodifiableSet(next));
            sDropMode.set(prefs.getBoolean(Constants.KEY_MODE_DROP, false));
        } catch (Throwable t) {
            Logger.e("syncPrefs failed", t);
        }
    }

    private static void initReflectionCache(ClassLoader cl) {
        try {
            Class<?> hrClass = Class.forName(HOSTING_RECORD, false, cl);
            try {
                Field f = hrClass.getDeclaredField("mHostingZygote");
                f.setAccessible(true);
                sHostingZygoteField = f;
            } catch (Throwable ignored) {}
            try {
                Method m = hrClass.getDeclaredMethod("usesAppZygote");
                m.setAccessible(true);
                sUsesAppZygoteMethod = m;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        try {
            Class<?> prClass = Class.forName(PROCESS_RECORD, false, cl);
            Field f = prClass.getDeclaredField("info");
            f.setAccessible(true);
            sProcessRecordInfoField = f;
        } catch (Throwable ignored) {}
    }

    private static void hookHostingRecord(final XposedModule module, ClassLoader cl) {
        try {
            Method m = sUsesAppZygoteMethod;
            if (m == null) {
                Class<?> hrClass = Class.forName(HOSTING_RECORD, false, cl);
                m = hrClass.getDeclaredMethod("usesAppZygote");
            }
            try { module.deoptimize(m); } catch (Throwable ignored) {}
            module.hook(m)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (Boolean.TRUE.equals(sForceRegularZygote.get())) {
                        return Boolean.FALSE;
                    }
                    Object self = chain.getThisObject();
                    if (self != null && isForcedRegularOnInstance(self)) {
                        return Boolean.FALSE;
                    }
                    return chain.proceed();
                });
        } catch (Throwable t) {
            Logger.e("hookHostingRecord failed", t);
        }
    }

    private static void hookProcessList(final XposedModule module, ClassLoader cl) {
        try {
            Class<?> plClass = Class.forName(PROCESS_LIST, false, cl);
            Method[] methods = plClass.getDeclaredMethods();
            for (final Method m : methods) {
                String name = m.getName();
                if ("startProcessLocked".equals(name)) {
                    try { module.deoptimize(m); } catch (Throwable ignored) {}
                    installStartProcessHook(module, m);
                } else if ("startAppZygoteIfNeededLocked".equals(name)) {
                    try { module.deoptimize(m); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            Logger.e("hookProcessList failed", t);
        }
    }

    private static void installStartProcessHook(final XposedModule module, final Method method) {
        final Class<?> returnType = method.getReturnType();
        final boolean returnsBoolean = (returnType == Boolean.TYPE || returnType == Boolean.class);
        final boolean returnsProcessRecord = PROCESS_RECORD.equals(returnType.getName());
        if (!returnsBoolean && !returnsProcessRecord) {
            return;
        }

        module.hook(method)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                List<Object> args = chain.getArgs();
                Object hr = null;
                Object pr = null;
                ApplicationInfo ai = null;

                for (Object arg : args) {
                    if (arg == null) continue;
                    if (arg instanceof ApplicationInfo && ai == null) {
                        ai = (ApplicationInfo) arg;
                        continue;
                    }
                    String cn = arg.getClass().getName();
                    if (hr == null && HOSTING_RECORD.equals(cn)) {
                        hr = arg;
                    } else if (pr == null && PROCESS_RECORD.equals(cn)) {
                        pr = arg;
                    }
                }

                if (hr == null) {
                    return chain.proceed();
                }

                if (!usesAppZygote(hr)) {
                    return chain.proceed();
                }

                String pkg = resolvePackage(pr, ai);
                if (pkg == null || !isDenied(pkg)) {
                    return chain.proceed();
                }

                if (sDropMode.get()) {
                    if (returnsBoolean) {
                        return Boolean.TRUE;
                    }
                    return pr;
                }

                downgradeToRegularZygote(hr);
                sForceRegularZygote.set(Boolean.TRUE);
                try {
                    return chain.proceed();
                } finally {
                    sForceRegularZygote.set(Boolean.FALSE);
                }
            });
    }

    private static void downgradeToRegularZygote(Object hostingRecord) {
        try {
            Field f = sHostingZygoteField;
            if (f == null) {
                f = hostingRecord.getClass().getDeclaredField("mHostingZygote");
                f.setAccessible(true);
                sHostingZygoteField = f;
            }
            f.setInt(hostingRecord, ZYGOTE_REGULAR);
        } catch (Throwable ignored) {}
    }

    private static boolean isForcedRegularOnInstance(Object hostingRecord) {
        try {
            Field f = sHostingZygoteField;
            if (f != null) {
                return f.getInt(hostingRecord) == ZYGOTE_REGULAR;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean usesAppZygote(Object hostingRecord) {
        try {
            Field f = sHostingZygoteField;
            if (f == null) {
                f = hostingRecord.getClass().getDeclaredField("mHostingZygote");
                f.setAccessible(true);
                sHostingZygoteField = f;
            }
            return f.getInt(hostingRecord) == ZYGOTE_APP;
        } catch (Throwable ignored) {}

        try {
            Method m = sUsesAppZygoteMethod;
            if (m == null) {
                m = hostingRecord.getClass().getDeclaredMethod("usesAppZygote");
                m.setAccessible(true);
                sUsesAppZygoteMethod = m;
            }
            Object res = m.invoke(hostingRecord);
            if (res instanceof Boolean) {
                return ((Boolean) res).booleanValue();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static String resolvePackage(Object processRecord, ApplicationInfo directAi) {
        if (directAi != null && directAi.packageName != null && !directAi.packageName.isEmpty()) {
            return directAi.packageName;
        }
        if (processRecord == null) {
            return null;
        }
        try {
            Field f = sProcessRecordInfoField;
            if (f == null) {
                f = processRecord.getClass().getDeclaredField("info");
                f.setAccessible(true);
                sProcessRecordInfoField = f;
            }
            Object info = f.get(processRecord);
            if (info instanceof ApplicationInfo) {
                String pkg = ((ApplicationInfo) info).packageName;
                if (pkg != null && !pkg.isEmpty()) {
                    return pkg;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isDenied(String pkg) {
        if (pkg == null) return false;
        if (Constants.APPLICATION_ID.equals(pkg)) return true;
        Set<String> denied = sDeniedPackages.get();
        return denied != null && denied.contains(pkg);
    }
}