# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.blank.app.**$$serializer { *; }
-keepclassmembers class com.blank.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.blank.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
