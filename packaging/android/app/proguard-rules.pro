# Keep WebView JS interface
-keepclassmembers class com.chat.app.MainActivity$WebAppInterface {
    <methods>;
}

# Keep BuildConfig
-keep class com.chat.app.BuildConfig { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Gson if used later
-keepattributes Signature
-keepattributes *Annotation*
