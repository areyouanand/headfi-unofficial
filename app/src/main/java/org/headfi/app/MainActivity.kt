package org.headfi.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.headfi.app.ui.HeadFiAppScreen
import org.headfi.app.ui.MainViewModel
import org.headfi.app.ui.theme.HeadFiTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null

    /** Output target handed to the camera app, if the user picked "Camera". */
    private var cameraImageUri: Uri? = null

    private var incomingDeepLinkUrl by mutableStateOf<String?>(null)

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileChooserCallback ?: return@registerForActivityResult
        pendingFileChooserCallback = null

        val cameraUri = cameraImageUri
        cameraImageUri = null

        val data = result.data
        val uris: Array<Uri>? = when {
            result.resultCode == Activity.RESULT_OK && data?.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { idx -> clip.getItemAt(idx).uri }
            }
            result.resultCode == Activity.RESULT_OK && data?.data != null -> arrayOf(data.data!!)
            // Several camera apps report RESULT_CANCELED even after saving the
            // photo, so fall back to the EXTRA_OUTPUT file we gave them.
            cameraUri != null && cameraFileHasContent(cameraUri) -> arrayOf(cameraUri)
            else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
        }

        if (uris == null) {
            discardCameraFile(cameraUri)
        }
        callback.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep splash screen briefly while backed-up cookies are verified/restored
        splashScreen.setKeepOnScreenCondition {
            !viewModel.isSessionReady.value
        }

        extractDeepLink(intent)

        setContent {
            val preferences by viewModel.preferences.collectAsState()

            HeadFiTheme(
                themeMode = preferences.themeMode,
                amoledBlack = preferences.amoledBlack
            ) {
                HeadFiAppScreen(
                    viewModel = viewModel,
                    deepLinkUrl = incomingDeepLinkUrl,
                    onConsumeDeepLink = { incomingDeepLinkUrl = null },
                    onFileChooserRequested = ::launchFileChooser
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLink(intent)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        viewModel.onAppPausedOrStopped()
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
        viewModel.onAppPausedOrStopped()
    }

    override fun onDestroy() {
        // The WebView no longer needs any camera capture we may have staged.
        if (!isChangingConfigurations) {
            pendingFileChooserCallback?.onReceiveValue(null)
            pendingFileChooserCallback = null
            discardCameraFile(cameraImageUri)
            cameraImageUri = null
            pruneCameraDirectory()
        }
        super.onDestroy()
    }

    private fun extractDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val host = data.host?.lowercase() ?: return
        if (host == "head-fi.org" || host.endsWith(".head-fi.org")) {
            incomingDeepLinkUrl = data.toString()
        }
    }

    private fun launchFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {
        pendingFileChooserCallback?.onReceiveValue(null)
        discardCameraFile(cameraImageUri)

        pendingFileChooserCallback = callback
        cameraImageUri = null

        return runCatching {
            val allowMultiple =
                params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

            val acceptTypes = params?.acceptTypes
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toTypedArray()
                ?.takeIf { it.isNotEmpty() }
                ?: arrayOf("*/*")

            val wantsImages = acceptTypes.any {
                it.startsWith("image") || it == "*/*"
            }

            // Prefer the WebView's own picker intent: it already carries the
            // right mode and MIME filters for the field that was tapped.
            val pickerIntent = runCatching { params?.createIntent() }.getOrNull()?.apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = if (acceptTypes.size == 1) acceptTypes[0] else "*/*"
                if (acceptTypes.size > 1) {
                    putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                }
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            }

            // Offer "Camera" alongside the document picker so photos can be
            // snapped directly for forum attachments and Classifieds listings.
            val extraIntents = mutableListOf<Intent>()
            if (wantsImages) {
                pruneCameraDirectory()
                createCameraImageUri()?.let { uri ->
                    cameraImageUri = uri
                    extraIntents += Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        addFlags(
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            }

            val chooser = Intent.createChooser(pickerIntent, "Select file or photo").apply {
                if (extraIntents.isNotEmpty()) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toTypedArray())
                }
            }

            fileChooserLauncher.launch(chooser)
            true
        }.getOrElse {
            pendingFileChooserCallback = null
            discardCameraFile(cameraImageUri)
            cameraImageUri = null
            false
        }
    }

    /** Creates an empty, shareable JPEG in our cache for the camera to fill in. */
    private fun createCameraImageUri(): Uri? = runCatching {
        val directory = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "headfi_${System.currentTimeMillis()}.jpg")
        if (!file.exists()) {
            file.createNewFile()
        }
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }.getOrNull()

    private fun cameraFileHasContent(uri: Uri): Boolean = runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 0L } ?: false
    }.getOrDefault(false)

    private fun discardCameraFile(uri: Uri?) {
        if (uri == null) return
        // ContentResolver.delete() reports how many rows it removed.
        val deleted = runCatching { contentResolver.delete(uri, null, null) }
            .getOrDefault(0) > 0
        if (!deleted) {
            runCatching { uri.path?.let { File(it).delete() } }
        }
    }

    /** Drops capture files left behind by abandoned chooser sessions. */
    private fun pruneCameraDirectory() {
        runCatching {
            val directory = File(cacheDir, "camera")
            val cutoff = System.currentTimeMillis() - CACHE_RETENTION_MS
            directory.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }

    private companion object {
        const val CACHE_RETENTION_MS = 24L * 60L * 60L * 1000L
    }
}
