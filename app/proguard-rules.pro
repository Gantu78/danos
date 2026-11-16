# Reglas de ProGuard / R8 para la variante release (H-72)

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes SourceFile, LineNumberTable

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.danos.**$$serializer { *; }
-keepclassmembers class com.example.danos.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.danos.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# DTOs y rutas serializables
-keep class com.example.danos.infraestructura.remoto.** { *; }
-keep class com.example.danos.presentacion.navegacion.** { *; }
-keep class com.example.danos.presentacion.evaluar.FotoFormulario { *; }
-keep class com.example.danos.presentacion.evaluar.FormularioEvaluar { *; }

# Retrofit y OkHttp
-keep interface com.example.danos.infraestructura.remoto.DanosApi { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.danos.infraestructura.local.** { *; }

# Hilt y WorkManager
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(...);
}
-keep class com.example.danos.infraestructura.sync.SincronizacionWorker { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# AppAuth (net.openid:appauth)
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**
