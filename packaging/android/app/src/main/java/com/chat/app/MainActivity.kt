package com.chat.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SERVER_URL = BuildConfig.SERVER_URL
        private const val APP_NAME = BuildConfig.APP_NAME
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rootLayout: FrameLayout

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var videoUploadCallback: ValueCallback<Uri>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (fileUploadCallback != null) {
            val results = if (result.resultCode == RESULT_OK && result.data?.clipData != null) {
                val count = result.data!!.clipData!!.itemCount
                Array(count) { i -> result.data!!.clipData!!.getItemAt(i).uri }
            } else if (result.resultCode == RESULT_OK && result.data?.data != null) {
                arrayOf(result.data!!.data!!)
            } else {
                null
            }
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }
        if (videoUploadCallback != null) {
            val resultUri = if (result.resultCode == RESULT_OK) result.data?.data else null
            videoUploadCallback?.onReceiveValue(resultUri)
            videoUploadCallback = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.rootLayout)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        webView = findViewById(R.id.webView)

        setupWebView()
        setupSwipeRefresh()
        monitorNetwork()

        loadApp()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(true)
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false

                // Improve performance
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }

            addJavascriptInterface(WebAppInterface(this@MainActivity), "AIChatBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    injectElectronFlag()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false

                    // Handle file downloads
                    if (url.contains("/api/files/") || url.matches(".*\\.(docx?|pptx?|pdf|txt|xlsx?)$".toRegex())) {
                        downloadFile(url)
                        return true
                    }

                    // Open external links in browser
                    if (!url.startsWith(SERVER_URL) && !url.startsWith("http://localhost")) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        return true
                    }

                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        showError(getString(R.string.error_loading))
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // For development only - in production, handle properly
                    handler?.proceed()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                }

                override fun onShowFileChooser(
                    view: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileUploadCallback = callback
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                            "image/*", "application/pdf", "application/msword",
                            "application/vnd.openxmlformats-officedocument.*",
                            "text/plain", "text/markdown"
                        ))
                    }
                    fileChooserLauncher.launch(intent)
                    return true
                }
            }

            // Override back button
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    if (canGoBack()) {
                        goBack()
                        return@setOnKeyListener true
                    }
                    // Show exit confirmation on first page
                    confirmExit()
                    return@setOnKeyListener true
                }
                false
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.apply {
            setColorSchemeColors(
                resources.getColor(R.color.primary, theme),
                resources.getColor(R.color.primary_light, theme)
            )
            setOnRefreshListener {
                webView.reload()
            }
        }
    }

    private fun monitorNetwork() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    swipeRefresh.isEnabled = true
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    Snackbar.make(rootLayout, R.string.no_connection, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun loadApp() {
        val url = SERVER_URL
        // Try to load with splash/loading indicator
        webView.loadUrl(url)

        // If connection fails after timeout, show setup dialog
        webView.postDelayed({
            if (progressBar.visibility == View.VISIBLE) {
                showServerSetupDialog()
            }
        }, 15000)
    }

    private fun showServerSetupDialog() {
        val currentUrl = SERVER_URL
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.server_setup_title))
            .setMessage(getString(R.string.server_setup_message, currentUrl))
            .setPositiveButton(getString(R.string.retry)) { _, _ ->
                webView.loadUrl(currentUrl)
            }
            .setNegativeButton(getString(R.string.settings)) { _, _ ->
                showServerUrlDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun showServerUrlDialog() {
        val input = android.widget.EditText(this).apply {
            setText(SERVER_URL)
            hint = "http://192.168.1.100:8080"
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.server_url_title))
            .setView(input)
            .setPositiveButton(getString(R.string.connect)) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotBlank()) {
                    webView.loadUrl(url)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun downloadFile(url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    url.substringAfterLast("/")
                )
                setTitle(APP_NAME)
                setDescription(getString(R.string.downloading))
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            webView.loadUrl(url)
        }
    }

    private fun injectElectronFlag() {
        webView.evaluateJavascript("""
            (function() {
                if (!window.electronAPI) {
                    window.electronAPI = {
                        isElectron: false,
                        isAndroid: true,
                        platform: 'android',
                        versions: { android: '${Build.VERSION.SDK_INT}' }
                    };
                }
                // Fix file input for Android WebView
                document.querySelectorAll('input[type="file"]').forEach(function(el) {
                    if (!el.hasAttribute('capture') && el.getAttribute('accept') && el.getAttribute('accept').includes('image')) {
                        el.setAttribute('capture', 'environment');
                    }
                });
            })();
        """.trimIndent())
    }

    private fun showError(message: String) {
        webView.loadDataWithBaseURL(
            null,
            """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
                body { font-family: -apple-system, sans-serif; display: flex;
                       align-items: center; justify-content: center; min-height: 100vh;
                       margin: 0; background: #f8fafc; color: #1e293b; }
                .card { text-align: center; padding: 2rem; max-width: 360px; }
                .icon { font-size: 4rem; margin-bottom: 1rem; }
                h2 { font-size: 1.5rem; margin-bottom: 0.5rem; }
                p { color: #64748b; margin-bottom: 1.5rem; }
                button { background: #4f46e5; color: white; border: none;
                         padding: 0.8rem 2rem; border-radius: 8px; font-size: 1rem; cursor: pointer; }
            </style></head><body>
            <div class="card">
                <div class="icon">📡</div>
                <h2>$message</h2>
                <p>请检查服务器连接设置</p>
                <button onclick="window.AIChatBridge.retry()">重试</button>
            </div></body></html>
            """.trimIndent(),
            "text/html", "UTF-8", null
        )
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_title))
            .setMessage(getString(R.string.exit_message))
            .setPositiveButton(getString(R.string.exit_confirm)) { _, _ -> finish() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
