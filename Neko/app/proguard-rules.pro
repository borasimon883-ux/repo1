-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# Enhanced obfuscation and optimization
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# Keep essential classes but allow more optimization
-keep class io.nekohasekai.sagernet.database.** { *; }
-keep class io.nekohasekai.sagernet.aidl.** { *; }
-keep class io.nekohasekai.sagernet.SagerNet { *; }
-keep class io.nekohasekai.sagernet.bg.** { *; }
-keep class io.nekohasekai.sagernet.fmt.**Bean { *; }
-keep class io.nekohasekai.sagernet.utils.SettingsManager { *; }
-keep class io.nekohasekai.sagernet.utils.ErrorHandler { *; }

# Keep libcore interface
-keep class libcore.** { *; }
-keep class moe.matsuri.nb4a.NativeInterface { *; }
-keep class moe.matsuri.nb4a.net.LocalResolverImpl { *; }
-keep class moe.matsuri.nb4a.SingBoxOptions** { *; }

# Keep UI entry points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service  
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Clean Kotlin (enhanced)
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void throwUninitializedPropertyAccessException(java.lang.String);
}

# Remove debug and logging code
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove unused resources
-assumenosideeffects class io.nekohasekai.sagernet.ktx.Logs {
    public static *** d(...);
    public static *** v(...);
}

# ini4j
-keep public class org.ini4j.spi.** { <init>(); }

# SnakeYaml
-keep class org.yaml.snakeyaml.** { *; }

# Enable obfuscation for better security and size reduction
# -dontobfuscate  # Commented out to enable obfuscation
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
# Remove source file info for smaller APK
# -keepattributes SourceFile

-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.beans.Transient
-dontwarn java.beans.VetoableChangeListener
-dontwarn java.beans.VetoableChangeSupport
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontwarn org.bouncycastle.jce.provider.BouncyCastleProvider
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn java.beans.PropertyVetoException
