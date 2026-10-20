package org.headfi.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.headfi.app.ui.HeadFiAppScreen
import org.headfi.app.ui.MainViewModel
import org.headfi.app.ui.theme.HeadFiTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var incomingDeepLinkUrl by mutableStateOf<String?>(null)

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileChooserCallback ?: return@registerForActivityResult
        pendingFileChooserCallback = null

        if (result.resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            return@registerForActivityResult
        }

        val data = result.data
        val uris: Array<Uri>? = when {
            data?.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { idx -> clip.getItemAt(idx).uri }
            }
            data?.data != null -> arrayOf(data.data!!)
            else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
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
                dynamicColor = preferences.dynamicColors,
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
        pendingFileChooserCallback = callback

        return runCatching {
            val acceptTypes = params?.acceptTypes
                ?.filter { it.isNotBlank() }
                ?.toTypedArray()
                ?: arrayOf("*/*")

            val pickerIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (acceptTypes.size == 1) acceptTypes[0] else "*/*"
                if (acceptTypes.size > 1) {
                    putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                }
                putExtra(
                    Intent.EXTRA_ALLOW_MULTIPLE,
                    params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                )
            }
            fileChooserLauncher.launch(Intent.createChooser(pickerIntent, "Select file or photo"))
            true
        }.getOrElse {
            pendingFileChooserCallback = null
            false
        }
    }
}
