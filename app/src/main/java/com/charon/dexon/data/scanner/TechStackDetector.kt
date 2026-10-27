package com.charon.dexon.data.scanner

import com.charon.dexon.data.model.TechFramework
import java.io.File
import java.util.zip.ZipFile

object TechStackDetector {

    data class ZipScanResult(
        val framework: TechFramework,
        val nativeLibs: List<String>
    )

    fun scanApkZip(apkPath: String): ZipScanResult {
        val file = File(apkPath)
        if (!file.exists() || !file.canRead()) {
            return ZipScanResult(TechFramework.KOTLIN_NATIVE, emptyList())
        }

        var hasFlutter = false
        var hasReactNative = false
        var hasUnity = false
        var hasCordova = false
        val nativeLibs = mutableSetOf<String>()

        runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    when {
                        name.endsWith("libflutter.so") || name.startsWith("assets/flutter_assets/") -> hasFlutter = true
                        name.endsWith("libreactnativejni.so") || name.endsWith("libreactnative.so") ||
                            name == "assets/index.android.bundle" -> hasReactNative = true
                        name.endsWith("libunity.so") -> hasUnity = true
                        name == "assets/www/cordova.js" -> hasCordova = true
                    }
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        nativeLibs.add(name.substringAfterLast("/"))
                    }
                }
            }
        }

        val framework = when {
            hasFlutter -> TechFramework.FLUTTER
            hasReactNative -> TechFramework.REACT_NATIVE
            hasUnity -> TechFramework.UNITY
            hasCordova -> TechFramework.CORDOVA
            else -> TechFramework.KOTLIN_NATIVE
        }

        return ZipScanResult(framework, nativeLibs.sorted())
    }
}
