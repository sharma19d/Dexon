# Shizuku instantiates the user service by class name inside a separate shell-UID process.
-keep class com.charon.dexon.data.shizuku.PrivilegedService { <init>(...); *; }
-keep class com.charon.dexon.shizuku.** { *; }

# apk-parser reads binary structures with reflection-free code, but keep its model getters intact.
-keep class net.dongliu.apk.parser.** { *; }

# Optional dependencies absent on Android: Ktor's IDE debugger probe, apk-parser's BouncyCastle
# certificate path (it falls back to the platform parser) and JSR-305 annotations.
-dontwarn java.lang.management.**
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.**
