# Juzoor R8 rules.
#
# Only the platform boundary needs stable names: manifest components are
# instantiated by the framework, and the ContentProvider is addressed by its
# authority. Everything else (engine, UI helpers, queue, guard) can be renamed
# and shrunk so the PRD's obfuscation requirement is actually meaningful.

-keep class com.ramyelbanna.juzoor.App { *; }

-keepnames class com.ramyelbanna.juzoor.*Activity
-keepnames class com.ramyelbanna.juzoor.ConversionService
-keepnames class com.ramyelbanna.juzoor.core.JuzoorFiles

-keepclassmembers class * extends android.app.Activity {
    public <init>();
    protected void onCreate(android.os.Bundle);
    protected void onResume();
    protected void onStart();
    protected void onStop();
    protected void onDestroy();
    protected void onActivityResult(int, int, android.content.Intent);
    public void onBackPressed();
}

-keepclassmembers class * extends android.app.Service {
    public <init>();
    public int onStartCommand(android.content.Intent, int, int);
    public android.os.IBinder onBind(android.content.Intent);
    public void onCreate();
    public void onDestroy();
}

-keepclassmembers class * extends android.content.ContentProvider {
    public <init>();
    public boolean onCreate();
    public android.database.Cursor query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String);
    public java.lang.String getType(android.net.Uri);
    public android.net.Uri insert(android.net.Uri, android.content.ContentValues);
    public int delete(android.net.Uri, java.lang.String, java.lang.String[]);
    public int update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[]);
    public android.os.ParcelFileDescriptor openFile(android.net.Uri, java.lang.String);
}

-keepclassmembers class * { native <methods>; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# Embedded Jadx 1.5.1 decompiler
# ---------------------------------------------------------------------------
# Jadx is called through its public API and internally resolves passes and the
# DEX input plugin. Keep the engine intact rather than relying on R8's guesses.
-keep class jadx.** { *; }
-keep class org.slf4j.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Desktop-only branches of Jadx reference AWT/XML classes that Android does not
# ship. They are never reached because Juzoor skips resources and configures an
# offline security provider, but R8 still needs to know the gap is intentional.
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.swing.**
-dontwarn javax.xml.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**
-dontwarn com.google.common.**
-dontwarn org.jspecify.**
-dontwarn com.beust.jcommander.**
-dontwarn javax.imageio.**
-dontwarn org.jetbrains.annotations.**
