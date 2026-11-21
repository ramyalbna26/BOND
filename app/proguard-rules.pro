# =====================================================================
# BOND / Sanad — ProGuard rules
# =====================================================================

# Keep source file/line info for readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations (needed by kotlinx.serialization + Room)
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, MethodParameters

# ---------- Sanad app code ----------
# Logging lives under com.sanad.bond.core.logging (was com.sanad.bond.logging —
# a package that does not exist, so the keep rule was a no-op).
-keep class com.sanad.bond.core.logging.** { *; }
-keepclassmembers class com.sanad.bond.core.logging.** { *; }

# WebView JS bridge — @JavascriptInterface methods are called from JS by
# their exact names. Without this keep rule R8 renames them in release
# builds and the Tiptap editor bridge breaks (silent in debug, broken in
# release only). This is the #1 release-only bug.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Keep the bridge object itself (anonymous class in TiptapWebView) so its
# class name is stable for addJavascriptInterface reflection.
-keep @android.webkit.JavascriptInterface class * { *; }

# Keep anything annotated @Keep (general hygiene).
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers @androidx.annotation.Keep class * { *; }

# Data models (Room entities + @Serializable models need reflection / generated serializers)
-keep class com.sanad.bond.data.model.** { *; }
-keep class com.sanad.bond.data.db.entity.** { *; }

# ---------- Kotlin metadata ----------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ---------- Kotlin Coroutines ----------
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlin.coroutines.** { *; }

# ---------- kotlinx.serialization ----------
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sanad.bond.**$$serializer { *; }
-keepclassmembers class com.sanad.bond.** {
    *** Companion;
}
-keepclasseswithmembers class com.sanad.bond.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Apache POI (DOCX read/write) ----------
# POI uses reflection heavily (XMLBeans, schemaorg_apache_xmlbeans). The
# -dontwarn rules cover the Java SE APIs POI references but Android doesn't
# ship — the code paths that use them are never reached on Android.
-keep class org.apache.poi.** { *; }
-keep interface org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-dontwarn org.apache.**
-dontwarn org.openxmlformats.**
-dontwarn schemaorg_apache_xmlbeans.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**
-dontwarn javax.xml.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.codec.**

# ---------- PDFBox (Android port by TomRoush) ----------
-keep class com.tom_roush.pdfbox.** { *; }
-keep interface com.tom_roush.pdfbox.** { *; }
# fontbox is loaded reflectively by PDFBox. -dontwarn alone hides the warning
# but does NOT prevent R8 from shrinking/renaming these classes in release,
# which then crashes with NoSuchMethodError when importing a PDF.
-keep class com.tom_roush.fontbox.** { *; }
-keep interface com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.commons.logging.**

# ---------- Hilt / Dagger ----------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.**

# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---------- WorkManager ----------
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.InputMerger { *; }
-dontwarn androidx.work.**

# ---------- AndroidX Lifecycle (ViewModel reflection) ----------
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# ---------- Material Components ----------
-dontwarn com.google.android.material.**

# ---------- General ----------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}