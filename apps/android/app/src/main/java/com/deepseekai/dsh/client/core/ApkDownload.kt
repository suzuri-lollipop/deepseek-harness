package com.deepseekai.dsh.client.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * APK self-update: fetch the client package the dsh web host serves beside
 * the dist ([APK_PATH]), keep it in the app cache, and hand it to the system
 * installer through FileProvider. Phase, progress, and the error code are
 * StateFlows written from the IO download task; the screen collects them.
 */
class ApkDownload(private val context: Context) {

    /** Download surface phases for the button row. */
    enum class Phase { Idle, Downloading, Ready }

    private val _phase = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = _phase

    /** Download progress 0..99 while unknown-length streams stay at 0. */
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    /** Stable failure code for the UI to localize, or null when none. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var job: Job? = null
    private var downloadedFile: File? = null

    /**
     * Start (or restart) the download from the normalized [serverBase] on
     * [scope]; the previous task is cancelled. The caller normalizes the URL
     * with [DshClient.normalizeUrl] so a bare host:port field works.
     */
    fun start(serverBase: String, scope: CoroutineScope) {
        job?.cancel()
        _error.value = null
        _progress.value = 0
        _phase.value = Phase.Downloading
        job = scope.launch {
            try {
                downloadedFile = download(serverBase)
                _progress.value = 100
                _phase.value = Phase.Ready
            } catch (e: ApkDownloadException) {
                _phase.value = Phase.Idle
                _error.value = e.code
            } catch (e: Exception) {
                _phase.value = Phase.Idle
                _error.value = "io"
            }
        }
    }

    /**
     * Hand the downloaded APK to the system installer. Returns false when
     * nothing is Ready or the unknown-apps grant is missing (in which case
     * the per-app install-permission settings screen is opened instead).
     */
    fun install(): Boolean {
        val file = downloadedFile ?: return false
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.apk", file)
        // minSdk 26: the grant is always required, checked for every launch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ))
            return false
        }
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }

    /** GET [serverBase]/[APK_PATH], stream it into the cache dir, verify the APK magic. */
    private suspend fun download(serverBase: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "apks")
        if (!dir.exists() && !dir.mkdirs()) throw ApkDownloadException("io")
        val target = File(dir, APK_FILE_NAME)
        val temp = File(dir, "$APK_FILE_NAME.part")
        // The shared client's two-minute call ceiling is too short for an APK.
        val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.MINUTES).build()
        try {
            val request = Request.Builder().url(serverBase + APK_PATH).build()
            http.newCall(request).execute().use { response ->
                if (response.code == 404) throw ApkDownloadException("apk-missing")
                if (!response.isSuccessful) throw ApkDownloadException("http-${response.code}")
                val body = response.body
                val total = body.contentLength()
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            received += read
                            if (total > 0) {
                                _progress.value = (received * 100 / total).toInt().coerceIn(0, 99)
                            }
                        }
                    }
                }
            }
            // An HTML error page or a truncated upload is not an APK.
            verifyApk(temp)
            if (target.exists() && !target.delete()) throw ApkDownloadException("io")
            if (!temp.renameTo(target)) throw ApkDownloadException("io")
            target
        } finally {
            temp.delete()
        }
    }

    /** Reject payloads that do not start with the ZIP local-file-header magic. */
    private fun verifyApk(file: File) {
        file.inputStream().use { input ->
            val head = ByteArray(4)
            if (input.read(head) != 4) throw ApkDownloadException("not-apk")
            val magic = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK\x03\x04"
            if (!head.contentEquals(magic)) throw ApkDownloadException("not-apk")
        }
    }

    companion object {
        /** APK path served by the dsh web host beside the web dist. */
        const val APK_PATH = "/dsh-android.apk"

        /** Cache file name for the downloaded package. */
        const val APK_FILE_NAME = "dsh-android.apk"
    }
}

/** A download failure with the stable [code] the UI maps to localized copy. */
class ApkDownloadException(val code: String) : Exception(code)
