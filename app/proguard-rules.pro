# Add project specific ProGuard rules here.
# Defaults are inherited from proguard-android-optimize.txt via build.gradle.kts.

# glance renders the agenda widget through workmanager (transitive, work-runtime
# 2.7.1), which instantiates several classes by reflection: the Room
# WorkDatabase_Impl (stripping it crashed app launch) and the InputMerger
# (stripping it left the widget stuck on its loading frame). 2.7.1's bundled r8
# rules predate full mode, so keep workmanager wholesale; also keep any
# ListenableWorker ctor since glance's own worker lives outside androidx.work.
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
