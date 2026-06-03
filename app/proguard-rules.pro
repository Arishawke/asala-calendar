# Add project specific ProGuard rules here.
# Defaults are inherited from proguard-android-optimize.txt via build.gradle.kts.

# glance pulls in workmanager (work-runtime 2.7.1); room loads WorkDatabase_Impl
# by reflection and r8 full mode strips it -> launch crash. re-root it so r8 keeps it.
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# reflective worker instantiation needs the ctor kept under full mode.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
