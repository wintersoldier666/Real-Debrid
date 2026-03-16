# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/android/sdk/tools/proguard/proguard-android.txt

# Keep WebView JavaScript interface (if any added in future)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep enum names for savedInstanceState restoration
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
