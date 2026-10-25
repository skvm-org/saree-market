# Most libraries (Retrofit, OkHttp, Hilt, Coil, WorkManager, Compose) ship their own rules.
# kotlinx.serialization looks serializers up by name, so keep them on the app's data classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.sareemarket.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.sareemarket.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
