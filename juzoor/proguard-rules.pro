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
