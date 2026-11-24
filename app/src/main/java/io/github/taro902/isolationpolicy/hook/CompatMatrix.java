package io.github.taro902.isolationpolicy.hook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.Logger;

public final class CompatMatrix {

    public static final int TIER_NONE = 0;
    public static final int TIER_EXACT = 1;
    public static final int TIER_OVERLOAD = 2;
    public static final int TIER_FALLBACK = 3;

    private static volatile Class<?> cachedParceledListClass;
    private static volatile Constructor<?> cachedParceledListCtor;
    private static volatile Method cachedParceledGetList;

    private CompatMatrix() {
    }

    public static final class ResolvedTarget {
        public final Class<?> targetClass;
        public final List<Method> methods;
        public final int tier;

        public ResolvedTarget(Class<?> targetClass, List<Method> methods, int tier) {
            this.targetClass = targetClass;
            this.methods = methods != null ? methods : Collections.<Method>emptyList();
            this.tier = tier;
        }

        public boolean isResolved() {
            return targetClass != null && !methods.isEmpty() && tier != TIER_NONE;
        }
    }

    public static Class<?> findClass(ClassLoader cl, String... classNames) {
        if (cl == null || classNames == null) {
            return null;
        }
        for (String name : classNames) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static Method findExactMethod(Class<?> cls, String name, Class<?>... params) {
        if (cls == null || name == null) {
            return null;
        }
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Method m = cur.getDeclaredMethod(name, params);
                if (!m.isBridge() && !m.isSynthetic() && !Modifier.isAbstract(m.getModifiers())) {
                    return m;
                }
            } catch (Throwable ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    public static List<Method> findMethodsByName(Class<?> cls, String name) {
        ArrayList<Method> out = new ArrayList<Method>(4);
        if (cls == null || name == null) {
            return out;
        }
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Method[] declared = cur.getDeclaredMethods();
                for (Method m : declared) {
                    if (name.equals(m.getName())
                        && !m.isBridge()
                        && !m.isSynthetic()
                        && !Modifier.isAbstract(m.getModifiers())
                    ) {
                        out.add(m);
                    }
                }
            } catch (Throwable ignored) {
            }
            if (!out.isEmpty()) {
                break;
            }
            cur = cur.getSuperclass();
        }
        return out;
    }

    public static Field findField(Class<?> cls, String... fieldNames) {
        if (cls == null || fieldNames == null) {
            return null;
        }
        for (String name : fieldNames) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            Class<?> cur = cls;
            while (cur != null && cur != Object.class) {
                try {
                    Field f = cur.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (Throwable ignored) {
                }
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    public static int readStaticInt(Class<?> cls, String fieldName, int fallback) {
        if (cls == null || fieldName == null) {
            return fallback;
        }
        try {
            Field f = findField(cls, fieldName);
            if (f != null && Modifier.isStatic(f.getModifiers())) {
                return f.getInt(null);
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public static ResolvedTarget resolveTarget(
        ClassLoader cl,
        String[] primaryClasses,
        String methodName,
        Class<?>[] exactParams,
        String[] fallbackClasses
    ) {
        Class<?> primary = findClass(cl, primaryClasses);
        if (primary != null) {
            if (exactParams != null) {
                Method exact = findExactMethod(primary, methodName, exactParams);
                if (exact != null) {
                    return new ResolvedTarget(primary, Collections.singletonList(exact), TIER_EXACT);
                }
            }
            List<Method> overloads = findMethodsByName(primary, methodName);
            if (!overloads.isEmpty()) {
                return new ResolvedTarget(primary, overloads, exactParams == null ? TIER_EXACT : TIER_OVERLOAD);
            }
        }
        if (fallbackClasses != null && fallbackClasses.length > 0) {
            Class<?> fb = findClass(cl, fallbackClasses);
            if (fb != null) {
                if (exactParams != null) {
                    Method exactFb = findExactMethod(fb, methodName, exactParams);
                    if (exactFb != null) {
                        return new ResolvedTarget(fb, Collections.singletonList(exactFb), TIER_FALLBACK);
                    }
                }
                List<Method> fbOverloads = findMethodsByName(fb, methodName);
                if (!fbOverloads.isEmpty()) {
                    return new ResolvedTarget(fb, fbOverloads, TIER_FALLBACK);
                }
            }
        }
        return new ResolvedTarget(null, Collections.<Method>emptyList(), TIER_NONE);
    }

    public static int hookMethod(
        XposedInterface module,
        Method method,
        int priority,
        HookGuard guard,
        HookGuard.Handler handler
    ) {
        if (method == null) {
            return 0;
        }
        return hookMethods(module, Collections.singletonList(method), priority, guard, handler);
    }

    public static int hookMethods(
        XposedInterface module,
        List<Method> methods,
        int priority,
        HookGuard guard,
        HookGuard.Handler handler
    ) {
        if (module == null || methods == null || methods.isEmpty() || guard == null || handler == null) {
            return 0;
        }
        XposedInterface.Hooker hooker = guard.wrap(handler);
        int installed = 0;
        for (Method m : methods) {
            try {
                module.hook(m)
                    .setPriority(priority)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(hooker);
                installed++;
            } catch (Throwable t) {
                Logger.e("hook failed: " + m.getDeclaringClass().getSimpleName() + "." + m.getName(), t);
            }
        }
        return installed;
    }

    public static void tryDeoptimize(XposedInterface module, Method m) {
        if (module == null || m == null) {
            return;
        }
        try {
            module.deoptimize(m);
        } catch (Throwable ignored) {
        }
    }

    public static List<?> extractParceledList(Object sliceOrList) {
        if (sliceOrList == null) {
            return null;
        }
        if (sliceOrList instanceof List) {
            return (List<?>) sliceOrList;
        }
        try {
            Method getter = cachedParceledGetList;
            if (getter == null) {
                Class<?> c = sliceOrList.getClass();
                getter = findExactMethod(c, "getList");
                if (getter != null) {
                    getter.setAccessible(true);
                    cachedParceledGetList = getter;
                }
            }
            if (getter != null) {
                Object res = getter.invoke(sliceOrList);
                if (res instanceof List) {
                    return (List<?>) res;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Object wrapParceledList(ClassLoader cl, List<?> items) {
        List<?> safeItems = items != null ? items : Collections.emptyList();
        try {
            Constructor<?> ctor = cachedParceledListCtor;
            if (ctor == null) {
                Class<?> cls = cachedParceledListClass;
                if (cls == null && cl != null) {
                    cls = findClass(cl, "android.content.pm.ParceledListSlice", "android.content.pm.BaseParceledListSlice");
                    cachedParceledListClass = cls;
                }
                if (cls == null) {
                    cls = Class.forName("android.content.pm.ParceledListSlice");
                    cachedParceledListClass = cls;
                }
                ctor = cls.getDeclaredConstructor(List.class);
                ctor.setAccessible(true);
                cachedParceledListCtor = ctor;
            }
            return ctor.newInstance(safeItems);
        } catch (Throwable t) {
            Logger.e("wrapParceledList failed", t);
            return null;
        }
    }
}
