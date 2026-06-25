package com.chat.app

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

/**
 * JavaScript interface exposed to the WebView.
 * The web frontend can call these methods via window.AIChatBridge.method()
 */
class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun showSnackbar(message: String) {
        // Snackbar requires a view - this is handled in the activity
    }

    @JavascriptInterface
    fun getPlatform(): String = "android"

    @JavascriptInterface
    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun retry() {
        // Reload the current page - handled via activity
    }

    @JavascriptInterface
    fun shareText(text: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享到"))
    }

    @JavascriptInterface
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
