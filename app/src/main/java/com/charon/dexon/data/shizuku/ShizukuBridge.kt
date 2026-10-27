package com.charon.dexon.data.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.charon.dexon.BuildConfig
import com.charon.dexon.shizuku.IPrivilegedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** Where the user is in Shizuku setup, in the order the steps must be done. */
enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

/**
 * Optional Tier 2 bridge. Nothing here runs until the user explicitly taps a Shizuku action,
 * so the app never prompts for Shizuku on launch.
 */
class ShizukuBridge(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var service: IPrivilegedService? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext.packageName, PrivilegedService::class.java.name))
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private var connection: ServiceConnection? = null

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isInstalled(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
    }.isSuccess

    /** Checked running-first: Sui (root) provides the Shizuku API without the manager app installed. */
    fun status(): ShizukuStatus = when {
        isRunning() -> if (hasPermission()) ShizukuStatus.READY else ShizukuStatus.NEEDS_PERMISSION
        isInstalled() -> ShizukuStatus.NOT_RUNNING
        else -> ShizukuStatus.NOT_INSTALLED
    }

    fun hasPermission(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun requestPermission(): Boolean {
        if (hasPermission()) return true
        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    suspend fun execute(command: String): Result<String> = runCatching {
        check(isRunning()) { "Shizuku is not running. Start it via Wireless Debugging first." }
        check(requestPermission()) { "Shizuku permission was denied." }
        val svc = service ?: withTimeout(10_000) { bind() }
        withContext(Dispatchers.IO) { svc.executeShellCommand(command) }
    }

    private suspend fun bind(): IPrivilegedService = suspendCancellableCoroutine { cont ->
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null || !binder.pingBinder()) return
                val svc = IPrivilegedService.Stub.asInterface(binder)
                service = svc
                if (cont.isActive) cont.resume(svc)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        connection = conn
        Shizuku.bindUserService(userServiceArgs, conn)
    }

    fun release() {
        val conn = connection ?: return
        runCatching { Shizuku.unbindUserService(userServiceArgs, conn, true) }
        connection = null
        service = null
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE = 0xDE
    }
}
