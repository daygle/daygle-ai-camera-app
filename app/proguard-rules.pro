# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.daygle.aicamera.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.daygle.aicamera.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
