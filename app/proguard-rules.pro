# Add project specific ProGuard rules here.
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class org.headfi.app.webview.HeadFiJsBridge { *; }

# WorkManager instantiates workers reflectively by class name, so the class and
# its (Context, WorkerParameters) constructor must survive shrinking/obfuscation.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class org.headfi.app.notifications.UnreadPollWorker { *; }
