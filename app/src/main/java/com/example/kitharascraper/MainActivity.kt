package com.example.kitharascraper

import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
// import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.kithara.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class MainActivity : AppCompatActivity() {

    private companion object {
        const val HOME_URL = "https://kithara.to/"
        const val ALLOWED_HOST = "kithara.to"
        const val GOOGLE_HOST = "google.com"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var backBtn: Button
    private lateinit var scrapeBtn: Button
    private lateinit var downloadBtn: Button
    private lateinit var reloadBtn: Button

    private var lastChordPro = ""
    private var lastTitle = ""
    private var lastArtist = ""
    private var mainFrameHttpError: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Σωστή κλήση χωρίς παραμέτρους
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                showMainScreen()
            }
        }, 700L)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showMainScreen() {
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        backBtn = findViewById(R.id.backBtn)
        scrapeBtn = findViewById(R.id.scrapeBtn)
        downloadBtn = findViewById(R.id.downloadBtn)
        reloadBtn = findViewById(R.id.reloadBtn)

        // --- Recommended Secure WebView Settings ---
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // Enable DOM storage for better site compatibility
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(true)
        }
        webView.setBackgroundColor(Color.TRANSPARENT)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                mainFrameHttpError = null
                progressBar.visibility = ProgressBar.VISIBLE
                reloadBtn.visibility = Button.GONE
                scrapeBtn.isEnabled = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = ProgressBar.GONE
                backBtn.isEnabled = webView.canGoBack()

                val httpError = mainFrameHttpError
                if (httpError == null) {
                    scrapeBtn.isEnabled = true
                    reloadBtn.visibility = Button.GONE
                    Toast.makeText(this@MainActivity, getString(R.string.page_loaded), Toast.LENGTH_SHORT).show()
                    return
                }

                view?.evaluateJavascript("document.querySelector('div#text') !== null") { result ->
                    if (result == "true") {
                        mainFrameHttpError = null
                        scrapeBtn.isEnabled = true
                        reloadBtn.visibility = Button.GONE
                        Toast.makeText(this@MainActivity, getString(R.string.page_loaded), Toast.LENGTH_SHORT).show()
                    } else {
                        scrapeBtn.isEnabled = false
                        reloadBtn.visibility = Button.VISIBLE
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.http_error, httpError),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                if (isAllowedUrl(url)) {
                    return false
                }
                Toast.makeText(this@MainActivity, getString(R.string.blocked_navigation), Toast.LENGTH_SHORT).show()
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = ProgressBar.GONE
                    scrapeBtn.isEnabled = false
                    reloadBtn.visibility = Button.VISIBLE
                    Toast.makeText(this@MainActivity, getString(R.string.page_load_error), Toast.LENGTH_LONG).show()
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) {
                    mainFrameHttpError = errorResponse?.statusCode ?: 0
                }
            }
        }

        if (isOnline()) {
            webView.loadUrl(HOME_URL)
        } else {
            progressBar.visibility = ProgressBar.GONE
            scrapeBtn.isEnabled = false
            reloadBtn.visibility = Button.VISIBLE
            Toast.makeText(this, getString(R.string.offline_error), Toast.LENGTH_LONG).show()
        }

        backBtn.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        scrapeBtn.setOnClickListener {
            webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { html ->
                val cleanHtml = html
                    ?.replace("\\u003C", "<")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\n", "\n")
                    ?.trim('"')
                if (cleanHtml != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        scrapeSong(cleanHtml)
                    }
                }
            }
        }

        downloadBtn.setOnClickListener {
            if (lastChordPro.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    saveChordProToDownloads(lastChordPro)
                }
            }
        }

        reloadBtn.setOnClickListener {
            if (isOnline()) {
                progressBar.visibility = ProgressBar.VISIBLE
                reloadBtn.visibility = Button.GONE
                if (webView.url.isNullOrBlank()) {
                    webView.loadUrl(HOME_URL)
                } else {
                    webView.reload()
                }
            } else {
                Toast.makeText(this, getString(R.string.offline_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isAllowedUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        val isAllowedHost = host == ALLOWED_HOST ||
            host.endsWith(".$ALLOWED_HOST") ||
            host == GOOGLE_HOST ||
            host.endsWith(".$GOOGLE_HOST")
        return uri.scheme == "https" && isAllowedHost
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun scrapeSong(html: String) {
        val doc = withContext(Dispatchers.IO) { Jsoup.parse(html) }

        lastTitle = doc.select("h1.ti").text()
        lastArtist = doc.select("h2.ar").text()
        val key = doc.select("summary .til").text()

        val chordPro = StringBuilder()
        chordPro.append("{title: $lastTitle}\n{artist: $lastArtist}\n{key: $key}\n\n")

        val textDiv = doc.selectFirst("div#text")
        val children = textDiv?.children() ?: return

        var pendingChordLine: String? = null

        for (child in children) {
            when {
                child.hasClass("ch") -> {
                    pendingChordLine = buildChordLineFromCh(child)
                }
                child.hasClass("te") -> {
                    if (pendingChordLine != null) {
                        chordPro.append(pendingChordLine).append("\n")
                    }
                    chordPro.append(child.text()).append("\n")
                    pendingChordLine = null
                }
                child.hasClass("no") -> {
                    chordPro.append("\n")
                    pendingChordLine = null
                }
            }
        }

        lastChordPro = chordPro.toString()

        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, getString(R.string.copied_clipboard), Toast.LENGTH_SHORT).show()
            copyToClipboard(lastChordPro)
        }
    }

    private fun buildChordLineFromCh(chDiv: Element): String {
        val sb = StringBuilder()
        for (node in chDiv.childNodes()) {
            when (node) {
                is TextNode -> sb.append(node.wholeText)
                is Element -> {
                    when (node.tagName()) {
                        "span" -> {
                            for (tn in node.textNodes()) {
                                sb.append(tn.wholeText)
                            }
                        }
                        "a" -> {
                            sb.append(node.text())
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    private suspend fun saveChordProToDownloads(content: String) {
        val filename = "${lastArtist.ifBlank { getString(R.string.unknown_artist) }} - ${lastTitle.ifBlank { getString(R.string.unknown_title) }}.txt"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Chords")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "${getString(R.string.saved_downloads)} $filename", Toast.LENGTH_LONG).show()
                openShareSheet(it)
            }
        }
    }

    private fun openShareSheet(uri: Uri) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_file)))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ChordPro", text)
        clipboard.setPrimaryClip(clip)
    }
}
