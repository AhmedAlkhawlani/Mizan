# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# WorkManager rebuilds a worker from its class name, so the constructors must survive R8.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# The widget receivers are manifest entry points reached by the Glance runtime, not by
# references R8 can see.
-keep class com.maknoon.mizan.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver

# Keep the mapping output readable for crashes; the mapping file is archived per release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile