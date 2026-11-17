package io.github.taro902.isolationpolicy;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class Entry extends XposedModule {

    public Entry() {
        super();
    }


    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        super.onModuleLoaded(param);
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        BindHook.install(this, param.getClassLoader());
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!Constants.APPLICATION_ID.equals(param.getPackageName())) {
            return;
        }
        hookSelf(param.getDefaultClassLoader());
    }

    private void hookSelf(ClassLoader cl) {
        try {
            Class<?> c = Class.forName("io.github.taro902.isolationpolicy.ModuleStatus", false, cl);
            Method m = c.getDeclaredMethod("isActive");
            hook(m)
                .setPriority(PRIORITY_HIGHEST)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> Boolean.TRUE);
        } catch (Throwable t) {
            Logger.e("hookSelf failed", t);
        }
    }
}