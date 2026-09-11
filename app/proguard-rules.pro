# Keep Xposed entry classes
-keep class com.yc.iqoolike.hook.** { *; }
-keep class io.github.libxposed.** { *; }
-keep class de.robv.android.xposed.** { *; }

# Keep data models for Gson serialization
-keep class com.yc.iqoolike.data.** { *; }
