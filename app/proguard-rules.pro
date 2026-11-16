-adaptresourcefilecontents META-INF/xposed/java_init.list
-keepattributes RuntimeVisibleAnnotations

-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
    public void onModuleLoaded(io.github.libxposed.api.XposedModuleInterface$ModuleLoadedParam);
    public void onSystemServerStarting(io.github.libxposed.api.XposedModuleInterface$SystemServerStartingParam);
    public void onPackageLoaded(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
    public void onPackageReady(io.github.libxposed.api.XposedModuleInterface$PackageReadyParam);
}

-keep class io.github.mhmrdd.isolationpolicy.Entry { *; }
-keep class io.github.mhmrdd.isolationpolicy.Constants { *; }
-keep class io.github.mhmrdd.isolationpolicy.ModuleStatus { *; }
-keep class io.github.mhmrdd.isolationpolicy.AppZygote { *; }
-keep class io.github.mhmrdd.isolationpolicy.IsolationPolicyService { *; }
-keep class io.github.mhmrdd.isolationpolicy.IIsolationPolicyService { *; }
-keep class io.github.mhmrdd.isolationpolicy.IIsolationPolicyService$* { *; }

-repackageclasses "isolationpolicy"
-allowaccessmodification
-overloadaggressively