# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Room & WorkManager (pulled in by Glance AppWidget)
# R8 full mode (AGP 8+/9+) strips the default constructor and DAO methods of
# RoomDatabase subclasses (such as WorkDatabase_Impl) unless explicitly kept,
# which causes Class.newInstance() in Room.getGeneratedImplementation() to
# throw InstantiationException during WorkManagerInitializer startup.
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
    *;
}
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
    *;
}
-keep class * extends androidx.work.Worker {
    public <init>(...);
    *;
}
-keep class * extends androidx.work.InputMerger {
    public <init>();
    *;
}

# ---------------------------------------------------------------------------
# Glance AppWidget & DataStore Protobuf reflection rules for R8 full mode
# ---------------------------------------------------------------------------
-keep public class * extends androidx.glance.appwidget.action.ActionCallback {
    public <init>();
    *;
}
-keep class * extends androidx.glance.appwidget.protobuf.GeneratedMessageLite {
    <init>(...);
    *;
}
-keep class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <init>(...);
    *;
}
