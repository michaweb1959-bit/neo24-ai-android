# ============================================================
# Neo24 AI - R8 / ProGuard rules
# ============================================================

# WebView JavaScript Bridge:
# Von JavaScript aufgerufene Methoden dürfen von R8
# nicht entfernt oder umbenannt werden.

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Neo24 AI WebBridge vollständig erhalten.
-keep class ai.neo24.app.WebBridge {
    *;
}