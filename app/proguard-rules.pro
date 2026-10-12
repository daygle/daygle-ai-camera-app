# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.daygle.aicamera.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.daygle.aicamera.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep exception class names readable; they are shown to users in error messages
# (e.g. SessionManager login errors) and in crash reports.
-keepnames class * extends java.lang.Throwable
